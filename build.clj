(ns build
    (:require [clojure.tools.build.api :as b]
              [sfsim25.worley :as w]
              [sfsim25.scale-image :as si]
              [sfsim25.scale-elevation :as se]
              [sfsim25.util :as u]))

(defn worley [& {:keys [size divisions] :or {size 64 divisions 8}}]
  [size divisions]
  (let [noise     (w/worley-noise divisions size true)]
    (u/spit-floats "data/worley.raw" (float-array noise))))

(defn scale-image [& {:keys [input output]}]
  "Scale input PNG and save to output PNG"
  (si/scale-image (str input) (str output)))

(defn scale-elevation [& {:keys [input output]}]
  "Scale raw short-integer elevation input data and save to output raw short integers"
  (se/scale-elevation (str input) (str output)))

(defn clean [_]
  "Clean secondary files"
  (b/delete {:path "data"}))
