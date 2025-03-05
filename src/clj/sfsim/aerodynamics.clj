(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (cos sin sqrt)]
      [sfsim.util :refer (sqr)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn basic-drag
  "Basic cosine shaped drag function"
  {:malli/schema [:=> [:cat :double :double] [:=> [:cat :double] :double]]}
  [min-drag max-drag]
  (fn [angle-of-attack] (+ min-drag (* 0.5 (- max-drag min-drag) (- 1 (cos (* 2 angle-of-attack)))))))


(defn basic-lift
  "Basic sinus shaped lift function"
  {:malli/schema [:=> [:cat :double] [:=> [:cat :double] :double]]}
  [max-lift]
  (fn [angle-of-attack] (* max-lift (sin (* 2 angle-of-attack)))))


(defn fall-off
  "Ellipse-like fall-off function"
  [reduced-increase interval]
  (fn [angle-of-attack]
      (let [relative-position (/ (- interval angle-of-attack) interval)]
        (if (>= relative-position 0.0)
          (* reduced-increase (- 1.0 (sqrt (- 1.0 (sqr relative-position)))))
          0.0))))


(defn glide
  "Increase of lift for small angles of attack before stall"
  [max-increase stall-angle reduced-increase fall-off-interval]
  (fn glide-fn [angle-of-attack]
      (if (neg? angle-of-attack)
        (- (glide-fn (- angle-of-attack)))
        (if (<= angle-of-attack stall-angle)
          (* (/ max-increase stall-angle) angle-of-attack)
          ((fall-off reduced-increase fall-off-interval) (- angle-of-attack stall-angle))))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
