(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map longitude latitude map-pixels-x map-pixels-y scale-point cube-coordinate
                                     offset-longitude offset-latitude)]
            [sfsim25.util :refer (tile-path slurp-image spit-image slurp-shorts spit-bytes get-pixel set-pixel! cube-dir cube-path
                                  ubyte->byte)]
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
    (aget img (+ (* py width) px))))

(defn interpolate
  "Interpolate elevation or RGB values"
  [in-level width point get-pixel p+ p*]
  (let [lon                     (longitude point)
        lat                     (latitude point)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        v0                      (get-pixel dy0 dx0 in-level width)
        v1                      (get-pixel dy0 dx1 in-level width)
        v2                      (get-pixel dy1 dx0 in-level width)
        v3                      (get-pixel dy1 dx1 in-level width)]
    (p+ (p* (* yfrac0 xfrac0) v0) (p* (* yfrac0 xfrac1) v1) (p* (* yfrac1 xfrac0) v2) (p* (* yfrac1 xfrac1) v3))))

(defn color-for-point
  "Compute interpolated RGB value for a point on the world"
  [^long in-level ^long width ^Vector3 point]
  (interpolate in-level width point world-map-pixel r/+ r/*))

(defn elevation-for-point
  "Compute interpolated elevation value for a point on the world"
  [^long in-level ^long width ^Vector3 point]
  (interpolate in-level width point elevation-pixel + *))

(defn elevated-point
  "Get elevated 3D point for a point on the world"
  [in-level width p radius1 radius2]
  (let [height (elevation-for-point in-level width p)]
    (scale-point p (+ radius1 (max 0 height)) (+ radius2 (max 0 height)))))

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

(defn water-for-point
  "Decide whether point is on land or on water"
  [^long in-level ^long width ^Vector3 point]
  (let [height (elevation-for-point in-level width point)]
    (if (< height 0) (int (/ (* height 255) -500)) 0)))

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
            water (byte-array (* tilesize tilesize))
            tile [tilesize tilesize data]]
        (doseq [v (range tilesize)]
          (let [j (cube-coordinate out-level tilesize b v)]
            (doseq [u (range tilesize)]
              (let [i       (cube-coordinate out-level tilesize a u)
                    p       (v/normalize (cube-map k j i))
                    color   (color-for-point in-level width p)]
                (set-pixel! tile v u color)
                (aset-byte water (+ (* tilesize v) u) (ubyte->byte (water-for-point in-level width p)))
                (do
                  (println (elevated-point in-level width p radius1 radius2))
                  (println (double (/ u (dec tilesize))) (double (/ v (dec tilesize))))
                  (println (normal-for-point p in-level out-level width tilesize radius1 radius2))
                  (println))))))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tilesize tilesize data)
        (spit-bytes (cube-path "globe" k out-level b a ".bin") water)
        (println (cube-path "globe" k out-level b a ".png"))))))
