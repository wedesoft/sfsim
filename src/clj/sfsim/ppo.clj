(ns sfsim.ppo
    (:require
      ; [libpython-clj2.require :refer (require-python)]
      ; [libpython-clj2.python :refer (py.) :as py]
      ; [sfsim.mlp :refer (tensor logprob-of-action without-gradient mse-loss indeterministic-act entropy-of-distribution
      ;                    critic-observation)]
      [sfsim.environment :refer (environment-observation environment-update environment-reward environment-done?
                                 environment-truncate?)]))


(comment  ; Disabled under Windows

(require-python '[torch :as torch]
                '[builtins :as python])


(defn sample-environment
  "Collect trajectory data from environment"
  [environment-factory policy size]
  (loop [state             (environment-factory)
         observations      []
         actions           []
         logprobs          []
         next-observations []
         rewards           []
         dones             []
         truncates         []
         i                 size]
    (if (pos? i)
      (let [observation      (environment-observation state)
            sample           (policy observation)
            action           (:action sample)
            logprob          (:logprob sample)
            reward           (environment-reward state action)
            done             (environment-done? state)
            truncate         (environment-truncate? state)
            next-state       (if (or done truncate) (environment-factory) (environment-update state action))
            next-observation (environment-observation next-state)]
        (recur next-state
               (conj observations observation)
               (conj actions action)
               (conj logprobs logprob)
               (conj next-observations next-observation)
               (conj rewards reward)
               (conj dones done)
               (conj truncates truncate)
               (dec i)))
      {:observations      observations
       :actions           actions
       :logprobs          logprobs
       :next-observations next-observations
       :rewards           rewards
       :dones             dones
       :truncates         truncates})))


(defn random-order
  "Create a list of randomly ordered indices"
  [n]
  (shuffle (range n)))


(defn shuffle-samples
  "Random shuffle of samples"
  ([samples]
   (shuffle-samples samples (random-order (python/len (first (vals samples))))))
  ([samples indices]
   (zipmap (keys samples) (map #(torch/index_select % 0 (torch/tensor indices)) (vals samples)))))


(defn create-batches
  "Create mini batches from environment samples"
  [batch-size samples]
  (apply mapv (fn [& args] (zipmap (keys samples) args)) (map #(py. % split batch-size) (vals samples))))


(defn tensor-batch
  "Convert batch to Torch tensors"
  [batch]
  {:observations (tensor (:observations batch))
   :logprobs (tensor (:logprobs batch))
   :actions (tensor (:actions batch))
   :advantages (tensor (:advantages batch))})


(defn deltas
  "Compute difference between actual reward plus discounted estimate of next state and estimated value of current state"
  [{:keys [observations next-observations rewards dones]} critic gamma]
  (mapv (fn [observation next-observation reward done]
            (- (+ reward (if done 0.0 (* gamma (critic next-observation)))) (critic observation)))
        observations next-observations rewards dones))


(defn advantages
  "Compute advantages attributed to each action"
  [{:keys [dones truncates]} deltas gamma lambda]
  (vec
    (reverse
    (rest
      (reductions
        (fn [advantage [delta done truncate]]
            (+ delta (if (or done truncate) 0.0 (* gamma lambda advantage))))
        0.0
        (reverse (map vector deltas dones truncates)))))))


(defn assoc-advantages
  "Associate advantages with batch of samples"
  [critic gamma lambda batch]
  (let [deltas     (deltas batch critic gamma)
        advantages (advantages batch deltas gamma lambda)]
    (assoc batch :advantages advantages)))


(defn critic-target
  "Determine target values for critic"
  [{:keys [observations advantages]} critic]
  (without-gradient (torch/add (critic observations) advantages)))


(defn assoc-critic-target
  "Associate critic target values with batch of samples"
  [critic batch]
  (let [target (critic-target batch critic)]
    (assoc batch :critic-target target)))


(defn normalize-advantages
  "Normalize advantages"
  [batch]
  (let [advantages (:advantages batch)]
    (assoc batch :advantages (torch/div (torch/sub advantages (torch/mean advantages)) (torch/std advantages)))))


(defn sample-with-advantage-and-critic-target
  "Create batches of samples and add add advantages and critic target values"
  [environment-factory actor critic size batch-size gamma lambda]
  (->> (sample-environment environment-factory (indeterministic-act actor) size)
       (assoc-advantages (critic-observation critic) gamma lambda)
       tensor-batch
       (assoc-critic-target critic)
       normalize-advantages
       shuffle-samples
       (create-batches batch-size)))


(defn probability-ratios
  "Probability ratios for a actions using updated policy and old policy"
  [{:keys [observations logprobs actions]} logprob-of-action]
  (let [updated-logprobs (logprob-of-action observations actions)]
    (torch/exp (py. (torch/sub updated-logprobs logprobs) sum 1))))


(defn clipped-surrogate-loss
  "Clipped surrogate loss (negative objective)"
  [probability-ratios advantages epsilon]
  (torch/mean
    (torch/neg
      (torch/min
        (torch/mul probability-ratios advantages)
        (torch/mul (torch/clamp probability-ratios (- 1.0 epsilon) (+ 1.0 epsilon)) advantages)))))


(defn actor-loss
  "Compute loss value for batch of samples and actor"
  [samples actor epsilon entropy-factor]
  (let [ratios         (probability-ratios samples (logprob-of-action actor))
        entropy        (torch/mul entropy-factor (torch/neg (torch/mean (entropy-of-distribution actor (:observations samples)))))
        surrogate-loss (clipped-surrogate-loss ratios (:advantages samples) epsilon)]
    (torch/add surrogate-loss entropy)))


(defn critic-loss
  "Compute loss value for batch of samples and critic"
  [samples critic]
  (let [criterion (mse-loss)
        loss      (criterion (critic (:observations samples)) (:critic-target samples))]
    loss))

)
