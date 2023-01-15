(require '[clojure.math :refer (to-radians cos sin PI)]
         '[clojure.core.matrix :refer (matrix add mul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all]
         '[sfsim25.shaders :as shaders])


(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
(Display/create)

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) (to-radians fov)))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 3050)])))
(def light (atom (* 0.5 PI)))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def keystates (atom {}))


(Display/destroy)
