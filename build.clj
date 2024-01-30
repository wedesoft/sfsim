; See https://clojure.github.io/tools.build/clojure.tools.build.api.html
(ns build
    (:require [clojure.tools.build.api :as b]
              [clojure.java.io :as io]
              [clojure.java.shell :refer (sh)]
              [sfsim.worley :as w]
              [sfsim.perlin :as p]
              [sfsim.scale-image :as si]
              [sfsim.scale-elevation :as se]
              [sfsim.bluenoise :as bn]
              [sfsim.render :as rn]
              [sfsim.clouds :as cl]
              [sfsim.atmosphere-lut :as al]
              [sfsim.map-tiles :as mt]
              [sfsim.elevation-tiles :as et]
              [sfsim.globe :as g]
              [sfsim.util :as u])
    (:import [org.lwjgl.glfw GLFW]))

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(defn worley
  "Generate 3D Worley noise textures"
  [& {:keys [size divisions] :or {size 64 divisions 8}}]
  (doseq [filename ["worley-north.raw" "worley-south.raw" "worley-cover.raw"]]
         (u/spit-floats (str "data/clouds/" filename) (float-array (w/worley-noise divisions size true)))))

(defn perlin
  "Generate 3D Perlin noise textures"
  [& {:keys [size divisions] :or {size 64 divisions 8}}]
  (u/spit-floats "data/clouds/perlin.raw" (float-array (p/perlin-noise divisions size true))))

(defn bluenoise
  "Generate 2D blue noise texture"
  [& {:keys [size] :or {size bn/noise-size}}]
  (let [n      (quot (* size size) 10)
        sigma  1.5
        dither (bn/blue-noise size n sigma)]
    (u/spit-floats "data/bluenoise.raw" (float-array (map #(/ % size size) dither)))))

(defn cloud-cover
  "Generate cloud cover cubemap"
  [& {:keys [worley-size] :or {worley-size w/worley-size}}]
  (GLFW/glfwInit)
  (rn/with-invisible-window
    (let [load-floats  (fn [filename] #:sfsim.image{:width worley-size :height worley-size :depth worley-size
                                                    :data (u/slurp-floats filename)})
          worley-north (rn/make-float-texture-3d :linear :repeat (load-floats "data/clouds/worley-north.raw"))
          worley-south (rn/make-float-texture-3d :linear :repeat (load-floats "data/clouds/worley-south.raw"))
          worley-cover (rn/make-float-texture-3d :linear :repeat (load-floats "data/clouds/worley-cover.raw"))
          cubemap      (cl/cloud-cover-cubemap :size cl/cover-size
                                               :worley-size worley-size
                                               :worley-south worley-south
                                               :worley-north worley-north
                                               :worley-cover worley-cover
                                               :flow-octaves [0.5 0.25 0.125]
                                               :cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                                               :whirl 1.0
                                               :prevailing 0.0
                                               :curl-scale 2.0
                                               :cover-scale 1.0
                                               :num-iterations 50
                                               :flow-scale 6e-3)]
      (doseq [i (range 6)]
             (u/spit-floats (str "data/clouds/cover" i ".raw") (:sfsim.image/data (rn/float-cubemap->floats cubemap i))))
      (rn/destroy-texture cubemap)
      (rn/destroy-texture worley-cover)
      (rn/destroy-texture worley-south)
      (rn/destroy-texture worley-north)
      (GLFW/glfwTerminate))))

(defn atmosphere-lut [_]
  "Generate atmospheric lookup tables"
  (al/generate-atmosphere-luts))

(defn scale-image-file
  "Scale down input PNG and save to output PNG"
  [& {:keys [input output]}]
  (si/scale-image-file (str input) (str output)))

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
             (io/input-stream url)
             (io/file filename)))))

(defn download-blackmarble
  "Download some NASA Blackmarble data from https://earthobservatory.nasa.gov/features/NightLights/page3.php"
  [_]
  (doseq [sector ["A1" "A2" "B1" "B2" "C1" "C2" "D1" "D2"]]
         (let [filename (str "BlackMarble_2016_" sector ".jpg")
               url      (str "https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/" filename)]
           (.println *err* (str "Downloading " url " ..."))
           (io/copy
             (io/input-stream url)
             (io/file filename)))))

(defn download-elevation
  "Download NOAA elevation data from https://www.ngdc.noaa.gov/mgg/topo/gltiles.html"
  [_]
  (let [filename "all10g.zip"
        url      (str "https://www.ngdc.noaa.gov/mgg/topo/DATATILES/elev/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
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

(defn map-sector-day-tiles
  "Generate pyramid of daytime map tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (map-tiles {:image (str "world.200404.3x21600x21600." sector ".png") :level 5 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 2 ".png") :level 4 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 3 ".png") :level 3 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 4 ".png") :level 2 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 5 ".png") :level 1 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "world." sector "." 6 ".png") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn map-sector-night-tiles
  "Generate pyramid of nighttime map tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (map-tiles {:image (str "BlackMarble_2016_" sector ".jpg") :level 5 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "blackmarble." sector "." 2 ".png") :level 4 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "blackmarble." sector "." 3 ".png") :level 3 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "blackmarble." sector "." 4 ".png") :level 2 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "blackmarble." sector "." 5 ".png") :level 1 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "blackmarble." sector "." 6 ".png") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn map-scales-day
  "Generate pyramid of daytime scales for given sector of world map"
  [& {:keys [sector]}]
  (sh "convert" (str "world.200404.3x21600x21600." sector ".png") "-scale" "50%" (str "world." sector "." 2 ".png"))
  (scale-image-file {:input (str "world." sector "." 2 ".png") :output (str "world." sector "." 3 ".png")})
  (scale-image-file {:input (str "world." sector "." 3 ".png") :output (str "world." sector "." 4 ".png")})
  (scale-image-file {:input (str "world." sector "." 4 ".png") :output (str "world." sector "." 5 ".png")})
  (scale-image-file {:input (str "world." sector "." 5 ".png") :output (str "world." sector "." 6 ".png")}))

(defn map-scales-night
  "Generate pyramid of nighttime scales for given sector of world map"
  [& {:keys [sector]}]
  (sh "convert" (str "BlackMarble_2016_" sector ".jpg") "-scale" "50%" (str "blackmarble." sector "." 2 ".png"))
  (scale-image-file {:input (str "blackmarble." sector "." 2 ".png") :output (str "blackmarble." sector "." 3 ".png")})
  (scale-image-file {:input (str "blackmarble." sector "." 3 ".png") :output (str "blackmarble." sector "." 4 ".png")})
  (scale-image-file {:input (str "blackmarble." sector "." 4 ".png") :output (str "blackmarble." sector "." 5 ".png")})
  (scale-image-file {:input (str "blackmarble." sector "." 5 ".png") :output (str "blackmarble." sector "." 6 ".png")}))

(defn map-sector-day
  "Generate daytime scale pyramid and map tiles for given sector"
  [& {:keys [sector prefix y x]}]
  (map-scales-day {:sector sector})
  (map-sector-day-tiles {:sector sector :prefix prefix :y y :x x}))

(defn map-sector-night
  "Generate nighttime scale pyramid and map tiles for given sector"
  [& {:keys [sector prefix y x]}]
  (map-scales-night {:sector sector})
  (map-sector-night-tiles {:sector sector :prefix prefix :y y :x x}))

(defn map-sectors-day
  "Convert all day map sectors into a pyramid of map tiles"
  [& {:keys [prefix] :or {prefix "tmp/day"}}]
  (map-sector-day {:sector "A1" :prefix prefix :y 0 :x 0})
  (map-sector-day {:sector "A2" :prefix prefix :y 1 :x 0})
  (map-sector-day {:sector "B1" :prefix prefix :y 0 :x 1})
  (map-sector-day {:sector "B2" :prefix prefix :y 1 :x 1})
  (map-sector-day {:sector "C1" :prefix prefix :y 0 :x 2})
  (map-sector-day {:sector "C2" :prefix prefix :y 1 :x 2})
  (map-sector-day {:sector "D1" :prefix prefix :y 0 :x 3})
  (map-sector-day {:sector "D2" :prefix prefix :y 1 :x 3}))

(defn map-sectors-night
  "Convert all day map sectors into a pyramid of map tiles"
  [& {:keys [prefix] :or {prefix "tmp/night"}}]
  (map-sector-night {:sector "A1" :prefix prefix :y 0 :x 0})
  (map-sector-night {:sector "A2" :prefix prefix :y 1 :x 0})
  (map-sector-night {:sector "B1" :prefix prefix :y 0 :x 1})
  (map-sector-night {:sector "B2" :prefix prefix :y 1 :x 1})
  (map-sector-night {:sector "C1" :prefix prefix :y 0 :x 2})
  (map-sector-night {:sector "C2" :prefix prefix :y 1 :x 2})
  (map-sector-night {:sector "D1" :prefix prefix :y 0 :x 3})
  (map-sector-night {:sector "D2" :prefix prefix :y 1 :x 3}))

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
  (cube-map {:in-level 3 :out-level 5})
  (cube-map {:in-level 4 :out-level 6}))

(defn clean [_]
  "Clean secondary files"
  (b/delete {:path "data/clouds/worley-north.raw"})
  (b/delete {:path "data/clouds/worley-south.raw"})
  (b/delete {:path "data/clouds/worley-cover.raw"})
  (b/delete {:path "data/clouds/perlin.raw"})
  (b/delete {:path "data/clouds/cover0.raw"})
  (b/delete {:path "data/clouds/cover1.raw"})
  (b/delete {:path "data/clouds/cover2.raw"})
  (b/delete {:path "data/clouds/cover3.raw"})
  (b/delete {:path "data/clouds/cover4.raw"})
  (b/delete {:path "data/clouds/cover5.raw"})
  (b/delete {:path "data/bluenoise.raw"})
  (b/delete {:path "data/atmosphere/mie-strength.scatter"})
  (b/delete {:path "data/atmosphere/ray-scatter.scatter"})
  (b/delete {:path "data/atmosphere/surface-radiance.scatter"})
  (b/delete {:path "data/atmosphere/transmittance.scatter"})
  (b/delete {:path "world"})
  (b/delete {:path "elevation"})
  (b/delete {:path "globe"}))

(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "target/classes")

(defn uber [_]
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file "target/sfsim.jar"
           :basis basis
           :main 'sfsim.core}))

(defn all [_]
  (worley)
  (perlin)
  (bluenoise)
  (cloud-cover)
  (atmosphere-lut)
  (download-bluemarble)
  (download-blackmarble)
  (download-elevation)
  (extract-elevation)
  (map-sectors-day)
  (map-sectors-night)
  (elevation-sectors)
  (cube-maps))
