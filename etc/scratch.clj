(require '[clojure.reflect :as r]
         '[sfsim25.util :refer :all])

(def img (slurp-image "test/sfsim25/fixtures/render/red.png"))
(spit-png "test.png" img)

(get-pixel img 0 0)
