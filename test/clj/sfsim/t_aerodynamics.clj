(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [clojure.math :refer (PI to-radians)]
      [sfsim.aerodynamics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Mix two values depending on angle"
       (mix 0.1 0.4 (to-radians 0)) => 0.1
       (mix 0.1 0.4 (to-radians 180)) => 0.4
       (mix 0.1 0.4 (to-radians 360)) => 0.1)


(facts "Basic drag function"
       ((basic-drag 0.1 2.0) 0.0) => 0.1
       ((basic-drag 0.1 2.0) (to-radians 90)) => 2.0
       ((basic-drag 0.1 2.0) (to-radians 180)) => 0.1)

(facts "Basic lift function"
       ((basic-lift 1.1) 0.0) => 0.0
       ((basic-lift 1.1) (to-radians 45)) => 1.1
       ((basic-lift 1.1) (to-radians 90)) => (roughly 0.0 1e-6))


(facts "Ellipse-like fall-off function"
       ((fall-off 0.8 0.5) 0.0) => 0.8
       ((fall-off 0.8 0.5) 0.5) => 0.0
       ((fall-off 0.8 0.5) 0.2) => (roughly 0.16 1e-6)
       ((fall-off 0.8 0.6) 1.0) => 0.0)

(facts "Increase of lift for small angles of attack before stall"
       ((glide 0.8 0.6 0.4 0.5) 0.0) => 0.0
       ((glide 0.8 0.6 0.4 0.5) 0.6) => 0.8
       ((glide 0.8 0.6 0.4 0.5) 1.1) => 0.0
       ((glide 0.8 0.6 0.4 0.5) 0.8) => (roughly 0.08 1e-6)
       ((glide 0.8 0.6 0.4 0.5) -0.6) => -0.8
       ((glide 0.8 0.6 0.4 0.5) -1.1) => 0.0
       ((glide 0.8 0.6 0.4 0.5) -0.8) => (roughly -0.08 1e-6))

(facts "Bumps to add to drag before 180 and -180 degrees"
       ((bumps 0.1 0.4) 0.0) => 0.0
       ((bumps 0.1 0.4) (- PI 0.2)) => 0.1
       ((bumps 0.1 0.4) (- 0.2 PI)) => 0.1
       ((bumps 0.1 0.4) (- PI)) => 0.0
       ((bumps 0.1 0.4) (+ PI)) => 0.0
       ((bumps 0.1 0.4) (- PI 0.4)) => 0.0
       ((bumps 0.1 0.4) (- 0.4 PI)) => 0.0)


(facts "Lift increase to add near 180 and -180 degrees"
       ((tail 0.4 0.1 0.2) PI) => 0.0
       ((tail 0.4 0.1 0.2) 0.1) => 0.0
       ((tail 0.4 0.1 0.2) -0.1) => 0.0
       ((tail 0.4 0.1 0.2) (- PI 0.1)) => (roughly -0.4 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.1 PI)) => (roughly 0.4 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.05)) => (roughly -0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.05 PI)) => (roughly 0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.2)) => (roughly -0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.2 PI)) => (roughly 0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.25)) => (roughly -0.1 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.25 PI)) => (roughly 0.1 1e-6))


(facts "Compose an aerodynamic curve"
       ((compose (fn [x] 0.0)) 0.0) => 0.0
       ((compose (fn [x] 1.0)) 0.0) => 1.0
       ((compose (fn [x] x)) 2.0) => 2.0
       ((compose (fn [x] 1.0) (fn [x] 2.0)) 0.0) => 3.0)


(facts "Sanity check for the aerodynamic coefficient functions"
       (coefficient-of-lift (to-radians 10)) => #(>= % 0.1)
       (coefficient-of-lift (to-radians -10)) => #(<= % -0.1)
       (coefficient-of-drag (to-radians 0)) => #(>= % 0.1)
       (coefficient-of-drag (to-radians 180)) => #(>= % 0.1)
       (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 0))))
       (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 180))))
       (coefficient-of-side-force (to-radians 0)) => zero?
       (coefficient-of-side-force (to-radians 45)) => #(>= % 0.1)
       (coefficient-of-side-force (to-radians -45)) => #(<= % -0.1)
       (coefficient-of-side-force (to-radians 135)) => #(<= % -0.1)
       (coefficient-of-side-force (to-radians -135)) => #(>= % 0.1))


(facts "Mirror values at 90 degrees"
       (mirror (to-radians 90)) => (roughly (to-radians 90) 1e-6)
       (mirror (to-radians 0)) => (roughly (to-radians 180) 1e-6)
       (mirror (to-radians 180)) => (roughly (to-radians 0) 1e-6)
       (mirror (to-radians -90)) => (roughly (to-radians -90) 1e-6)
       (mirror (to-radians -180)) => (roughly (to-radians 0) 1e-6))


(facts "Sanity check for the 3D aerodynamic coefficient functions"
       (coefficient-of-lift (to-radians 30) (to-radians 0))
       => (roughly (coefficient-of-lift (to-radians 30) (to-radians 180)) 1e-6)
       (coefficient-of-lift (to-radians 5) (to-radians 0))
       => (roughly (coefficient-of-lift (to-radians -175) (to-radians 180)) 1e-6)
       (coefficient-of-drag (to-radians 0) (to-radians 0))
       => (roughly (coefficient-of-drag (to-radians 0) (to-radians 180)) 1e-6)
       (coefficient-of-drag (to-radians 0) (to-radians 90))
       => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 0)) 0.1))
       (coefficient-of-drag (to-radians 0) (to-radians -90))
       => (roughly (coefficient-of-drag (to-radians 0) (to-radians 90)) 1e-6)
       (coefficient-of-drag (to-radians 90) (to-radians 0))
       => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 90)) 0.1)))
