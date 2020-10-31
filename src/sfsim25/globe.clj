(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map-x cube-map-y cube-map-z longitude latitude map-pixels-x map-pixels-y)]
            [sfsim25.util :refer (tile-path slurp-image spit-image get-pixel set-pixel!)])
  (:gen-class))

(defn cube-coordinate [level tilesize tile pixel]
  (let [tiles (bit-shift-left 1 level)]
    (+ tile (float (/ pixel (dec tilesize) tiles)))))

(def world-map-tile
  (m/lru
    (fn [in-level ty tx]
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 16))

(defn world-map-pixel [dy dx in-level width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (world-map-tile in-level ty tx)]
    (get-pixel img py px)))

(defn color-for-point [in-level width x y z]
  (let [lon                     (longitude x y z)
        lat                     (latitude x y z)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        [c0r c0g c0b]           (world-map-pixel dy0 dx0 in-level width)
        [c1r c1g c1b]           (world-map-pixel dy0 dx1 in-level width)
        [c2r c2g c2b]           (world-map-pixel dy1 dx0 in-level width)
        [c3r c3g c3b]           (world-map-pixel dy1 dx1 in-level width)]
    [(int (+ (* c0r yfrac0 xfrac0) (* c1r yfrac0 xfrac1) (* c2r yfrac1 xfrac0) (* c3r yfrac1 xfrac1)))
     (int (+ (* c0g yfrac0 xfrac0) (* c1g yfrac0 xfrac1) (* c2g yfrac1 xfrac0) (* c3g yfrac1 xfrac1)))
     (int (+ (* c0b yfrac0 xfrac0) (* c1b yfrac0 xfrac1) (* c2b yfrac1 xfrac0) (* c3b yfrac1 xfrac1)))]))

(defn -main
  "Program to generate tiles for cube map"
  [& args]
  (when-not (= (count args) 1)
    (.println *err* "Syntax: lein run-globe [output level]")
    (System/exit 1))
  (let [out-level (Integer/parseInt (nth args 0))
        in-level   out-level
        n          (bit-shift-left 1 out-level)
        width      675
        tilesize   256]
    (doseq [k (range 6) b (range n) a (range n)]
      (let [data (byte-array (* 3 tilesize tilesize))]
        (doseq [v (range tilesize)]
          (let [j (cube-coordinate out-level tilesize b v)]
            (doseq [u (range tilesize)]
              (let [i       (cube-coordinate out-level tilesize a u)
                    x       (cube-map-x k j i)
                    y       (cube-map-y k j i)
                    z       (cube-map-z k j i)
                    offset  (* 3 (+ (* v tilesize) u))
                    color   (color-for-point in-level width x y z)]
                (set-pixel! [tilesize tilesize data] v u color)))))
        (spit-image (str "globe" k ".png") tilesize tilesize data)))))
