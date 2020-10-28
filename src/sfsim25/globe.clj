(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map-x cube-map-y cube-map-z longitude latitude map-pixels-x map-pixels-y)]
            [sfsim25.util :refer (tile-path slurp-image)])
  (:gen-class))

(defn cube-coordinate [level tilesize tile pixel]
  (let [tiles (bit-shift-left 1 level)]
    (+ tile (float (/ pixel (dec tilesize) tiles)))))

(def world-map-tile
  (m/lru
    (fn [in-level ty tx]
      (println (tile-path "world" in-level ty tx ".png"))
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 16))

(defn color-for-point [in-level width x y z]
  (let [lon (longitude x y z)
        lat (latitude x y z)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)]
    (world-map-tile in-level (quot dy0 width) (quot dx0 width))))

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
              (let [i (cube-coordinate out-level tilesize a u)
                    x (cube-map-x k j i)
                    y (cube-map-y k j i)
                    z (cube-map-z k j i)]
                (color-for-point in-level width x y z)))))))))
