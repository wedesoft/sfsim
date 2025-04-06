; height dependent formula for atmospheric temperature
; Hull: Fundamentals of Airplane Flight Mechanics
(require '[clojure.math :refer (exp cos sin pow to-radians to-degrees)])
(require '[sfsim.quaternion :as q])
(require '[sfsim.util :refer :all])
(require '[sfsim.units :refer :all])
(require '[sfsim.aerodynamics :refer :all])
(require '[sfsim.atmosphere :refer :all])
(require '[fastmath.vector :refer (vec3 mag)])

(def radius 6378000.0)

(def position (vec3 0 0 6379000))

(def surface 198.0)
(def chord 10.0)
(def wingspan 20.75)

(def weight 100000)

(def height (- (mag position) radius))

(def approach (to-radians 18))

(def orientation (q/rotation (to-radians 8) (vec3 0 -1 0)))

(def speed-mag 100.0)
(def speed (vec3 (* (cos approach) speed-mag) 0 (* (sin approach) speed-mag)))
(def speed-body-system (q/rotate-vector (q/inverse orientation) speed))

(def alpha (angle-of-attack speed-body-system))
(def beta (angle-of-side-slip speed-body-system))
(to-degrees alpha)

(def wind-to-body (q/* (q/rotation alpha (vec3 0 0 1)) (q/rotation (- beta) (vec3 0 1 0))))

(def cl (coefficient-of-lift alpha beta))
(def cd (coefficient-of-drag alpha beta))
(def cy (coefficient-of-side-force alpha beta))

(def cm (coefficient-of-pitch-moment alpha beta))
(def cn (coefficient-of-yaw-moment beta))
(def cr (coefficient-of-roll-moment beta))

(def lift (* 0.5 cl rho (sqr speed-mag) surface))
(def drag (* 0.5 cd rho (sqr speed-mag) surface))
(def side-force (* 0.5 cy rho (sqr speed-mag) surface))

(def pitch-moment (* 0.5 cm rho (sqr speed-mag) surface chord))
(def yaw-moment (* 0.5 cn rho (sqr speed-mag) surface wingspan))
(def roll-moment (* 0.5 cr rho (sqr speed-mag) surface wingspan))

(def force-wind-system (vec3 (- drag) side-force (- lift)))
(def force-body-system (q/rotate-vector wind-to-body force-wind-system))
(def force-world (q/rotate-vector orientation force-body-system))

(def moment-body-system (vec3 roll-moment pitch-moment yaw-moment))
(def moment-world (q/rotate-vector orientation moment-body-system))

(def rho (density-at-height height))
