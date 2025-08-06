;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

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


(facts "Euler integration method"
       (euler 42.0 1.0 (fn [_y _dt] 0.0) add scale) => 42.0
       (euler 42.0 1.0 (fn [_y _dt] 5.0) add scale) => 47.0
       (euler 42.0 2.0 (fn [_y _dt] 5.0) add scale) => 52.0
       (euler 42.0 2.0 (fn [_y dt] dt) add scale) => 46.0)


(facts "Runge-Kutta integration method"
       (runge-kutta 42.0 1.0 (fn [_y _dt] 0.0) add scale) => 42.0
       (runge-kutta 42.0 1.0 (fn [_y _dt] 5.0) add scale) => 47.0
       (runge-kutta 42.0 2.0 (fn [_y _dt] 5.0) add scale) => 52.0
       (runge-kutta 42.0 1.0 (fn [_y dt] (* 2.0 dt)) add scale) => 43.0
       (runge-kutta 42.0 2.0 (fn [_y dt] (* 2.0 dt)) add scale) => 46.0
       (runge-kutta 42.0 1.0 (fn [_y dt] (* 3.0 dt dt)) add scale) => 43.0
       (runge-kutta 1.0 1.0 (fn [y _dt] y) add scale) => (roughly (exp 1) 1e-2))


(defn semi-implicit
  [a]
  (fn [y dt] (let [speed (+ (:speed y) (* a dt))] {:position speed :speed a})))


(def add-values (fn [x y] (merge-with + x y)))
(def scale-values (fn [s x] (into {} (for [[k v] x] [k (* s v)]))))


(defn semi-implicit-euler-twice
  [y0 dt [a1 a2]]
  (-> y0
      (euler dt (semi-implicit a1) add-values scale-values)
      (euler dt (semi-implicit a2) add-values scale-values)))


(fact "Sanity check for euler test function"
      (semi-implicit-euler-twice {:position 42.0 :speed 0.0} 1.0 [2 3]) => {:position 49.0 :speed 5.0}
      (semi-implicit-euler-twice {:position 42.0 :speed 0.0} 2.0 [2 3]) => {:position 70.0 :speed 10.0})


(tabular "Test Runge Kutta matching scheme for semi-implicit Euler"
         (fact (semi-implicit-euler-twice ?y0 ?dt (matching-scheme ?y0 ?dt ?y2 * -)) => ?y2)
         ?y0                        ?dt ?y2
         {:position 0.0 :speed 0.0} 1.0 {:position 0.0 :speed 0.0}
         {:position 0.0 :speed 0.0} 1.0 {:position 3.0 :speed 2.0}
         {:position 0.0 :speed 0.0} 2.0 {:position 12.0 :speed 4.0}
         {:position 0.0 :speed 0.0} 1.0 {:position 2.0 :speed 2.0}
         {:position 0.0 :speed 0.0} 1.0 {:position 4.0 :speed 2.0}
         {:position 0.0 :speed 0.0} 0.0 {:position 0.0 :speed 0.0})
