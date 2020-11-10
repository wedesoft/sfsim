(ns sfsim25.globe
  (:import [java.io File])
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map longitude latitude map-pixels-x map-pixels-y scale-point cube-coordinate)]
            [sfsim25.util :refer (tile-path slurp-image spit-image slurp-shorts get-pixel set-pixel! cube-dir cube-path)]
            [sfsim25.rgb :as r])
  (:gen-class))

(def world-map-tile
  (m/lru
    (fn [in-level ty tx]
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 128))

(defn world-map-pixel [dy dx in-level width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (world-map-tile in-level ty tx)]
    (get-pixel img py px)))

(defn color-for-point [in-level width p]
  (let [lon                     (longitude p)
        lat                     (latitude p)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        c0                      (world-map-pixel dy0 dx0 in-level width)
        c1                      (world-map-pixel dy0 dx1 in-level width)
        c2                      (world-map-pixel dy1 dx0 in-level width)
        c3                      (world-map-pixel dy1 dx1 in-level width)]
    (r/+ (r/* (* yfrac0 xfrac0) c0) (r/* (* yfrac0 xfrac1) c1) (r/* (* yfrac1 xfrac0) c2) (r/* (* yfrac1 xfrac1) c3))))

(def elevation-tile
  (m/lru
    (fn [in-level ty tx]
      (slurp-shorts (tile-path "elevation" in-level ty tx ".raw")))
    :lru/threshold 16))

(defn elevation-pixel [dy dx in-level width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (elevation-tile in-level ty tx)]
    (max 0 (aget img (+ (* py width) px)))))

(defn elevation-for-point [in-level width p]
  (let [lon                     (longitude p)
        lat                     (latitude p)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        e0                      (elevation-pixel dy0 dx0 in-level width)
        e1                      (elevation-pixel dy0 dx1 in-level width)
        e2                      (elevation-pixel dy1 dx0 in-level width)
        e3                      (elevation-pixel dy1 dx1 in-level width)]
    (+ (* e0 yfrac0 xfrac0) (* e1 yfrac0 xfrac1) (* e2 yfrac1 xfrac0) (* e3 yfrac1 xfrac1))))

(defn elevated-point [in-level width p radius1 radius2]
  (let [height (elevation-for-point in-level width p)]
    (scale-point p (+ radius1 height) (+ radius2 height))))

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
      (let [data (byte-array (* 3 tilesize tilesize))]
        (doseq [v (range tilesize)]
          (let [j (cube-coordinate out-level tilesize b v)]
            (doseq [u (range tilesize)]
              (let [i       (cube-coordinate out-level tilesize a u)
                    p       (cube-map k j i)
                    offset  (* 3 (+ (* v tilesize) u))
                    color   (color-for-point in-level width p)]
                (set-pixel! [tilesize tilesize data] v u color)
                (println (elevated-point in-level width p radius1 radius2))))))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tilesize tilesize data)
        (println (cube-path "globe" k out-level b a ".png"))))))
