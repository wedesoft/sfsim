;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(require '[clojure.math :refer (sqrt PI)]
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
(def dt (/ 1.0 60.0))
(def n 3)

(def sphere (jolt/create-and-add-dynamic-body (jolt/sphere-settings 0.5 1000.0) (vec3 orbit-radius 0 0) (q/->Quaternion 1 0 0 0)))
(def mass (jolt/get-mass sphere))
(jolt/set-linear-velocity sphere (vec3 0 speed 0))

(doseq [euler [true false]]
       (with-open [f (clojure.java.io/writer (if euler "/tmp/euler.dat" "/tmp/rk.dat"))]
         (dotimes [t (long (* n (/ period dt)))]
           (let [state  {:position (jolt/get-translation sphere) :speed (jolt/get-linear-velocity sphere)}
                 state2 (physics/runge-kutta state dt (physics/state-change (physics/gravitation earth-mass))
                                             (fn [x y] (merge-with add x y))
                                             (fn [s x] (into {} (for [[k v] x] [k (mult v s)]))))
                 [dv1 dv2] (physics/matching-scheme state dt state2 #(mult %2 %1) sub)]
             (if euler
               (jolt/set-gravity ((physics/gravitation earth-mass) (jolt/get-translation sphere)))
               (jolt/set-gravity (vec3 0 0 0)))
             (when (not euler)
               (jolt/add-impulse sphere (mult dv1 mass)))
             (jolt/update-system dt 1)
             (when (not euler)
               (jolt/add-impulse sphere (mult dv2 mass)))
             (.write f (format "%f %f\n" (* t dt) (- (mag (jolt/get-translation sphere)) radius)))))))

(sh "gnuplot" "-c" "etc/euler.gp")

(jolt/jolt-destroy)
(System/exit 0)
