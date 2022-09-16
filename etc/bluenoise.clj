; http://cv.ulichney.com/papers/1993-void-cluster.pdf
(require '[sfsim25.util :refer :all])

(import '[ij.process ByteProcessor]
        '[ij ImagePlus])

(def M 16)
(def Ones 26)

(defn indices [M] (range (* M M)))

(defn pick-n [values n] (take n (shuffle values)))

(defn initial [M Ones]
  (let [result (boolean-array (* M M))]
    (doseq [index (pick-n (indices M) Ones)]
           (aset-boolean result index true))
    result))

(defn show-bools [M data]
  (let [processor (ByteProcessor. M M (byte-array (map #(if % 0 255) data)))
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))
