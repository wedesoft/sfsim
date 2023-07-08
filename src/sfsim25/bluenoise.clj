(ns sfsim25.bluenoise
    "Functions and main program for generating blue noise"
    (:require [clojure.math :refer (exp)]
              [com.climate.claypoole :refer (pfor ncpus)]))

; http://cv.ulichney.com/papers/1993-void-cluster.pdf

(set! *unchecked-math* true)

(defn indices-2d
  "Create range of indices with M x M elements"
  [m] (range (* m m)))

(defn pick-n
  "Randomly pick N different values from ARR"
  ([arr n] (pick-n arr n shuffle))
  ([arr n order] (take n (order arr))))

(defn scatter-mask
  "Create mask of size M x M filled with specified indices set to true"
  [arr m]
  (reduce #(assoc %1 %2 true) (vec (repeat (* m m) false)) arr))

(defn density-function
  "Return 2D Gauss bell function for given SIGMA"
  [sigma]
  (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(defn argmax-with-mask
  "Return index of largest element in ARR with corresponding MASK value begin true"
  [arr mask]
  (first (apply max-key second (filter (fn [[idx _value]] (nth mask idx)) (map-indexed vector arr)))))

(defn argmin-with-mask
  "Return index of smallest element in ARR with corresponding MASK value begin false"
  [arr mask]
  (first (apply min-key second (remove (fn [[idx _value]] (nth mask idx)) (map-indexed vector arr)))))

(defn wrap
  "Wrap index X to be within -M/2 and +M/2"
  [x m]
  (let [offset (quot m 2)]
    (- (mod (+ x offset) m) offset)))

(defn density-sample
  "Compute sample of convolution of MASK with F"
  [mask m f cx cy]
  (reduce +
    (for [y (range m) x (range m)]
       (let [index (+ (* y m) x)]
         (if (nth mask index)
           (f (wrap (- x cx) m) (wrap (- y cy) m))
           0)))))

(defn density-array
  "Convolve MASK of size M x M with F"
  [mask m f]
  (vec (pfor (+ 2 (ncpus)) [cy (range m) cx (range m)] (density-sample mask m f cx cy))))

(defn density-change [density m op f index]
  (let [cy (quot index m)
        cx (mod index m)]
    (pfor (+ 2 (ncpus)) [y (range m) x (range m)]
          (let [index (+ (* y m) x)]
            (op (nth density index) (f (wrap (- x cx) m) (wrap (- y cy) m)))))))

(defn seed-pattern
  "Create initial seed pattern by distributing the values in MASK evenly"
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

(set! *unchecked-math* false)
