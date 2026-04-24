(ns sfsim.t-ppo
    (:require
      [midje.sweet :refer :all]
      [libpython-clj2.python :refer (py.) :as py]
      [sfsim.environment :refer (Environment)]
      [sfsim.mlp :refer (tensor tolist Actor Critic indeterministic-act adam-optimizer)]
      [sfsim.ppo :refer :all]))


(defrecord TestEnvironment [state]
  Environment
  (environment-update [_this action] (->TestEnvironment (+ state (first action))))
  (environment-observation [_this] [(+ state 100)])
  (environment-done? [_this] (>= state 10))
  (environment-truncate? [_this] (< state 0))
  (environment-reward [_this _action] (- (abs (- state 5)))))


(defn test-env-factory [] (constantly (->TestEnvironment 1)))
(defn constant-value [a] (fn [observation] {:action [a] :logprob [0]}))
(defn stop-at-102 [observation] {:action (if (>= (first observation) 102) [0] [1]) :logprob [0]})
(defn feedback-state [observation] {:action [(- (first observation) 100)] :logprob [(- 100 (first observation))]})

(facts "Generate samples from environment"
       (:observations (sample-environment (test-env-factory) (constant-value 0) 1)) => [[101]]
       (:observations (sample-environment (test-env-factory) (constant-value 0) 2)) => [[101] [101]]
       (:observations (sample-environment (test-env-factory) (constant-value 1) 2)) => [[101] [102]]
       (:observations (sample-environment (test-env-factory) stop-at-102 3)) => [[101] [102] [102]]
       (:rewards (sample-environment (test-env-factory) (constant-value 1) 5)) => [-4 -3 -2 -1 0]
       (:dones (sample-environment (test-env-factory) (constant-value 3) 4)) => [false false false true]
       (:observations (sample-environment (test-env-factory) (constant-value 3) 5)) => [[101] [104] [107] [110] [101]]
       (:truncates (sample-environment (test-env-factory) (constant-value -1) 3)) => [false false true]
       (:observations (sample-environment (test-env-factory) (constant-value -1) 4)) => [[101] [100] [99] [101]]
       (:next-observations (sample-environment (test-env-factory) (constant-value 3) 5)) => [[104] [107] [110] [101] [104]]
       (:actions (sample-environment (test-env-factory) feedback-state 3)) => [[1] [2] [4]]
       (:logprobs (sample-environment (test-env-factory) (constant-value 0) 1))  => [[0]]
       (:logprobs (sample-environment (test-env-factory) feedback-state 3)) => [[-1] [-2] [-4]])


(fact "Integration test sampling environment"
      (let [factory (test-env-factory)
            actor   (Actor 1 5 1)
            samples (sample-environment (test-env-factory) (indeterministic-act actor) 8)]
        (count (:observations samples)) => 8))


(facts "Random shuffle of samples"
       (let [samples  {:observations (tensor [[101.0] [102.0] [103.0] [104.0]])
                       :actions (tensor [[1.0] [2.0] [3.0] [4.0]])
                       :logprobs (tensor [[-1.0] [-2.0] [-3.0] [-4.0]])}
             shuffled (shuffle-samples samples (tensor [0 3 2 1] torch/long))]
         (tolist (:observations shuffled)) => [[101.0] [104.0] [103.0] [102.0]]
         (tolist (:actions shuffled)) => [[1.0] [4.0] [3.0] [2.0]]
         (tolist (:logprobs shuffled)) => [[-1.0] [-4.0] [-3.0] [-2.0]]
         (shuffle-samples samples) => some?))


(facts "Create batches from samples"
       (let [samples {:observations (tensor [[101.0] [102.0] [103.0] [104.0]])
                      :actions (tensor [[1.0] [2.0] [3.0] [4.0]])
                      :logprobs (tensor [[-1.0] [-2.0] [-3.0] [-4.0]])}
             batches (create-batches 2 samples)]
         (tolist (:observations (first batches))) => [[101.0] [102.0]]
         (tolist (:observations (second batches))) => [[103.0] [104.0]]
         (tolist (:actions (first batches))) => [[1.0] [2.0]]
         (tolist (:actions (second batches))) => [[3.0] [4.0]]
         (tolist (:logprobs (first batches))) => [[-1.0] [-2.0]]
         (tolist (:logprobs (second batches))) => [[-3.0] [-4.0]]))


(defn linear-critic [observation] (first observation))

(facts "Compute difference between actual reward plus discounted estimate of next state and estimated value of current state"
       (deltas {:observations [[4]] :next-observations [[3]] :rewards [0] :dones [false]} (constantly 0) 1.0) => [0.0]
       (deltas {:observations [[4]] :next-observations [[3]] :rewards [1] :dones [false]} (constantly 0) 1.0) => [1.0]
       (deltas {:observations [[4]] :next-observations [[3]] :rewards [1] :dones [false]} linear-critic 1.0) => [0.0]
       (deltas {:observations [[2]] :next-observations [[1]] :rewards [1] :dones [false]} linear-critic 0.5) => [-0.5]
       (deltas {:observations [[4] [3]] :next-observations [[3] [2]] :rewards [2 3] :dones [false false]} linear-critic 1.0)
       => [1.0 2.0]
       (deltas {:observations [[4]] :next-observations [[3]] :rewards [4] :dones [true]} linear-critic 1.0) => [0.0]
       (deltas {:observations [[4]] :next-observations [[3]] :rewards [4] :dones [true]} linear-critic 1.0) => [0.0])


(facts "Compute advantages attributed to each action"
       (advantages {:dones [false] :truncates [false]} [0.0] 1.0 1.0) => [0.0]
       (advantages {:dones [false] :truncates [false]} [1.0] 1.0 1.0) => [1.0]
       (advantages {:dones [false false] :truncates [false false]} [2.0 3.0] 1.0 1.0) => [5.0 3.0]
       (advantages {:dones [false false] :truncates [false false]} [2.0 3.0] 0.5 1.0) => [3.5 3.0]
       (advantages {:dones [false false] :truncates [false false]} [2.0 3.0] 1.0 0.5) => [3.5 3.0]
       (advantages {:dones [true false] :truncates [false false]} [2.0 3.0] 1.0 1.0) => [2.0 3.0]
       (advantages {:dones [false false] :truncates [true false]} [2.0 3.0] 1.0 1.0) => [2.0 3.0]
       (advantages {:dones [false] :truncates [false]} [0.0] 1.0 1.0) => vector?)


(facts "Associate advantages with batch of samples"
       (let [batch      {:observations [[4]] :next-observations [[2]] :rewards [2] :dones [false] :truncates [false]}
             result     (assoc-advantages (constantly 0) 1.0 1.0 batch)]
         (:observations result) => [[4]]
         (:advantages result) => [2.0]))


(facts "Target values for critic"
       (tolist (critic-target {:observations (tensor [[4]]) :advantages (tensor [0])} (constantly (tensor [0])))) => [0.0]
       (tolist (critic-target {:observations (tensor [[4]]) :advantages (tensor [2])} (constantly (tensor [0])))) => [2.0]
       (tolist (critic-target {:observations (tensor [[4]]) :advantages (tensor [0])} #(torch/squeeze % -1))) => [4.0]
       (tolist (critic-target {:observations (tensor [[4]]) :advantages (tensor [3])} #(torch/squeeze % -1))) => [7.0]
       (tolist (critic-target {:observations (tensor [[4] [3]]) :advantages (tensor [2 1])} #(torch/squeeze % -1))) => [6.0 4.0])


(defn action-prob [p] (fn [observations actions] (tensor [[p]])))
(defn use-obs [observations actions] observations)
(defn use-action [observations actions] actions)

(facts "Probability ratios for a actions using updated policy and old policy"
       (tolist (probability-ratios {:observations (tensor [[4]]) :logprobs (tensor [[0]]) :actions (tensor [[0]])} (action-prob 0)))
       => [1.0]
       (tolist (probability-ratios {:observations (tensor [[4]]) :logprobs (tensor [[1]]) :actions (tensor [[0]])} (action-prob 0)))
       => [0.3678794503211975]
       (tolist (probability-ratios {:observations (tensor [[4]]) :logprobs (tensor [[0]]) :actions (tensor [[0]])} (action-prob 1)))
       => [2.7182817459106445]
       (tolist (probability-ratios {:observations (tensor [[1]]) :logprobs (tensor [[0]]) :actions (tensor [[0]])} use-obs))
       => [2.7182817459106445]
       (tolist (probability-ratios {:observations (tensor [[2 3]]) :logprobs (tensor [[2 3]]) :actions (tensor [[0 0]])} use-obs))
       => [1.0]
       (tolist (probability-ratios {:observations (tensor [[0 1]]) :logprobs (tensor [[0 0]]) :actions (tensor [[0 0]])} use-obs))
       => [2.7182817459106445]
       (tolist (probability-ratios {:observations (tensor [[0 0]]) :logprobs (tensor [[0 1]]) :actions (tensor [[0 0]])} use-obs))
       => [0.3678794503211975]
       (tolist (probability-ratios {:observations (tensor [[0 0]]) :logprobs (tensor [[0 0]]) :actions (tensor [[0 1]])} use-action))
       => [2.7182817459106445])


(facts "Clipped surrogate loss (negative objective)"
       ;; zero advantage
       (tolist (clipped-surrogate-loss (tensor [[1.0]]) (tensor [[0.0]]) 0.25)) => 0.0
       ;; positive advantage
       (tolist (clipped-surrogate-loss (tensor [[1.0]]) (tensor [[3.0]]) 0.25)) => -3.0
       (tolist (clipped-surrogate-loss (tensor [[1.25]]) (tensor [[3.0]]) 0.25)) => -3.75
       (tolist (clipped-surrogate-loss (tensor [[2.0]]) (tensor [[3.0]]) 0.25)) => -3.75
       (tolist (clipped-surrogate-loss (tensor [[0.0]]) (tensor [[3.0]]) 0.25)) => 0.0
       ;; negative advantage
       (tolist (clipped-surrogate-loss (tensor [[1.0]]) (tensor [[-3.0]]) 0.25)) => 3.0
       (tolist (clipped-surrogate-loss (tensor [[0.75]]) (tensor [[-3.0]]) 0.25)) => 2.25
       (tolist (clipped-surrogate-loss (tensor [[0.0]]) (tensor [[-3.0]]) 0.25)) => 2.25
       (tolist (clipped-surrogate-loss (tensor [[2.0]]) (tensor [[-3.0]]) 0.25)) => 6.0)


(facts "Convert batch to Torch tensors"
       (let [result (tensor-batch {:observations [[4.0]]
                                   :logprobs [[-1.0]]
                                   :actions [[0.0 1.0]]
                                   :advantages [0.5]})]
         (tolist (:observations result)) => [[4.0]]
         (tolist (:logprobs result)) => [[-1.0]]
         (tolist (:actions result)) => [[0.0 1.0]]
         (tolist (:advantages result)) => [0.5]))


(fact "Integration test critic training step"
      (let [factory        (test-env-factory)
            actor          (Actor 1 5 1)
            critic         (Critic 1 5)
            samples        (sample-with-advantage-and-critic-target factory actor critic 32 8 0.9 1.0)
            batch          (first samples)
            optimizer      (adam-optimizer critic 0.01 0.0)
            _              (py. optimizer zero_grad)
            loss           (critic-loss batch critic)
            _              (py. loss backward)
            _              (py. optimizer step)
            updated-loss   (critic-loss batch critic)]
        (tolist updated-loss) => #(< % (tolist loss))))


(fact "Integration test actor training step"
      (let [factory        (test-env-factory)
            actor          (Actor 1 5 1)
            critic         (Critic 1 5)
            samples        (sample-with-advantage-and-critic-target factory actor critic 32 8 0.9 1.0)
            batch          (first samples)
            optimizer      (adam-optimizer actor 0.01 0.0)
            _              (py. optimizer zero_grad)
            loss           (actor-loss batch actor 0.2 0.0)
            _              (py. loss backward)
            _              (py. optimizer step)
            updated-loss   (actor-loss batch actor 0.2 0.0)]
        (tolist updated-loss) => #(< % (tolist loss))))
