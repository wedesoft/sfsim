(ns sfsim25.t-rgb
  (:refer-clojure :exclude [+ *])
  (:require [midje.sweet :refer :all]
            [sfsim25.rgb :refer :all]))

(facts "Return components of RGB value"
  (:r (->RGB 2 3 5)) => 2.0
  (:g (->RGB 2 3 5)) => 3.0
  (:b (->RGB 2 3 5)) => 5.0)

(facts "Add RGB values"
  (+ (->RGB 2 3 5))                              => (->RGB  2  3  5)
  (+ (->RGB 2 3 5) (->RGB 3 5 7))                => (->RGB  5  8 12)
  (+ (->RGB 2 3 5) (->RGB 3 5 7) (->RGB 5 7 11)) => (->RGB 10 15 23))

(fact "Scale RGB value"
  (* 2 (->RGB 2 3 5)) => (->RGB 4 6 10))
