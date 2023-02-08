(ns build
    (:require [clojure.tools.build.api :as b]
              [build.worley :as w]))

(defn worley [& {:keys [size divisions] :or {size 64 divisions 8}}]
  (w/generate-worley-noise size divisions))

(defn clean [_]
  (b/delete {:path "data"}))
