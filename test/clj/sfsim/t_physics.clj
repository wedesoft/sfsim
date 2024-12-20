(ns sfsim.t-physics
  (:require
    [clojure.math :refer (exp)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.physics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(def add (fn [x y] (+ x y)))
(def scale (fn [s x] (* s x)))


(facts "Runge-Kutta integration method"
       (runge-kutta 42.0 1.0 (fn [y dt] 0.0) add scale) => 42.0
       (runge-kutta 42.0 1.0 (fn [y dt] 5.0) add scale) => 47.0
       (runge-kutta 42.0 2.0 (fn [y dt] 5.0) add scale) => 52.0
       (runge-kutta 42.0 1.0 (fn [y dt] (* 2.0 dt)) add scale) => 43.0
       (runge-kutta 42.0 2.0 (fn [y dt] (* 2.0 dt)) add scale) => 46.0
       (runge-kutta 42.0 1.0 (fn [y dt] (* 3.0 dt dt)) add scale) => 43.0
       (runge-kutta 1.0 1.0 (fn [y dt] y) add scale) => (roughly (exp 1) 1e-2))
