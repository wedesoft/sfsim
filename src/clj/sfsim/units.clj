(ns sfsim.units)


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(def rankin (/ 5.0 9.0))
(def foot 0.3048)
(def gravitation 9.8066500286389)
(def pound-weight 0.45359237)
(def pound-force (* pound-weight gravitation))
(def slugs 14.5939029372)


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
