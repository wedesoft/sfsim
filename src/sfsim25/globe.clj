(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map cube-coordinate color-for-point water-for-point elevated-point normal-for-point)]
            [sfsim25.util :refer (tile-path slurp-image spit-image slurp-shorts spit-bytes spit-floats set-pixel!
                                  cube-dir cube-path ubyte->byte)]
            [sfsim25.rgb :as r]
            [sfsim25.vector3 :as v])
  (:import [java.io File]
           [sfsim25.vector3 Vector3])
  (:gen-class))

(set! *unchecked-math* true)

(defn set-vertex-data!
  "Write 3D point, texture coordinates, and 3D normal to vertex data array"
  [vertex tilesize u v point u-scaled v-scaled normal]
  (let [offset (* 8 (+ (* tilesize v) u))]
    (aset-float vertex (+ offset 0) (.x ^Vector3 point))
    (aset-float vertex (+ offset 1) (.y ^Vector3 point))
    (aset-float vertex (+ offset 2) (.z ^Vector3 point))
    (aset-float vertex (+ offset 3) u-scaled)
    (aset-float vertex (+ offset 4) v-scaled)
    (aset-float vertex (+ offset 5) (.x ^Vector3 normal))
    (aset-float vertex (+ offset 6) (.y ^Vector3 normal))
    (aset-float vertex (+ offset 7) (.z ^Vector3 normal))))

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
        tilesize  33
        radius1   6378000.0
        radius2   6357000.0]
    (doseq [k (range 6) b (range n) a (range n)]
      (let [data   (byte-array (* 3 tilesize tilesize))
            water  (byte-array (* tilesize tilesize))
            vertex (float-array (* 8 tilesize tilesize))
            tile   [tilesize tilesize data]]
        (doseq [v (range tilesize)]
          (let [j (cube-coordinate out-level tilesize b v)]
            (doseq [u (range tilesize)]
              (let [i      (cube-coordinate out-level tilesize a u)
                    p      (v/normalize (cube-map k j i))
                    color  (color-for-point in-level width p)
                    point  (elevated-point in-level width p radius1 radius2)
                    normal (normal-for-point p in-level out-level width tilesize radius1 radius2)]
                (set-pixel! tile v u color)
                (aset-byte water (+ (* tilesize v) u) (ubyte->byte (water-for-point in-level width p)))
                (set-vertex-data! vertex tilesize u v point (float (/ u (dec tilesize))) (float (/ v (dec tilesize))) normal)))))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tilesize tilesize data)
        (spit-bytes (cube-path "globe" k out-level b a ".bin") water)
        (spit-floats (cube-path "globe" k out-level b a ".raw") vertex)
        (println (cube-path "globe" k out-level b a ".*"))))))

(set! *unchecked-math* false)
