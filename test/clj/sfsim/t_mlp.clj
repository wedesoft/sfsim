(ns sfsim.t-mlp
    (:require
      [midje.sweet :refer :all]
      ; [libpython-clj2.require :refer (require-python)]
      ; [libpython-clj2.python :refer (py.) :as py]
      [sfsim.mlp :refer :all]))

(comment  ; Disabled under Windows

(require-python '[torch :as torch])


(fact "Test critic network"
      (without-gradient
        (let [zero-critic (Critic 2 5)]
          (doseq [param (py. zero-critic parameters)]
                 (py. param zero_))
          (py. zero-critic eval)
          (tolist (zero-critic (tensor [[0 0] [0 0] [0 0]]))) => [0.0 0.0 0.0])))


(fact "Mean square error cost function"
      (let [criterion (mse-loss)]
        (without-gradient
          (toitem (criterion (tensor [[0.0] [0.0] [0.0]]) (tensor [[0.0] [0.0] [0.0]]))) => 0.0
          (toitem (criterion (tensor [[0.0] [0.0] [0.0]]) (tensor [[1.0] [1.0] [1.0]]))) => 1.0
          (toitem (criterion (tensor [[-1.0] [-1.0] [-1.0]]) (tensor [[1.0] [1.0] [1.0]])))  => 4.0)))


(fact "Train network"
      (let [model     (Critic 1 2)
            optimizer (adam-optimizer model 0.1 0.001)
            batches   [[(tensor [[0.0] [0.0]]) (tensor [0.0 0.0])] [(tensor [[1.0] [1.0]]) (tensor [1.0 1.0])]]
            criterion (mse-loss)]
        (py. model train)
        (doseq [epoch (range 100)]
               (train-epoch optimizer model criterion batches))
        (py. model eval)
        (without-gradient
          (toitem (criterion (model (tensor [[0.0] [1.0]])) (tensor [0.0 1.0]))) => (roughly 0.0 1e-3))))


(fact "Use critic with Clojure datatypes"
      (let [model (Critic 1 2)]
        ((critic-observation model) [0.0]) => float?))


(facts "Test actor network"
       (without-gradient
         (let [zero-actor (Actor 2 5 1)]
           (doseq [param (py. zero-actor parameters)]
                  (py. param zero_))
           (py. zero-actor eval)
           (let [result (zero-actor (tensor [[0 0] [0 0] [0 0]]))]
             (tolist (first result)) => [[1.6931471824645996] [1.6931471824645996] [1.6931471824645996]]
             (tolist (second result)) => [[1.6931471824645996] [1.6931471824645996] [1.6931471824645996]] )
           (tolist (py. zero-actor deterministic_act (tensor [[0 0]]))) => [[0.5]]
           (let [result ((indeterministic-act zero-actor) [[0 0]])]
             (:action result) => some?
             (:logprob result) => some?
             (tolist ((logprob-of-action zero-actor) (tensor [[0 0]]) (tensor (:action result)))) => (:logprob result))
           (tolist (entropy-of-distribution zero-actor (tensor [[0 0]]))) => [[-0.07745969295501709]]
           (:action ((indeterministic-act zero-actor) [[0 0]])) => some?
           (:logprob ((indeterministic-act zero-actor) [[0 0]])) => some?)))

)
