(ns sfsim25.bluenoise
    "Functions and main program for generating blue noise"
    (:require [clojure.math :refer (exp)]
              [com.climate.claypoole :refer (pfor ncpus)]
              [sfsim25.util :refer :all]))

; http://cv.ulichney.com/papers/1993-void-cluster.pdf

(set! *unchecked-math* true)

(defn indices-2d [m] (range (* m m)))

(defn pick-n
  ([arr n] (pick-n arr n shuffle))
  ([arr n order] (take n (order arr))))

(defn scatter-mask [arr m]
  (reduce #(assoc %1 %2 true) (vec (repeat (* m m) false)) arr))

(defn density-function [sigma] (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(defn argmax-with-mask [arr mask]
  (first (apply max-key second (filter (fn [[idx value]] (nth mask idx)) (map-indexed vector arr)))))

(defn argmin-with-mask [arr mask]
  (first (apply min-key second (remove (fn [[idx value]] (nth mask idx)) (map-indexed vector arr)))))

(defn wrap [x m]
  (let [offset (quot m 2)]
    (- (mod (+ x offset) m) offset)))

(defn density-sample [mask m f cx cy]
  (reduce +
    (for [y (range m) x (range m)]
       (let [index (+ (* y m) x)]
         (if (nth mask index)
           (f (wrap (- x cx) m) (wrap (- y cy) m))
           0)))))

(defn density-array [mask m f]
  (vec (pfor (+ 2 (ncpus)) [cy (range m) cx (range m)] (density-sample mask m f cx cy))))

(defn density-change [density m op f index]
  (let [cy (quot index m)
        cx (mod index m)]
    (pfor (+ 2 (ncpus)) [y (range m) x (range m)]
          (let [index (+ (* y m) x)]
            (op (nth density index) (f (wrap (- x cx) m) (wrap (- y cy) m)))))))

(defn seed-pattern
  ([mask m f] (seed-pattern mask m f (density-array mask m f)))
  ([mask m f density]
   (let [cluster (argmax-with-mask density mask)
         mask    (assoc mask cluster false)]
     (let [density (density-change density m - f cluster)
           void    (argmin-with-mask density mask)
           mask    (assoc mask void true)]
       (if (= cluster void)
         mask
         (recur mask m f (density-change density m + f void)))))))

(defn dither-phase1
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
  ([mask m n dither f] (dither-phase2 mask m n dither f (density-array mask m f)))
  ([mask m n dither f density]
   (if (>= n (quot (* m m) 2))
     [dither mask]
     (let [void    (argmin-with-mask density mask)
           density (density-change density m + f void)
           mask    (assoc mask void true)]
       (recur mask m (inc n) (assoc dither void n) f density)))))

(defn dither-phase3
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

(defn -main
  "Program to generate blue noise tile"
  [& args]
  (let [m             64
        n             (quot (* m m) 10)
        mask          (scatter-mask (pick-n (indices-2d m) n) m)
        f             (density-function 1.5)
        seed          (seed-pattern mask m f)
        density       (density-array seed m f)
        dither        (dither-phase1 seed m n f density)
        [dither half] (dither-phase2 seed m n dither f density)
        dither        (dither-phase3 half m (quot (* m m) 2) dither f)]
    (spit-floats "data/bluenoise.raw" (float-array (map #(/ % m m) dither)))
    (System/exit 0)))

(set! *unchecked-math* false)
