(ns build
    (:require [clojure.tools.build.api :as b]
              [sfsim25.worley :as w]
              [sfsim25.scale-image :as si]
              [sfsim25.scale-elevation :as se]
              [sfsim25.bluenoise :as bn]
              [sfsim25.atmosphere-lut :as al]
              [sfsim25.map-tiles :as mt]
              [sfsim25.util :as u]))

(defn worley
  "Generate 3D Worley noise texture"
  [& {:keys [size divisions] :or {size 64 divisions 8}}]
  (let [noise     (w/worley-noise divisions size true)]
    (u/spit-floats "data/worley.raw" (float-array noise))))

(defn scale-image
  "Scale down input PNG and save to output PNG"
  [& {:keys [input output]}]
  (si/scale-image (str input) (str output)))

(defn scale-elevation
  "Scale down raw short-integer elevation input data and save to output raw short integers"
  [& {:keys [input output]}]
  (se/scale-elevation (str input) (str output)))

(defn bluenoise
  "Generate 2D blue noise texture"
  [& {:keys [size] :or {size 64}}]
  (let [n      (quot (* size size) 10)
        sigma  1.5
        dither (bn/blue-noise size n sigma)]
    (u/spit-floats "data/bluenoise.raw" (float-array (map #(/ % size size) dither)))))

(defn atmosphere-lut [_]
  "Generate atmospheric lookup tables"
  (al/generate-atmosphere-luts))

(defn map-tiles
  "Generate map tiles from specified image"
  [& {:keys [image tilesize level prefix y-offset x-offset] :or {tilesize 675}}]
  (mt/make-map-tiles image tilesize level prefix y-offset x-offset))

(defn map-scales
  "Generate pyramid of scales for given sector of world map"
  [& {:keys [sector]}]
  (scale-image {:input (str "world.200404.3x21600x21600." sector ".png") :output (str "world." sector "." 2 ".png")})
  (scale-image {:input (str "world." sector "." 2 ".png") :output (str "world." sector "." 3 ".png")})
  (scale-image {:input (str "world." sector "." 3 ".png") :output (str "world." sector "." 4 ".png")})
  (scale-image {:input (str "world." sector "." 4 ".png") :output (str "world." sector "." 5 ".png")})
  (scale-image {:input (str "world." sector "." 5 ".png") :output (str "world." sector "." 6 ".png")}))

(defn map-sector-tiles
  "Generate pyramid of map tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (map-tiles {:image (str "world.200404.3x21600x21600." sector ".png") :level 5 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 2 ".png") :level 4 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 3 ".png") :level 3 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 4 ".png") :level 2 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 5 ".png") :level 1 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 6 ".png") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn map-sector
  "Generate scale pyramid and tiles for given sector"
  [& {:keys [sector prefix y x]}]
  (map-scales {:sector sector})
  (map-sector-tiles {:sector sector :prefix prefix :y y :x x}))

(defn map-sectors
  "Convert all map sectors into a pyramid of map tiles"
  [& {:keys [prefix] :or {prefix "world"}}]
  (map-sector {:sector "A1" :prefix prefix :y 0 :x 0})
  (map-sector {:sector "A2" :prefix prefix :y 1 :x 0})
  (map-sector {:sector "B1" :prefix prefix :y 0 :x 1})
  (map-sector {:sector "B2" :prefix prefix :y 1 :x 1})
  (map-sector {:sector "C1" :prefix prefix :y 0 :x 2})
  (map-sector {:sector "C2" :prefix prefix :y 1 :x 2})
  (map-sector {:sector "D1" :prefix prefix :y 0 :x 3})
  (map-sector {:sector "D2" :prefix prefix :y 1 :x 3}))

(defn clean [_]
  "Clean secondary files"
  (b/delete {:path "data/worley.raw"})
  (b/delete {:path "data/bluenoise.raw"})
  (b/delete {:path "data/atmosphere/mie-strength.scatter"})
  (b/delete {:path "data/atmosphere/ray-scatter.scatter"})
  (b/delete {:path "data/atmosphere/surface-radiance.scatter"})
  (b/delete {:path "data/atmosphere/transmittance.scatter"}))
