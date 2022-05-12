(require '[clojure.core.matrix :refer (matrix div sub mul add mget) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin to-radians)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.ray :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

; ------------------------------------------------------------------------------
(def d 3)
(def size 64)

(defn points [n] (vec (repeatedly n #(mul size (matrix (repeatedly d rand))))))

; TODO: repeat points in all directions

(def points1 (points 30))
(def points2 (points 120))

(defn values [points] (vec (cp/pfor (+ 2 (cp/ncpus)) [i (range size) j (range size) k (range size)]
                           (apply min (map (fn [point] (norm (sub point (matrix [i j k])))) points)))))

(defn normed [values] (let [largest (apply max values)] (vec (pmap #(/ % largest) values))))
(defn inverted [values] (vec (pmap #(- 1 %) values)))

(def values1 (normed (values points1)))
(def values2 (normed (values points2)))

(def mixed (inverted (pmap #(* %1 (+ 0.25 (* 0.75 %2))) values1 values2)))

(show-floats {:width size :height size :data (float-array (take (* size size) mixed))})

; ------------------------------------------------------------------------------
(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(def z-near 10)
(def z-far 1000)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))
(def origin (matrix [0 0 -100]))
(def transform (transformation-matrix (quaternion->matrix (q/rotation (to-radians 90) (matrix [1 0 0]))) origin))

(onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0.5 0.5 0.5])) )

(Display/destroy)
