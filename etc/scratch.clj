(require '[clojure.reflect :as r]
         '[sfsim25.util :refer :all]
         '[clojure.java.io :as io])

(import '[javax.imageio ImageIO]
        '[java.io File]
        '[java.awt.image BufferedImage]
        '[org.lwjgl.stb STBImage STBImageWrite])

(def img (with-open [input-file (io/input-stream "/tmp/himalayas.jpg")] (ImageIO/read input-file)))
(def img (with-open [input-file (io/input-stream "test/sfsim25/fixtures/clouds/overlay.png")] (ImageIO/read input-file)))

(def tmp (BufferedImage. (.getWidth img) (.getHeight img) (BufferedImage/TYPE_4BYTE_ABGR)))
(.drawImage (.createGraphics tmp) img 0 0 (.getWidth img) (.getHeight img) nil)

(def t {:width (.getWidth img) :height (.getHeight img) :data (.getData (.getDataBuffer (.getRaster tmp)))})

(slurp-image "/tmp/himalayas.jpg")

(get-pixel img 0 0)

(spit-png "/tmp/t.png" t)
