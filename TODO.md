# TODO
* Get scale-image to work on large images
* implement fast version of slurp-shorts and slurp-floats

```
(require '[clojure.core.memoize :as m])
(def id (clojure.core.memoize/lru
        #(do (Thread/sleep 5000) (identity %))
    :lru/threshold 3))

(defn sqrt [x & {:keys [error] :or {error 0.001}}]
  (defn good-enough? [guess] (< (Math/abs (- (* guess guess) x)) error))
  (defn improve [guess] (/ (+ guess (/ x guess)) 2))
  (defn sqrt-iter [guess]
    (if (good-enough? guess)
      guess
      (recur (improve guess))))
  (sqrt-iter 1.0))

(import '[magick MagickImage ImageInfo ColorspaceType])
(def info (ImageInfo. "world/0/0/0.png"))
(def image (MagickImage. info))
(.getDimension image)
(require '[clojure.reflect :as r])
(defn members [o] (sort (map :name (:members (r/reflect o)))))
(members image)
(.getOnePixel image 100 100)

(def image (MagickImage.))
(def b (byte-array (take (* 3 256 256) (cycle [0 0 -1]))))
(doseq [i (range 256)] (aset-byte b (* i 3 257) -1))
(.constituteImage image 256 256 "RGB" b)
; (.allocateImage image info)
(def info (ImageInfo.))
(.setSize info "256x256")
(.setDepth info 8)
(.setColorspace info ColorspaceType/RGBColorspace)
(.setFileName image "test.jpg")
(.writeImage image info)
```
