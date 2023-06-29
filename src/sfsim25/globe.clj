(ns sfsim25.globe
  "Use Mercator color and elevation map tiles to generate cube map tiles for color, water, elevation, and normals."
  (:require [com.climate.claypoole :as cp]
            [fastmath.vector :refer (mag)]
            [sfsim25.cubemap :refer (cartesian->geodetic color-geodetic cube-coordinate cube-map normal-for-point
                                     project-onto-globe water-geodetic)]
            [sfsim25.util :refer (align-address cube-dir cube-path make-progress-bar set-byte! set-float! set-pixel!
                                  set-vector3! spit-bytes spit-floats spit-png sqr tick-and-print)])
  (:import [java.io File])
  (:gen-class))

(set! *unchecked-math* true)

(defn make-cube-map
  "Program to generate tiles for cube map"
  [in-level out-level]
  (let [n                  (bit-shift-left 1 out-level)
        width              675
        elevation-tilesize 33
        sublevel           2
        subsample          (bit-shift-left 1 sublevel)
        color-tilesize     (inc (* subsample (dec elevation-tilesize)))
        radius             6378000.0
        bar                (agent (make-progress-bar (* 6 n n) 1))]
    (cp/pdoseq (+ (cp/ncpus) 2) [k (range 6) b (range n) a (range n)]
      (let [tile    {:width color-tilesize :height color-tilesize :data (byte-array (* 4 (sqr color-tilesize)))}
            water   {:width (align-address color-tilesize 4) :height color-tilesize :data (byte-array (* color-tilesize (+ color-tilesize 3)))}
            scale   {:width elevation-tilesize :height elevation-tilesize :data (float-array (sqr elevation-tilesize))}
            normals {:width color-tilesize :height color-tilesize :data (float-array (* 3 (sqr color-tilesize)))}]
        (doseq [v (range elevation-tilesize) u (range elevation-tilesize)]
          (let [j                 (cube-coordinate out-level elevation-tilesize b v)
                i                 (cube-coordinate out-level elevation-tilesize a u)
                p                 (cube-map k j i)
                point             (project-onto-globe p (min 4 in-level) width radius)]
            (set-float! scale v u (/ (mag point) (mag p)))))
        (doseq [v (range color-tilesize) u (range color-tilesize)]
          (let [j                 (cube-coordinate out-level color-tilesize b v)
                i                 (cube-coordinate out-level color-tilesize a u)
                p                 (cube-map k j i)
                point             (project-onto-globe p (min 4 in-level) width radius)
                [lon lat _height] (cartesian->geodetic point radius)
                normal            (normal-for-point point (min 4 in-level) out-level width color-tilesize radius)
                color             (color-geodetic (min 5 (+ in-level sublevel)) width lon lat)
                wet               (water-geodetic (min 4 (+ in-level sublevel)) width lon lat)]
            (set-vector3! normals v u normal)
            (set-pixel! tile v u color)
            (set-byte! water v u wet)))
        (.mkdirs (File. (cube-dir "data/globe" k out-level a)))
        (spit-png (cube-path "data/globe" k out-level b a ".png") tile)
        (spit-bytes (cube-path "data/globe" k out-level b a ".water") (:data water))
        (spit-floats (cube-path "data/globe" k out-level b a ".scale") (:data scale))
        (spit-floats (cube-path "data/globe" k out-level b a ".normals") (:data normals))
        (send bar tick-and-print)))))

(set! *unchecked-math* false)
