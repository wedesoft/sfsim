(require '[clojure.math :refer (sqrt)])
(require '[clojure.core.matrix :refer (matrix mget add mul inverse det set-current-implementation dot mmul eseq)])
(require '[clojure.core.matrix.linear :refer (norm)])
(require '[criterium.core :refer :all])
(import '[mikera.matrixx Matrix Matrixx])
(import '[mikera.vectorz Vector])
(import '[org.ejml.simple SimpleMatrix SimpleBase])
(import '[org.ejml.data DMatrixRMaj])

; support for both single and double precision floating point numbers
; only 2D matrices (vectors need to be presented as single column matrix)
; access to raw data array
; no custom 3x3 implementation
; faster 4x4 inverse

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)
(set-current-implementation :vectorz)

(defmacro mybench [expr] `(do (println (quote ~expr)) (bench ~expr) (println)))

(def a (matrix [[1.0 1.0 1.0 1.0] [0.0 2.0 2.0 2.0] [0.0 0.0 4.0 4.0] [0.0 0.0 0.0 8.0]]))
(def b (SimpleMatrix. 4 4 true (double-array [1.0 1.0 1.0 1.0 0.0 2.0 2.0 2.0 0.0 0.0 4.0 4.0 0.0 0.0 0.0 8.0])))
(def c (Matrixx/create [[1.0 1.0 1.0 1.0] [0.0 2.0 2.0 2.0] [0.0 0.0 4.0 4.0] [0.0 0.0 0.0 8.0]]))
(def u (matrix [1.0 2.0 3.0 4.0]))
(def v (SimpleMatrix. 4 1 true (double-array [1.0 2.0 3.0 4.0])))
(def w (Vector/create [1.0 2.0 3.0 4.0]))

(println "--------------------------------------------------------------------------------")
(mybench (matrix [[1.0 1.0 1.0 1.0] [0.0 2.0 2.0 2.0] [0.0 0.0 4.0 4.0] [0.0 0.0 0.0 8.0]]))
(mybench (SimpleMatrix. 4 4 true (double-array [1.0 1.0 1.0 1.0 0.0 2.0 2.0 2.0 0.0 0.0 4.0 4.0 0.0 0.0 0.0 8.0])))
(mybench (Matrixx/create [[1.0 1.0 1.0 1.0] [0.0 2.0 2.0 2.0] [0.0 0.0 4.0 4.0] [0.0 0.0 0.0 8.0]]))

(println "--------------------------------------------------------------------------------")
(mybench (matrix [1.0 2.0 3.0 4.0]))
(mybench (SimpleMatrix. 4 1 true (double-array [1.0 2.0 3.0 4.0])))
(mybench (Vector/create [1.0 2.0 3.0 4.0]))

(println "--------------------------------------------------------------------------------")
(mybench (add ^Vector u ^Vector u))
(mybench (.plus ^SimpleMatrix v ^SimpleMatrix v))
(mybench (let [result (.clone ^Matrix c)] (.add ^Matrix result ^Matrix c) result))

(println "--------------------------------------------------------------------------------")
(mybench (inverse ^Matrix a))
(mybench (.invert ^SimpleMatrix b))
(mybench (.inverse ^Matrix c))

(println "--------------------------------------------------------------------------------")
(mybench (mul ^Matrix a ^Matrix a))
(mybench (.elementMult ^SimpleMatrix b ^SimpleMatrix b))
(mybench (let [result (.clone ^Matrix c)] (.multiply ^Matrix result ^Matrix c) result))

(println "--------------------------------------------------------------------------------")
(mybench (mmul ^Matrix a ^Matrix a))
(mybench (.mult ^SimpleMatrix b ^SimpleMatrix b))
(mybench (.innerProduct ^Matrix c ^Matrix c))

(println "--------------------------------------------------------------------------------")
(mybench (mmul ^Matrix a ^Vector u))
(mybench (.mult ^SimpleMatrix b ^SimpleMatrix v))
(mybench (.innerProduct ^Matrix c ^Vector w))

(println "--------------------------------------------------------------------------------")
(mybench (dot ^Vector u ^Vector u))
(mybench (.dot ^SimpleMatrix v ^SimpleMatrix v))
(mybench (.innerProduct ^Vector w ^Vector w))

(println "--------------------------------------------------------------------------------")
(mybench (norm ^Matrix u))
(mybench (.normF ^SimpleMatrix v))

(println "--------------------------------------------------------------------------------")
(mybench (det ^Matrix a))
(mybench (.determinant ^SimpleMatrix b))
(mybench (.determinant ^Matrix c))

(println "--------------------------------------------------------------------------------")
(mybench (mget ^Matrix a 1 1))
(mybench (.get ^SimpleMatrix b 1 1))
(mybench (.get ^Matrix a 1 1))

(println "--------------------------------------------------------------------------------")
(mybench (.getElements ^Matrix a))
(mybench (.data ^DMatrixRMaj (.getMatrix ^SimpleMatrix b)))
(mybench (.getElements ^Matrix c))
