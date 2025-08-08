;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(require '[clojure.math :refer (sqrt PI log pow)]
         '[fastmath.vector :refer (vec3 mag add mult sub)]
         '[sfsim.jolt :as jolt]
         '[sfsim.util :refer (cube)]
         '[sfsim.quaternion :as q]
         '[sfsim.config :as config]
         '[sfsim.physics :as physics])
(use '[clojure.java.shell :only [sh]])

(jolt/jolt-init)

(def height 408000.0)
(def earth-mass (config/planet-config :sfsim.planet/mass))
(def radius (config/planet-config :sfsim.planet/radius))
(def g physics/gravitational-constant)
(def orbit-radius (+ ^double radius ^double height))
(def speed (sqrt (/ (* ^double earth-mass ^double g) ^double orbit-radius)))
(def period (* 2.0 PI (sqrt (/ (cube orbit-radius) (* ^double g ^double earth-mass)))))
; (def dt (/ 1.0 60.0))
(def dt 10.0)
(def n 1)

(def sphere (jolt/create-and-add-dynamic-body (jolt/sphere-settings 0.5 1000.0) (vec3 orbit-radius 0 0) (q/->Quaternion 1 0 0 0)))
(def mass (jolt/get-mass sphere))
(jolt/set-linear-velocity sphere (vec3 0 speed 0))

(def euler false)

(def max-error (atom 0.0))

(java.util.Locale/setDefault java.util.Locale/US)

(def x (map #(pow 2 %) (range -5 6 0.1)))
(def y (atom []))

(doseq [dt x]
       (with-open [f (clojure.java.io/writer (if euler (format "/tmp/euler%f.dat" dt) (format "/tmp/rk%5.3f.dat" dt)))]
         (jolt/set-translation sphere (vec3 orbit-radius 0 0))
         (jolt/set-linear-velocity sphere (vec3 0 speed 0))
         (reset! max-error 0.0)
         (dotimes [t (long (* n (/ period dt)))]
           (let [state  {:position (jolt/get-translation sphere) :speed (jolt/get-linear-velocity sphere)}
                 state2 (physics/runge-kutta state dt (physics/state-change (physics/gravitation (vec3 0 0 0) earth-mass))
                                             (fn [x y] (merge-with add x y))
                                             (fn [s x] (into {} (for [[k v] x] [k (mult v s)]))))
                 [dv1 dv2] (physics/matching-scheme state dt state2 #(mult %2 %1) sub)]
             (if euler
               (jolt/set-gravity ((physics/gravitation (vec3 0 0 0) earth-mass) (jolt/get-translation sphere)))
               (jolt/set-gravity (vec3 0 0 0)))
             (when (not euler)
               (jolt/add-impulse sphere (mult dv1 mass)))
             (jolt/update-system dt 1)
             (when (not euler)
               (jolt/add-impulse sphere (mult dv2 mass)))
             (let [error (- (mag (jolt/get-translation sphere)) orbit-radius)]
               (swap! max-error max (abs error))
               (.write f (format "%f %f\n" (* t dt) error)))))
         (swap! y conj @max-error)))


(with-open [f (clojure.java.io/writer "/tmp/errors.dat")]
 (doseq [[i j] (map list x @y)]
   (.write f (format "%f %f\n" (log i) j))))


(sh "gnuplot" "-c" "etc/orbit.gp")
(sh "gnuplot" "-c" "etc/errors.gp")

(jolt/jolt-destroy)
(System/exit 0)
