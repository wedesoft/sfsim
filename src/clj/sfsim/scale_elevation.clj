;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.scale-elevation
  "Convert large elevation image into lower resolution image with half the width and height."
  (:require
    [clojure.math :refer (sqrt round)]
    [sfsim.util :refer (slurp-shorts spit-shorts)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn scale-elevation
  "Program to scale elevation images"
  [input-data output-data]
  (let [data          (slurp-shorts input-data)
        n             (alength data)
        w             (long (round (sqrt n)))
        size          (quot w 2)
        result        (short-array (* size size))]
    (doseq [^int j (range size) ^int i (range size)]
      (let [offset (+ (* j 2 w) (* i 2))
            x1 (aget ^shorts data offset)
            x2 (aget ^shorts data (inc offset))
            x3 (aget ^shorts data (+ offset w))
            x4 (aget ^shorts data (+ offset w 1))]
        (aset ^shorts result (+ (* j size) i) (short (/ (+ x1 x2 x3 x4) 4)))))
    (spit-shorts output-data result)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
