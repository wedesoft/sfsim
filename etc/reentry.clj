(ns user
    (:require
      [clojure.math :refer (PI to-radians to-degrees sqrt cos)]
      [clojure.java.shell :refer [sh]]
      [fastmath.matrix :refer (mat3x3 mulv eye col inverse)]
      [fastmath.vector :refer (vec3 mag div)]
      [sfsim.quaternion :as q]
      [sfsim.util :refer (sqr)]
      [sfsim.units :refer :all]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.aerodynamics :refer :all :as aerodynamics]))

;; https://www.researchgate.net/publication/268557220_Continuous_Aerodynamic_Modelling_of_Entry_Shapes

(def reentry-angle (akima-spline
                     0.0  (to-radians 5)
                     1.0  (to-radians 5)
                     5.0  (to-radians 10)
                     12.0 (to-radians 40)
                     25.0 (to-radians 40)
                     30.0 (to-radians 40)))


(def optimal-deceleration (* 1.7 gravitation))


(defn orientation-pitch
  [pitch-angle]
  (q/rotation pitch-angle (vec3 0 1 0)))


(defn orientation-for-speed
  [speed-mach]
  (orientation-pitch (reentry-angle speed-mach)))


(defn speed-of-sound-at-height
  [height]
  (atmosphere/speed-of-sound (atmosphere/temperature-at-height height)))


(def mass 125000.0)

(def inertia (mat3x3 5016255.0      69.359375  89151.0859375,
                     69.3671875     8511745.0 -12.953125,
                     89151.1171875 -12.96875   1.2697381E7))


(defn deceleration-at-reentry
  [height speed]
  (let [speed-of-sound    (speed-of-sound-at-height height)
        speed-mach        (/ speed speed-of-sound)
        orientation       (orientation-for-speed speed-mach)
        loads             (aerodynamic-loads height orientation (vec3 speed 0 0) (vec3 0 0 0) (vec3 0 0 0) 0.0 0.0)]
    (div (:sfsim.aerodynamics/forces loads) mass)))


(defn deceleration-magnitude-at-reentry
  [height speed]
  (mag (deceleration-at-reentry height speed)))


(defn bisection-inverse
  [f y x0 x1 accuracy]
  (let [xm (/ (+ x0 x1) 2.0)
        fm (f xm)]
    (cond
      (< (- x1 x0) accuracy) xm
      (< y fm) (recur f y x0 xm accuracy)
      (> y fm) (recur f y xm x1 accuracy))))


(defn optimal-speed-for-height
  [height]
  (let [lower-bound 0.0
        upper-bound (* 30.0 (speed-of-sound-at-height height))]
    (bisection-inverse (partial deceleration-magnitude-at-reentry height) optimal-deceleration lower-bound upper-bound 1.0)))


(def max-pitch-error (to-radians 3.0))

(def nominal-pitch-acceleration (to-radians 1.0))


(defn pitch-acceleration
  [height]
  (let [speed                (optimal-speed-for-height height)
        speed-of-sound       (speed-of-sound-at-height height)
        orientation          (orientation-for-speed (/ speed speed-of-sound))
        min-flaps            (to-radians -20.0)
        max-flaps            (to-radians 20.0)
        moments-lower        (:sfsim.aerodynamics/moments
                               (aerodynamic-loads height orientation (vec3 speed 0 0) (vec3 0 0 0) (vec3 0 max-flaps 0) 0.0 0.0))
        moments-upper        (:sfsim.aerodynamics/moments
                               (aerodynamic-loads height orientation (vec3 speed 0 0) (vec3 0 0 0) (vec3 0 min-flaps 0) 0.0 0.0))
        angular-acc-lower    (mulv (inverse inertia) moments-lower)
        angular-acc-upper    (mulv (inverse inertia) moments-upper)]
    {:lower (angular-acc-lower 1) :upper (angular-acc-upper 1)}))



(spit "/tmp/curve.gnuplot"
"#!/usr/bin/gnuplot -c
set terminal pngcairo size 1280,720
set output ARG2
set xlabel ARG3
set ylabel ARG4
plot ARG1 using 1:2 with lines title ARG4
")

(def mach (range 0.0 30.0 0.125))
; (spit "/tmp/angle-of-attack.dat" (apply str (map (fn [x y] (str x " " y "\n")) mach (map (comp to-degrees reentry-angle) mach))))
; (sh "gnuplot" "-c" "/tmp/curve.gnuplot" "/tmp/angle-of-attack.dat" "/tmp/angle-of-attack.png" "speed/Ma" "AoA/°" "AoA vs. Mach")
; (sh "display" "/tmp/angle-of-attack.png")

(def height (range 0.0 121000.0 1000.0))
; (spit "/tmp/speed.dat" (apply str (map (fn [x y] (str x " " y "\n")) height (map (fn [height] (/ (optimal-speed-for-height height) (speed-of-sound-at-height height))) height))))
; (sh "gnuplot" "-c" "/tmp/curve.gnuplot" "/tmp/speed.dat" "/tmp/speed.png" "height/m" "speed/Ma" "speed vs. height")
; (sh "display" "/tmp/speed.png")

(spit "/tmp/area.gnuplot"
"#!/usr/bin/gnuplot -c
set terminal pngcairo size 1280,720
set output ARG2
set xlabel ARG3
set ylabel ARG4
plot ARG1 using 1:2:3 with filledcurves title ARG4
")

(def height (range 0.0 121000.0 1000.0))
(spit "/tmp/control.dat" (apply str (map (fn [x {:keys [lower upper]}] (str x " " (to-degrees lower) " " (to-degrees upper) "\n")) height (map pitch-acceleration height))))
(sh "gnuplot" "-c" "/tmp/area.gnuplot" "/tmp/control.dat" "/tmp/control.png" "height/m" "pitch-control/(°/s²)" "height vs. pitch-control range")
(sh "display" "/tmp/control.png")

(System/exit 0)
