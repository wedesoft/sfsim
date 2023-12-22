(ns sfsim25.bluenoise
    "Functions and main program for generating blue noise"
    (:require [clojure.math :refer (exp)]
              [com.climate.claypoole :refer (pfor ncpus)]
              [malli.core :as m]
              [sfsim25.util :refer (N0 N)]))

; http://cv.ulichney.com/papers/1993-void-cluster.pdf

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def noise-size 64)

(defn indices-2d
  "Create range of indices with M x M elements"
  {:malli/schema [:=> [:cat N] [:vector N0]]}
  [m] (vec (range (* m m))))

(defn pick-n
  "Randomly pick N different values from ARR"
  {:malli/schema [:function [:=> [:cat [:vector :some] N0] [:vector :some]] [:=> [:cat [:vector :some] N0 fn?] [:vector :some]]]}
  ([arr n] (pick-n arr n shuffle))
  ([arr n order] (vec (take n (order arr)))))

(defn scatter-mask
  "Create mask of size M x M filled with specified indices set to true"
  {:malli/schema [:=> [:cat [:vector N0] N] [:vector :boolean]]}
  [arr m]
  (reduce #(assoc %1 %2 true) (vec (repeat (* m m) false)) arr))

(def F (m/schema [:=> [:cat :int :int] :double]))

(defn density-function
  "Return 2D Gauss bell function for given SIGMA"
  {:malli/schema [:=> [:cat :double] F]}
  [sigma]
  (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(defn argmax-with-mask
  "Return index of largest element in ARR with corresponding MASK value begin true"
  {:malli/schema [:=> [:cat [:sequential number?] [:sequential :boolean]] N0]}
  [arr mask]
  (first (apply max-key second (filter (fn [[idx _value]] (nth mask idx)) (map-indexed vector arr)))))

(defn argmin-with-mask
  "Return index of smallest element in ARR with corresponding MASK value begin false"
  {:malli/schema [:=> [:cat [:sequential number?] [:sequential :boolean]] N0]}
  [arr mask]
  (first (apply min-key second (remove (fn [[idx _value]] (nth mask idx)) (map-indexed vector arr)))))

(defn wrap
  "Wrap index X to be within -M/2 and +M/2"
  {:malli/schema [:=> [:cat :int N] :int]}
  [x m]
  (let [offset (quot m 2)]
    (- (mod (+ x offset) m) offset)))

(defn density-sample
  "Compute sample of convolution of MASK with F"
  {:malli/schema [:=> [:cat [:vector :boolean] N F :int :int] :double]}
  [mask m f cx cy]
  (reduce +
    (for [y (range m) x (range m)]
       (let [index (+ (* y m) x)]
         (if (nth mask index)
           (f (wrap (- x cx) m) (wrap (- y cy) m))
           0.0)))))

(defn density-array
  "Convolve MASK of size M x M with F"
  {:malli/schema [:=> [:cat [:vector :boolean] N F] [:vector :double]]}
  [mask m f]
  (vec (pfor (+ 2 (ncpus)) [cy (range m) cx (range m)] (density-sample mask m f cx cy))))

(defn density-change
  "Compute array with changes to density"
  {:malli/schema [:=> [:cat [:vector :double] N fn? F N0] [:vector :double]]}
  [density m op f index]
  (let [cy (quot index m)
        cx (mod index m)]
    (vec
      (pfor (+ 2 (ncpus)) [y (range m) x (range m)]
            (let [index (+ (* y m) x)]
              (op (nth density index) (f (wrap (- x cx) m) (wrap (- y cy) m))))))))

(defn seed-pattern
  "Create initial seed pattern by distributing the values in MASK evenly"
  {:malli/schema [:function
                  [:=> [:cat [:vector :boolean] N F] [:vector :boolean]]
                  [:=> [:cat [:vector :boolean] N F [:vector :double]] [:vector :boolean]]]}
  ([mask m f] (seed-pattern mask m f (density-array mask m f)))
  ([mask m f density]
   (let [cluster (argmax-with-mask density mask)
         mask    (assoc mask cluster false)
         density (density-change density m - f cluster)
         void    (argmin-with-mask density mask)
         mask    (assoc mask void true)]
     (if (== cluster void)
       mask
       (recur mask m f (density-change density m + f void))))))

(defn dither-phase1
  "First phase of blue noise dithering removing true values from MASK"
  {:malli/schema [:function
                  [:=> [:cat [:vector :boolean] N N0 F] [:vector N0]]
                  [:=> [:cat [:vector :boolean] N N0 F [:vector :double]] [:vector N0]]
                  [:=> [:cat [:vector :boolean] N N0 F [:vector :double] [:vector N0]] [:vector N0]]]}
  ([mask m n f] (dither-phase1 mask m n f (density-array mask m f)))
  ([mask m n f density] (dither-phase1 mask m n f density (vec (repeat (* m m) 0))))
  ([mask m n f density dither]
   (if (zero? n)
     dither
     (let [cluster (argmax-with-mask density mask)
           density (density-change density m - f cluster)
           mask    (assoc mask cluster false)]
       (recur mask m (dec n) f density (assoc dither cluster (dec n)))))))

(defn dither-phase2
  "Second phase of blue noise dithering filling MASK until it is 50% set to true"
  {:malli/schema [:function
                  [:=> [:cat [:vector :boolean] N N0 [:vector N0] F] [:vector [:vector :some]]]
                  [:=> [:cat [:vector :boolean] N N0 [:vector N0] F [:vector :double]] [:vector [:vector :some]]]]}
  ([mask m n dither f] (dither-phase2 mask m n dither f (density-array mask m f)))
  ([mask m n dither f density]
   (if (>= n (quot (* m m) 2))
     [dither mask]
     (let [void    (argmin-with-mask density mask)
           density (density-change density m + f void)
           mask    (assoc mask void true)]
       (recur mask m (inc n) (assoc dither void n) f density)))))

(defn dither-phase3
  "Third phase of blue noise dithering negating MASK and then removing true values"
  {:malli/schema [:function
                  [:=> [:cat [:vector :boolean] N N0 [:vector N0] F] [:vector N0]]
                  [:=> [:cat [:vector :boolean] N N0 [:vector N0] F [:vector :double]] [:vector N0]]]}
  ([mask m n dither f]
   (let [mask-not (mapv not mask)]
     (dither-phase3 mask-not m n dither f (density-array mask-not m f))))
  ([mask-not m n dither f density]
   (if (>= n (* m m))
     dither
     (let [cluster  (argmax-with-mask density mask-not)
           density  (density-change density m - f cluster)
           mask-not (assoc mask-not cluster false)]
       (recur mask-not m (inc n) (assoc dither cluster n) f density)))))

(defn blue-noise
  "Greate blue noise dithering array of size M x M starting with seed pattern of N samples"
  {:malli/schema [:=> [:cat N N0 :double] [:vector N0]]}
  [m n sigma]
  (let [mask          (scatter-mask (pick-n (indices-2d m) n) m)
        f             (density-function sigma)
        seed          (seed-pattern mask m f)
        density       (density-array seed m f)
        dither        (dither-phase1 seed m n f density)
        [dither half] (dither-phase2 seed m n dither f density)
        dither        (dither-phase3 half m (quot (* m m) 2) dither f)]
    dither))

(def sampling-offset
  "Shader for sampling blue noise texture"
  (slurp "resources/shaders/bluenoise/sampling-offset.glsl"))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
