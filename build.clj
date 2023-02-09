(ns build
    (:require [clojure.tools.build.api :as b]
              [sfsim25.worley :as w]
              [sfsim25.util :as u]))

(defn worley [& {:keys [size divisions] :or {size 64 divisions 8}}]
  [size divisions]
  (let [noise     (w/worley-noise divisions size true)]
    (u/spit-floats "data/worley.raw" (float-array noise))))

(defn clean [_]
  (b/delete {:path "data"}))
