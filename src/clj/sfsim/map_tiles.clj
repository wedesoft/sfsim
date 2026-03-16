;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.map-tiles
  "Split up part of Mercator map into map tiles of specified size and save them in computed file names."
  (:require
    [clojure.java.io :as io]
    [sfsim.util :refer (tile-dir tile-path non-empty-string N N0)])
  (:import
    (java.io
      File)
    (javax.imageio
      ImageIO)))


(defn make-map-tiles
  "Program to generate map tiles"
  {:malli/schema [:=> [:cat non-empty-string N N0 :string N0 N0] :nil]}
  [input-path tilesize level prefix y-offset x-offset]
  (let [dy          (bit-shift-left y-offset level)
        dx          (bit-shift-left x-offset level)
        img         (with-open [input-file (io/input-stream input-path)] (ImageIO/read input-file))
        [w h]       [(.getWidth img) (.getHeight img)]]
    (doseq [j (range (quot h tilesize)) i (range (quot w tilesize))]
      (let [dir  (tile-dir prefix level (+ i dx))
            path (tile-path prefix level (+ j dy) (+ i dx) ".png")
            tile (.getSubimage img (* i tilesize) (* j tilesize) tilesize tilesize)]
        (.mkdirs (File. dir))
        (with-open [output-file (io/output-stream path)]
          (ImageIO/write tile "png" output-file))))))
