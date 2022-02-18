(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.planet :refer :all]))

(facts "Create vertex array object for drawing cube map tiles"
       (let [a (matrix [-0.75 -0.5  -1.0])
             b (matrix [-0.5  -0.5  -1.0])
             c (matrix [-0.75 -0.25 -1.0])
             d (matrix [-0.5  -0.25 -1.0])]
         (with-redefs [cubemap/cube-map-corners (fn [^long face ^long level ^long y ^long x]
                                                    (fact [face level y x] => [5 3 2 1])
                                                    [a b c d])]
           (let [arr (make-cube-map-tile-vertices 5 3 2 1 10 100)]
             (subvec arr  0  3) => (vec a)
             (subvec arr  7 10) => (vec b)
             (subvec arr 14 17) => (vec c)
             (subvec arr 21 24) => (vec d)
             (subvec arr  3  5) => [0.05 0.05]
             (subvec arr 10 12) => [0.95 0.05]
             (subvec arr 17 19) => [0.05 0.95]
             (subvec arr 24 26) => [0.95 0.95]
             (subvec arr  5  7) => [0.005 0.005]
             (subvec arr 12 14) => [0.995 0.005]
             (subvec arr 19 21) => [0.005 0.995]
             (subvec arr 26 28) => [0.995 0.995]))))
