;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

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
              [sfsim.texture :as t]
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
  [& {:keys [size divisions] :or {size 16 divisions 4}}]
  (doseq [filename ["worley-north.raw" "worley-south.raw" "worley-cover.raw"]]
         (u/spit-floats (str "data/clouds/" filename) (float-array (w/worley-noise divisions size true)))))

(defn perlin
  "Generate 3D Perlin noise textures"
  [& {:keys [size divisions] :or {size 16 divisions 4}}]
  (u/spit-floats "data/clouds/perlin.raw" (float-array (p/perlin-noise divisions size true))))

(defn bluenoise
  "Generate 2D blue noise texture"
  [& {:keys [size] :or {size bn/noise-size}}]
  (let [n      (quot (* size size) 10)
        sigma  1.5
        dither (bn/blue-noise size n sigma)]
    (u/spit-floats "data/bluenoise.raw" (float-array (mapv #(/ % size size) dither)))))

(defn cloud-cover
  "Generate cloud cover cubemap"
  [& {:keys [worley-size] :or {worley-size w/worley-size}}]
  (GLFW/glfwInit)
  (rn/with-invisible-window
    (let [load-floats  (fn slurp-float-image [filename] #:sfsim.image{:width worley-size :height worley-size :depth worley-size
                                                                      :data (u/slurp-floats filename)})
          worley-north (t/make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat (load-floats "data/clouds/worley-north.raw"))
          worley-south (t/make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat (load-floats "data/clouds/worley-south.raw"))
          worley-cover (t/make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat (load-floats "data/clouds/worley-cover.raw"))
          cubemap      (cl/cloud-cover-cubemap :sfsim.clouds/size cl/cover-size
                                               :sfsim.clouds/worley-size worley-size
                                               :sfsim.clouds/worley-south worley-south
                                               :sfsim.clouds/worley-north worley-north
                                               :sfsim.clouds/worley-cover worley-cover
                                               :sfsim.clouds/flow-octaves [0.5 0.25 0.125]
                                               :sfsim.clouds/cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                                               :sfsim.clouds/whirl 1.0
                                               :sfsim.clouds/prevailing 0.0
                                               :sfsim.clouds/curl-scale 2.0
                                               :sfsim.clouds/cover-scale 1.0
                                               :sfsim.clouds/num-iterations 50
                                               :sfsim.clouds/flow-scale 6e-3)]
      (doseq [i (range 6)]
             (u/spit-floats (str "data/clouds/cover" i ".raw") (:sfsim.image/data (t/float-cubemap->floats cubemap i))))
      (t/destroy-texture cubemap)
      (t/destroy-texture worley-cover)
      (t/destroy-texture worley-south)
      (t/destroy-texture worley-north)
      (GLFW/glfwTerminate))))

(defn atmosphere-lut
  "Generate atmospheric lookup tables"
  [& _]
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
  [& _]
  (doseq [sector ["A1" "A2" "B1" "B2" "C1" "C2" "D1" "D2"]]
         (let [filename (str "world.200404.3x21600x21600." sector ".png")
               url      (str "https://eoimages.gsfc.nasa.gov/images/imagerecords/74000/74017/" filename)]
           (.println *err* (str "Downloading " url " ..."))
           (io/copy
             (io/input-stream url)
             (io/file (str "tmp/" filename))))))

(defn download-blackmarble
  "Download some NASA Blackmarble data from https://earthobservatory.nasa.gov/features/NightLights/page3.php"
  [& _]
  (doseq [sector ["A1" "A2" "B1" "B2" "C1" "C2" "D1" "D2"]]
         (let [filename (str "BlackMarble_2016_" sector ".jpg")
               url      (str "https://eoimages.gsfc.nasa.gov/images/imagerecords/144000/144898/" filename)]
           (.println *err* (str "Downloading " url " ..."))
           (io/copy
             (io/input-stream url)
             (io/file (str "tmp/" filename))))))

(defn download-elevation
  "Download NOAA elevation data from https://www.ngdc.noaa.gov/mgg/topo/gltiles.html"
  [& _]
  (let [filename "all10g.zip"
        url      (str "https://www.ngdc.noaa.gov/mgg/topo/DATATILES/elev/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "tmp/" filename)))))


(defn download-lunar-color
  "Download CGI Moon Kit color image. Map centered on 0 degree longitude"
  [& _]
  (let [filename "lroc_color_poles.tif"
        url (str "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "tmp/" filename)))))


(defn download-lunar-elevation
  "Download CGI Moon Kit elevation floating-point TIFFs in kilometer, relative to a radius of 1737400 meters"
  [& _]
  (let [filename "ldem_64.tif"
        url (str "https://svs.gsfc.nasa.gov/vis/a000000/a004700/a004720/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "tmp/" filename)))))


(defn extract-elevation
  "Extract and concatenate elevation files"
  [& _]
  (b/unzip {:zip-file "tmp/all10g.zip" :target-dir "tmp"})
  (with-open [out (io/output-stream "tmp/elevation.A1.raw")]
    (io/copy (io/file "tmp/all10/a10g") out) (io/copy (io/file "tmp/all10/e10g") out))
  (with-open [out (io/output-stream "tmp/elevation.B1.raw")]
    (io/copy (io/file "tmp/all10/b10g") out) (io/copy (io/file "tmp/all10/f10g") out))
  (with-open [out (io/output-stream "tmp/elevation.C1.raw")]
    (io/copy (io/file "tmp/all10/c10g") out) (io/copy (io/file "tmp/all10/g10g") out))
  (with-open [out (io/output-stream "tmp/elevation.D1.raw")]
    (io/copy (io/file "tmp/all10/d10g") out) (io/copy (io/file "tmp/all10/h10g") out))
  (with-open [out (io/output-stream "tmp/elevation.A2.raw")]
    (io/copy (io/file "tmp/all10/i10g") out) (io/copy (io/file "tmp/all10/m10g") out))
  (with-open [out (io/output-stream "tmp/elevation.B2.raw")]
    (io/copy (io/file "tmp/all10/j10g") out) (io/copy (io/file "tmp/all10/n10g") out))
  (with-open [out (io/output-stream "tmp/elevation.C2.raw")]
    (io/copy (io/file "tmp/all10/k10g") out) (io/copy (io/file "tmp/all10/o10g") out))
  (with-open [out (io/output-stream "tmp/elevation.D2.raw")]
    (io/copy (io/file "tmp/all10/l10g") out) (io/copy (io/file "tmp/all10/p10g") out)))

(defn map-tiles
  "Generate map tiles from specified image"
  [& {:keys [image tilesize level prefix y-offset x-offset] :or {tilesize 675}}]
  (mt/make-map-tiles image tilesize level prefix y-offset x-offset))

(defn map-sector-day-tiles
  "Generate pyramid of daytime map tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (map-tiles {:image (str "tmp/world.200404.3x21600x21600." sector ".png") :level 5 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/world." sector "." 2 ".png") :level 4 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/world." sector "." 3 ".png") :level 3 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/world." sector "." 4 ".png") :level 2 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/world." sector "." 5 ".png") :level 1 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/world." sector "." 6 ".png") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn map-sector-night-tiles
  "Generate pyramid of nighttime map tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (map-tiles {:image (str "tmp/BlackMarble_2016_" sector ".jpg") :level 5 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/blackmarble." sector "." 2 ".png") :level 4 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/blackmarble." sector "." 3 ".png") :level 3 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/blackmarble." sector "." 4 ".png") :level 2 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/blackmarble." sector "." 5 ".png") :level 1 :prefix prefix :y-offset y :x-offset x})
  (map-tiles {:image (str "tmp/blackmarble." sector "." 6 ".png") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn map-scales-day
  "Generate pyramid of daytime scales for given sector of world map"
  [& {:keys [sector]}]
  (sh "convert" (str "tmp/world.200404.3x21600x21600." sector ".png") "-scale" "50%" (str "tmp/world." sector "." 2 ".png"))
  (scale-image-file {:input (str "tmp/world." sector "." 2 ".png") :output (str "tmp/world." sector "." 3 ".png")})
  (scale-image-file {:input (str "tmp/world." sector "." 3 ".png") :output (str "tmp/world." sector "." 4 ".png")})
  (scale-image-file {:input (str "tmp/world." sector "." 4 ".png") :output (str "tmp/world." sector "." 5 ".png")})
  (scale-image-file {:input (str "tmp/world." sector "." 5 ".png") :output (str "tmp/world." sector "." 6 ".png")}))

(defn map-scales-night
  "Generate pyramid of nighttime scales for given sector of world map"
  [& {:keys [sector]}]
  (sh "convert" (str "tmp/BlackMarble_2016_" sector ".jpg") "-scale" "50%" (str "tmp/blackmarble." sector "." 2 ".png"))
  (scale-image-file {:input (str "tmp/blackmarble." sector "." 2 ".png") :output (str "tmp/blackmarble." sector "." 3 ".png")})
  (scale-image-file {:input (str "tmp/blackmarble." sector "." 3 ".png") :output (str "tmp/blackmarble." sector "." 4 ".png")})
  (scale-image-file {:input (str "tmp/blackmarble." sector "." 4 ".png") :output (str "tmp/blackmarble." sector "." 5 ".png")})
  (scale-image-file {:input (str "tmp/blackmarble." sector "." 5 ".png") :output (str "tmp/blackmarble." sector "." 6 ".png")}))

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
  "Generate pyramid of scales for given "
  [& {:keys [sector]}]
  (scale-elevation {:input (str "tmp/elevation." sector ".raw") :output (str "tmp/elevation." sector "." 2 ".raw")})
  (scale-elevation {:input (str "tmp/elevation." sector "." 2 ".raw") :output (str "tmp/elevation." sector "." 3 ".raw")})
  (scale-elevation {:input (str "tmp/elevation." sector "." 3 ".raw") :output (str "tmp/elevation." sector "." 4 ".raw")})
  (scale-elevation {:input (str "tmp/elevation." sector "." 4 ".raw") :output (str "tmp/elevation." sector "." 5 ".raw")}))

(defn elevation-tiles
  "Generate map tiles from specified image"
  [& {:keys [image tilesize level prefix y-offset x-offset] :or {tilesize 675}}]
  (et/make-elevation-tiles image tilesize level prefix y-offset x-offset))

(defn elevation-sector-tiles
  "Generate pyramid of elevation tiles for given sector of world map"
  [& {:keys [sector prefix y x]}]
  (elevation-tiles {:image (str "tmp/elevation." sector ".raw") :level 4 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "tmp/elevation." sector "." 2 ".raw") :level 3 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "tmp/elevation." sector "." 3 ".raw") :level 2 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "tmp/elevation." sector "." 4 ".raw") :level 1 :prefix prefix :y-offset y :x-offset x})
  (elevation-tiles {:image (str "tmp/elevation." sector "." 5 ".raw") :level 0 :prefix prefix :y-offset y :x-offset x}))

(defn elevation-sector
  "Generate scale pyramid and elevation tiles for given sector"
  [& {:keys [sector prefix y x]}]
  (elevation-scales {:sector sector})
  (elevation-sector-tiles {:sector sector :prefix prefix :y y :x x}))

(defn elevation-sectors
  "Convert all elevation sectors into a pyramid of elevation tiles"
  [& {:keys [prefix] :or {prefix "tmp/elevation"}}]
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
  (g/make-cube-map in-level out-level)
  (g/make-cube-map-tars out-level))

(defn cube-maps
  "Create pyramid of cube maps"
  [& _]
  (cube-map {:in-level -3 :out-level 0})
  (cube-map {:in-level -2 :out-level 1})
  (cube-map {:in-level -1 :out-level 2})
  (cube-map {:in-level  0 :out-level 3})
  (cube-map {:in-level  1 :out-level 4})
  (cube-map {:in-level  2 :out-level 5})
  (cube-map {:in-level  3 :out-level 6})
  (cube-map {:in-level  4 :out-level 7}))

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
  (b/copy-dir {:src-dirs ["src/clj"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src/clj"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file "target/sfsim.jar"
           :basis basis
           :main 'sfsim.core}))

(defn download-spaceship
  "Download Spaceship model"
  [& _]
  (let [filename "venturestar.glb"
        url      (str "https://www.wedesoft.de/downloads/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file filename))))

(defn download-audio
  "Download Spaceship model"
  [& _]
  (doseq [filename ["gear-deploy.ogg" "gear-retract.ogg"]]
         (let [url      (str "https://www.wedesoft.de/downloads/" filename)]
           (.println *err* (str "Downloading " url " ..."))
           (io/copy
             (io/input-stream url)
             (io/file (str "data/audio/" filename))))))

(defn download-ephemeris
  "Download NASA JPL planet ephemeris data from https://ssd.jpl.nasa.gov/ftp/eph/planets/bsp/"
  [& _]
  (let [filename "de430_1850-2150.bsp"
        url      (str "https://ssd.jpl.nasa.gov/ftp/eph/planets/bsp/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "data/astro/" filename)))))

(defn download-reference-frames
  "Download NASA JPL reference frames specification kernel from https://naif.jpl.nasa.gov/pub/naif/pds/wgc/kernels/fk/"
  [& _]
  (let [filename "moon_080317.tf"
        url      (str "https://naif.jpl.nasa.gov/pub/naif/pds/wgc/kernels/fk/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "data/astro/" filename)))))

(defn download-lunar-pck-file
  "Download NASA JPL moon PCK file from https://naif.jpl.nasa.gov/pub/naif/generic_kernels/pck/"
  [& _]
  (let [filename "moon_pa_de421_1900-2050.bpc"
        url      (str "https://naif.jpl.nasa.gov/pub/naif/generic_kernels/pck/" filename)]
    (.println *err* (str "Downloading " url " ..."))
    (io/copy
      (io/input-stream url)
      (io/file (str "data/astro/" filename)))))

(defn quit
  "Fast quit"
  [& _]
  (System/exit 0))

(defn all [_]
  (worley)
  (perlin)
  (bluenoise)
  (cloud-cover)
  (download-spaceship)
  (download-audio)
  (download-bluemarble)
  (download-blackmarble)
  (download-elevation)
  (download-ephemeris)
  (download-reference-frames)
  (download-lunar-pck-file)
  (download-lunar-color)
  (download-lunar-elevation)
  (extract-elevation)
  (map-sectors-day)
  (map-sectors-night)
  (elevation-sectors)
  (cube-maps)
  (atmosphere-lut)
  (quit))
