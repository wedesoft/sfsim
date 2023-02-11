(ns build
    (:require [clojure.tools.build.api :as b]
              [clojure.java.io :as io]
              [babashka.http-client :as http]
              [sfsim25.worley :as w]
              [sfsim25.scale-image :as si]
              [sfsim25.scale-elevation :as se]
              [sfsim25.bluenoise :as bn]
              [sfsim25.atmosphere-lut :as al]
              [sfsim25.map-tiles :as mt]
              [sfsim25.elevation-tiles :as et]
              [sfsim25.globe :as g]
              [sfsim25.util :as u]))

(defn worley
  "Generate 3D Worley noise texture"
  [& {:keys [size divisions] :or {size 64 divisions 8}}]
  (let [noise     (w/worley-noise divisions size true)]
    (u/spit-floats "data/worley.raw" (float-array noise))))

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

(defn scale-image
  "Scale down input PNG and save to output PNG"
  [& {:keys [input output]}]
  (si/scale-image (str input) (str output)))

(defn scale-elevation
  "Scale down raw short-integer elevation input data and save to output raw short integers"
  [& {:keys [input output]}]
  (se/scale-elevation (str input) (str output)))

(defn download-bluemarble
  "Download some NASA Bluemarble data from https://visibleearth.nasa.gov/images/74017/april-blue-marble-next-generation/74037"
  [_]
  (doseq [sector ["A1" "A2" "B1" "B2" "C1" "C2" "D1" "D2"]]
         (let [filename (str "world.200404.3x21600x21600." sector ".png")
               url      (str "https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74017/" filename)]
           (.println *err* (str "Downloading " url " ..."))
           (io/copy
             (:body (http/get url {:as :stream}))
             (io/file filename)))))

(defn download-elevation
  "Download NOAA elevation data from https://www.ngdc.noaa.gov/mgg/topo/gltiles.html"
  [_]
  (let [filename "all10g.zip"
        url      (str "https://www.ngdc.noaa.gov/mgg/topo/DATATILES/elev/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (:body (http/get url {:as :stream}))
      (io/file filename))))

(defn extract-elevation
  "Extract and concatenate elevation files"
  [_]
  (b/unzip {:zip-file "all10g.zip" :target-dir "."})
  (with-open [out (io/output-stream "elevation.A1.raw")]
    (io/copy (io/file "all10/a10g") out) (io/copy (io/file "all10/e10g") out))
  (with-open [out (io/output-stream "elevation.B1.raw")]
    (io/copy (io/file "all10/b10g") out) (io/copy (io/file "all10/f10g") out))
  (with-open [out (io/output-stream "elevation.C1.raw")]
    (io/copy (io/file "all10/c10g") out) (io/copy (io/file "all10/g10g") out))
  (with-open [out (io/output-stream "elevation.D1.raw")]
    (io/copy (io/file "all10/d10g") out) (io/copy (io/file "all10/h10g") out))
  (with-open [out (io/output-stream "elevation.A2.raw")]
    (io/copy (io/file "all10/i10g") out) (io/copy (io/file "all10/m10g") out))
  (with-open [out (io/output-stream "elevation.B2.raw")]
    (io/copy (io/file "all10/j10g") out) (io/copy (io/file "all10/n10g") out))
  (with-open [out (io/output-stream "elevation.C2.raw")]
    (io/copy (io/file "all10/k10g") out) (io/copy (io/file "all10/o10g") out))
  (with-open [out (io/output-stream "elevation.D2.raw")]
    (io/copy (io/file "all10/l10g") out) (io/copy (io/file "all10/p10g") out)))

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
  "Generate scale pyramid and map tiles for given sector"
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

(defn elevation-scales
  "Generate pyeramid of scales for given "
  [& {:keys [sector]}]
  (scale-elevation {:input (str "elevation." sector ".raw") :output (str "elevation." sector "." 2 ".raw")})
  (scale-elevation {:input (str "elevation." sector "." 2 ".raw") :output (str "elevation." sector "." 3 ".raw")})
  (scale-elevation {:input (str "elevation." sector "." 3 ".raw") :output (str "elevation." sector "." 4 ".raw")})
  (scale-elevation {:input (str "elevation." sector "." 4 ".raw") :output (str "elevation." sector "." 5 ".raw")}))

(defn elevation-tiles
  "Generate map tiles from specified image"
  [& {:keys [image tilesize level prefix y-offset x-offset] :or {tilesize 675}}]
  (et/make-elevation-tiles image tilesize level prefix y-offset x-offset))

(defn elevation-sector-tiles
  "Generate pyramid of elevation tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (elevation-tiles {:image (str "elevation." sector ".raw") :level 4 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "elevation." sector "." 2 ".raw") :level 3 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "elevation." sector "." 3 ".raw") :level 2 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "elevation." sector "." 4 ".raw") :level 1 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "elevation." sector "." 5 ".raw") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn elevation-sector
  "Generate scale pyramid and elevation tiles for given sector"
  [& {:keys [sector prefix y x]}]
  (elevation-scales {:sector sector})
  (elevation-sector-tiles {:sector sector :prefix prefix :y y :x x}))

(defn elevation-sectors
  "Convert all elevation sectors into a pyramid of elevation tiles"
  [& {:keys [prefix] :or {prefix "elevation"}}]
  (elevation-sector {:sector "A1" :prefix prefix :y 0 :x 0})
  (elevation-sector {:sector "A2" :prefix prefix :y 1 :x 0})
  (elevation-sector {:sector "B1" :prefix prefix :y 0 :x 1})
  (elevation-sector {:sector "B2" :prefix prefix :y 1 :x 1})
  (elevation-sector {:sector "C1" :prefix prefix :y 0 :x 2})
  (elevation-sector {:sector "C2" :prefix prefix :y 1 :x 2})
  (elevation-sector {:sector "D1" :prefix prefix :y 0 :x 3})
  (elevation-sector {:sector "D2" :prefix prefix :y 1 :x 3}))

(defn cube-map
  "Create cub map level from map and elevation tiles"
  [& {:keys [in-level out-level]}]
  (g/make-cube-map in-level out-level))

(defn cube-maps
  "Create pyramid of cube maps"
  [_]
  (cube-map {:in-level 0 :out-level 0})
  (cube-map {:in-level 0 :out-level 1})
  (cube-map {:in-level 0 :out-level 2})
  (cube-map {:in-level 1 :out-level 3})
  (cube-map {:in-level 2 :out-level 4})
  (cube-map {:in-level 3 :out-level 5}))

(defn clean [_]
  "Clean secondary files"
  (b/delete {:path "data/worley.raw"})
  (b/delete {:path "data/bluenoise.raw"})
  (b/delete {:path "data/atmosphere/mie-strength.scatter"})
  (b/delete {:path "data/atmosphere/ray-scatter.scatter"})
  (b/delete {:path "data/atmosphere/surface-radiance.scatter"})
  (b/delete {:path "data/atmosphere/transmittance.scatter"})
  (b/delete {:path "world"})
  (b/delete {:path "elevation"})
  (b/delete {:path "globe"}))

(defn all [_]
  (worley)
  (bluenoise)
  (atmosphere-lut)
  (download-bluemarble)
  (download-elevation)
  (extract-elevation)
  (map-sectors)
  (elevation-sectors)
  (cube-maps))
