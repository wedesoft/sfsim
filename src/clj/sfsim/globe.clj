;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.globe
  "Use Mercator color and elevation map tiles to generate cube map tiles for color, water, elevation, and normals."
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [com.climate.claypoole :as cp]
    [fastmath.vector :refer (sub)]
    [sfsim.cubemap :refer (cartesian->geodetic color-geodetic-day color-geodetic-night cube-coordinate cube-map
                           normal-for-point project-onto-globe water-geodetic tile-center)]
    [sfsim.image :refer (set-byte! set-pixel! set-vector3! spit-jpg spit-normals make-image make-byte-image
                         make-vector-image)]
    [sfsim.util :refer (align-address cube-dir cube-path cube-tar make-progress-bar spit-bytes-gz spit-floats-gz tick-and-print
                        index->face create-tar)])
  (:import
    (java.io
      File)))


(set! *unchecked-math* :warn-on-boxed)


(defn make-cube-map
  "Program to generate tiles for cube map"
  [in-level out-level]
  (let [n                  (bit-shift-left 1 ^long out-level)
        width              675
        surface-tilesize   65
        sublevel           1
        max-surface-level  4
        max-color-level    5
        subsample          (bit-shift-left 1 sublevel)
        color-tilesize     (inc (* subsample (dec surface-tilesize)))
        radius             6378000.0
        bar                (agent (make-progress-bar (* 6 n n) 1))]
    (cp/pdoseq (+ ^long (cp/ncpus) 2) [k (range 6) b (range n) a (range n)]
               (let [face       (index->face k)
                     tile-day   (make-image color-tilesize color-tilesize)
                     tile-night (make-image color-tilesize color-tilesize)
                     water      (make-byte-image (align-address color-tilesize 4) color-tilesize)
                     surface    (make-vector-image surface-tilesize surface-tilesize)
                     normals    (make-vector-image color-tilesize color-tilesize)
                     center     (tile-center face out-level b a radius)]
                 (doseq [v (range surface-tilesize) u (range surface-tilesize)]
                   (let [j                 (cube-coordinate out-level surface-tilesize b (double v))
                         i                 (cube-coordinate out-level surface-tilesize a (double u))
                         p                 (cube-map face j i)
                         point             (project-onto-globe p (max 0 (min max-surface-level ^long in-level)) width radius)]
                     (set-vector3! surface v u (sub point center))))
                 (doseq [v (range color-tilesize) u (range color-tilesize)]
                   (let [j                 (cube-coordinate out-level color-tilesize b (double v))
                         i                 (cube-coordinate out-level color-tilesize a (double u))
                         p                 (cube-map face j i)
                         point             (project-onto-globe p (max 0 (min max-surface-level ^long in-level)) width radius)
                         [lon lat _height] (cartesian->geodetic point radius)
                         normal            (normal-for-point point (max 0 (min max-surface-level ^long in-level)) out-level width color-tilesize radius)
                         color-day         (color-geodetic-day (max 0 (min max-color-level (+ ^long in-level sublevel))) width lon lat)
                         color-night       (color-geodetic-night (max 0 (min max-color-level (+ ^long in-level sublevel))) width lon lat)
                         wet               (water-geodetic (max 0 (min max-surface-level (+ ^long in-level sublevel))) width lon lat)]
                     (set-vector3! normals v u normal)
                     (set-pixel! tile-day v u color-day)
                     (set-pixel! tile-night v u color-night)
                     (set-byte! water v u wet)))
                 (.mkdirs (File. (cube-dir "data/globe" face out-level a)))
                 (spit-jpg (cube-path "data/globe" face out-level b a ".jpg") tile-day)
                 (spit-jpg (cube-path "data/globe" face out-level b a ".night.jpg") tile-night)
                 (spit-bytes-gz (cube-path "data/globe" face out-level b a ".water.gz") (:sfsim.image/data water))
                 (spit-floats-gz (cube-path "data/globe" face out-level b a ".surf.gz") (:sfsim.image/data surface))
                 (spit-normals (cube-path "data/globe" face out-level b a ".png") normals)
                 (send bar tick-and-print)))))


(defn make-cube-map-tars
  "Program to put cube map tiles into tar files"
  [out-level]
  (let [n (bit-shift-left 1 ^long out-level)]
    (doseq [k (range 6) a (range n)]
           (let [face          (index->face k)
                 tar-file-name (cube-tar "data/globe" face out-level a)
                 directory     (io/file (cube-dir "data/globe" face out-level a))
                 files         (remove (memfn isDirectory) (file-seq directory))
                 file-paths    (map (memfn getPath) files)
                 file-names    (map (memfn getName) files)]
             (println "creating " tar-file-name)
             (create-tar tar-file-name (interleave file-names file-paths))
             (run! io/delete-file (reverse (file-seq directory)))))))


(set! *unchecked-math* false)
