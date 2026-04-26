(ns sfsim.mlp
    "Multilayer perceptrons for actor and critic of reinforcement learning"
    (:require [libpython-clj2.require :refer (require-python)]
              [libpython-clj2.python :refer (py.) :as py]))

(require-python '[builtins :as python]
                '[torch :as torch]
                '[torch.nn :as nn]
                '[torch.nn.functional :as F]
                '[torch.optim :as optim]
                '[torch.distributions :refer (Beta)])


(defmacro without-gradient
  "Execute body without gradient calculation"
  [& body]
  `(let [no-grad# (torch/no_grad)]
     (try
       (py. no-grad# ~'__enter__)
       ~@body
       (finally
         (py. no-grad# ~'__exit__ nil nil nil)))))


(defn tensor
  "Convert nested vector to tensor"
  ([data]
   (tensor data torch/float32))
  ([data dtype]
   (torch/tensor data :dtype dtype)))


(defn tolist
  "Convert tensor to nested vector"
  [tensor]
  (py/->jvm (py. tensor tolist)))


(defn toitem
  "Convert torch scalar value to float"
  [tensor]
  (py. tensor item))


(def Critic
  (py/create-class
    "Critic" [nn/Module]
    {"__init__"
     (py/make-instance-fn
       (fn [self observation-size hidden-units]
           (py. nn/Module __init__ self)
           (py/set-attrs!
             self
             {"fc1" (nn/Linear observation-size hidden-units)
              "fc2" (nn/Linear hidden-units hidden-units)
              "fc3" (nn/Linear hidden-units 1)})
           nil))
     "forward"
     (py/make-instance-fn
       (fn [self x]
           (let [x (py. self fc1 x)
                 x (torch/tanh x)
                 x (py. self fc2 x)
                 x (torch/tanh x)
                 x (py. self fc3 x)]
             (torch/squeeze x -1))))}))


(def Actor
  (py/create-class
    "Actor" [nn/Module]
    {"__init__"
     (py/make-instance-fn
       (fn [self observation-size hidden-units action-size]
           (py. nn/Module __init__ self)
           (py/set-attrs!
             self
             {"fc1"     (nn/Linear observation-size hidden-units)
              "fc2"     (nn/Linear hidden-units hidden-units)
              "fcalpha" (nn/Linear hidden-units action-size)
              "fcbeta"  (nn/Linear hidden-units action-size)})
           nil))
     "forward"
     (py/make-instance-fn
       (fn [self x]
           (let [x (py. self fc1 x)
                 x (torch/tanh x)
                 x (py. self fc2 x)
                 x (torch/tanh x)
                 alpha (torch/add 1.0 (F/softplus (py. self fcalpha x)))
                 beta  (torch/add 1.0 (F/softplus (py. self fcbeta x)))]
             [alpha beta])))
     "deterministic_act"
     (py/make-instance-fn
       (fn [self x]
           (let [[alpha beta] (py. self forward x)]
             (torch/div alpha (torch/add alpha beta)))))
     "get_dist"
     (py/make-instance-fn
       (fn [self x]
           (let [[alpha beta] (py. self forward x)]
             (Beta alpha beta))))}))


(defn mse-loss
  "Mean square error cost function"
  []
  (nn/MSELoss))


(defn adam-optimizer
  "Adam optimizer"
  [model learning-rate weight-decay]
  (optim/Adam (py. model parameters) :lr learning-rate :weight_decay weight-decay))


(defn train-epoch
  "Train network for specified number of epochs"
  [optimizer model criterion batches]
  (doseq [[data label] batches]
         (let [prediction (model data)
               loss       (criterion prediction label)]
           (py. optimizer zero_grad)
           (py. loss backward)
           (py. optimizer step))))


(defn critic-observation
  "Use critic with Clojure datatypes"
  [critic]
  (fn [observation]
      (without-gradient (toitem (critic (tensor observation))))))


(defn indeterministic-act
  "Sample action using actor network returning random action and log-probability"
  [actor]
  (fn indeterministic-act-with-actor [observation]
      (without-gradient
        (let [dist    (py. actor get_dist (tensor observation))
              sample  (py. dist sample)
              action  (torch/clamp sample 0.0 1.0)
              logprob (py. dist log_prob action)]
          {:action (tolist action) :logprob (tolist logprob)}))))


(defn logprob-of-action
  "Get log probability of action"
  [actor]
  (fn [observation action]
      (let [dist (py. actor get_dist observation)]
        (py. dist log_prob action))))


(defn entropy-of-distribution
  "Get entropy of distribution"
  [actor observation]
  (let [dist (py. actor get_dist observation)]
    (py. dist entropy)))
