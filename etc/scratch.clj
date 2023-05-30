(require '[clojure.reflect :as r])
(import '[org.lwjgl.stb STBImage STBImageWrite]
        '[org.lwjgl BufferUtils])

(defn slurp-image [path]
  "Load an RGB image"
  (let [width (int-array 1)
        height (int-array 1)
        channels (int-array 1)]
    (let [buffer (STBImage/stbi_load path width height channels 4)
          width  (aget width 0)
          height (aget height 0)
          data   (int-array (* width height))]
      (.get (.asIntBuffer buffer) data)
      {:data data :width width :height height :channels (aget channels 0)})))

(defn spit-image
  "Save RGB image as PNG file"
  [path {:keys [width height data] :as img}]
  (let [byte-buffer (BufferUtils/createByteBuffer (* 4 (count data)))
        int-buffer  (-> byte-buffer (.asIntBuffer) (.put data) (.flip))]
    (STBImageWrite/stbi_write_png path width height 4 byte-buffer (* 4 width))
    img))


(def img (slurp-image "sun.png"))
(spit-image "test.png" img)
