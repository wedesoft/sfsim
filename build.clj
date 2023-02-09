(ns build
    (:require [clojure.tools.build.api :as b]
              [sfsim25.worley :as w]
              [sfsim25.scale-image :as si]
              [sfsim25.scale-elevation :as se]
              [sfsim25.bluenoise :as bn]
              [sfsim25.atmosphere-lut :as al]
              [sfsim25.util :as u]))

(defn worley [& {:keys [size divisions] :or {size 64 divisions 8}}]
  "Generate 3D Worley noise texture"
  [size divisions]
  (let [noise     (w/worley-noise divisions size true)]
    (u/spit-floats "data/worley.raw" (float-array noise))))

(defn scale-image [& {:keys [input output]}]
  "Scale down input PNG and save to output PNG"
  (si/scale-image (str input) (str output)))

(defn scale-elevation [& {:keys [input output]}]
  "Scale down raw short-integer elevation input data and save to output raw short integers"
  (se/scale-elevation (str input) (str output)))

(defn bluenoise [& {:keys [size] :or {size 64}}]
  "Generate 2D blue noise texture"
  (let [n      (quot (* size size) 10)
        sigma  1.5
        dither (bn/blue-noise size n sigma)]
    (u/spit-floats "data/bluenoise.raw" (float-array (map #(/ % size size) dither)))))

(defn atmosphere-lut [_]
  "Generate atmospheric lookup tables"
  (al/generate-atmosphere-luts))

(defn clean [_]
  "Clean secondary files"
  (b/delete {:path "data/worley.raw"})
  (b/delete {:path "data/bluenoise.raw"})
  (b/delete {:path "data/atmosphere/mie-strength.scatter"})
  (b/delete {:path "data/atmosphere/ray-scatter.scatter"})
  (b/delete {:path "data/atmosphere/surface-radiance.scatter"})
  (b/delete {:path "data/atmosphere/transmittance.scatter"}))
