(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map longitude latitude map-pixels-x map-pixels-y scale-point cube-coordinate
                                     offset-longitude offset-latitude)]
            [sfsim25.util :refer (tile-path slurp-image spit-image slurp-shorts get-pixel set-pixel! cube-dir cube-path)]
            [sfsim25.rgb :as r]
            [sfsim25.vector3 :as v])
  (:import [java.io File]
           [sfsim25.vector3 Vector3])
  (:gen-class))

(def world-map-tile
  "Load and cache map tiles"
  (m/lru
    (fn [^long in-level ^long ty ^long tx]
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 128))

(def elevation-tile
  "Load and cache elevation tiles"
  (m/lru
    (fn [^long in-level ^long ty ^long tx]
      (slurp-shorts (tile-path "elevation" in-level ty tx ".raw")))
    :lru/threshold 128))

(defn world-map-pixel
  "Get world map RGB value for a given pixel coordinate"
  [^long dy ^long dx ^long in-level ^long width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (world-map-tile in-level ty tx)]
    (get-pixel img py px)))

(defn elevation-pixel
  "Get elevation value for given pixel coordinates"
  [^long dy ^long dx ^long in-level ^long width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (elevation-tile in-level ty tx)]
    (max 0 (aget img (+ (* py width) px)))))

(defn color-for-point
  "Compute interpolated RGB value for a point on the world"
  [^long in-level ^long width ^Vector3 p]
  (let [lon                     (longitude p)
        lat                     (latitude p)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        c0                      (world-map-pixel dy0 dx0 in-level width)
        c1                      (world-map-pixel dy0 dx1 in-level width)
        c2                      (world-map-pixel dy1 dx0 in-level width)
        c3                      (world-map-pixel dy1 dx1 in-level width)]
    (r/+ (r/* (* yfrac0 xfrac0) c0) (r/* (* yfrac0 xfrac1) c1) (r/* (* yfrac1 xfrac0) c2) (r/* (* yfrac1 xfrac1) c3))))

(defn elevation-for-point
  "Compute interpolated elevation value for a point on the world"
  [^long in-level ^long width ^Vector3 p]
  (let [lon                     (longitude p)
        lat                     (latitude p)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        e0                      (elevation-pixel dy0 dx0 in-level width)
        e1                      (elevation-pixel dy0 dx1 in-level width)
        e2                      (elevation-pixel dy1 dx0 in-level width)
        e3                      (elevation-pixel dy1 dx1 in-level width)]
    (+ (* e0 yfrac0 xfrac0) (* e1 yfrac0 xfrac1) (* e2 yfrac1 xfrac0) (* e3 yfrac1 xfrac1))))

(defn elevated-point
  "Get elevated 3D point for a point on the world"
  [in-level width p radius1 radius2]
  (let [height (elevation-for-point in-level width p)]
    (scale-point p (+ radius1 height) (+ radius2 height))))

(defn surrounding-points
  "Compute local point cloud consisting of nine points"
  [p in-level out-level width tilesize radius1 radius2]
  (let [d1 (offset-longitude p out-level tilesize)
        d2 (offset-latitude p out-level tilesize radius1 radius2)]
    (for [dj (range -1 2) di (range -1 2)]
        (let [ps (v/+ p (v/* dj d2) (v/* di d1))]
          (elevated-point in-level width ps radius1 radius2)))))

(defn normal-for-point
  "Estimate normal vector for a point on the world"
  [p in-level out-level width tilesize radius1 radius2]
  (let [pc (surrounding-points p in-level out-level width tilesize radius1 radius2)
        sx [-0.25  0    0.25, -0.5 0 0.5, -0.25 0   0.25]
        sy [-0.25 -0.5 -0.25,  0   0 0  ,  0.25 0.5 0.25]
        n1 (apply v/+ (map v/* sx pc))
        n2 (apply v/+ (map v/* sy pc))]
    (v/normalize (v/cross-product n1 n2))))

(defn -main
  "Program to generate tiles for cube map"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-globe [input level] [output level]")
    (System/exit 1))
  (let [in-level  (Integer/parseInt (nth args 0))
        out-level (Integer/parseInt (nth args 1))
        n         (bit-shift-left 1 out-level)
        width     675
        tilesize  256
        radius1   6378000.0
        radius2   6357000.0]
    (doseq [k (range 6) b (range n) a (range n)]
      (let [data (byte-array (* 3 tilesize tilesize))
            tile [tilesize tilesize data]]
        (doseq [v (range tilesize)]
          (let [j (cube-coordinate out-level tilesize b v)]
            (doseq [u (range tilesize)]
              (let [i       (cube-coordinate out-level tilesize a u)
                    p       (v/normalize (cube-map k j i))
                    color   (color-for-point in-level width p)]
                (set-pixel! tile v u color)
                (println (elevated-point in-level width p radius1 radius2))
                (println (normal-for-point p in-level out-level width tilesize radius1 radius2))
                (println)))))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tilesize tilesize data)
        (println (cube-path "globe" k out-level b a ".png"))))))
