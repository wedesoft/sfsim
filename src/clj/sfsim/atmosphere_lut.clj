;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.atmosphere-lut
  "Compute lookup tables for atmospheric scattering"
  (:require
    [fastmath.vector :refer (vec3 add mult dot)]
    [sfsim.atmosphere :refer (phase point-scatter point-scatter-component point-scatter-space ray-scatter
                                    ray-scatter-space strength-component surface-radiance surface-radiance-base
                                    surface-radiance-space transmittance transmittance-space)]
    [sfsim.image :refer (convert-4d-to-2d)]
    [sfsim.interpolate :refer (interpolate-function make-lookup-table)]
    [sfsim.matrix :refer (pack-matrices)]
    [sfsim.util :refer (progress-wrap size-of-shape spit-floats)]))


(def radius 6378000.0)
(def height 35000.0)


(def earth
  {:sfsim.sphere/centre (vec3 0 0 0)
   :sfsim.sphere/radius radius
   :sfsim.atmosphere/height height
   :sfsim.atmosphere/brightness (vec3 0.3 0.3 0.3)})


(def mie
  #:sfsim.atmosphere{:scatter-base (vec3 2e-5 2e-5 2e-5)
                     :scatter-scale 1200.0
                     :scatter-g 0.76
                     :scatter-quotient 0.9})


(def rayleigh
  #:sfsim.atmosphere{:scatter-base (vec3 5.8e-6 13.5e-6 33.1e-6)
                     :scatter-scale 8000.0})


(defn generate-atmosphere-luts
  "Program to generate lookup tables for atmospheric scattering"
  []
  (.println *err* (str "Initialization " (.toString (java.time.LocalDateTime/now))))
  (let [height-size                   32
        elevation-size                127
        light-elevation-size          32
        heading-size                  8
        transmittance-height-size     64
        transmittance-elevation-size  255
        surface-height-size           16
        surface-sun-elevation-size    63
        transmittance-shape           [transmittance-height-size transmittance-elevation-size]
        surface-radiance-shape        [surface-height-size surface-sun-elevation-size]
        ray-scatter-shape             [height-size elevation-size light-elevation-size heading-size]
        bar                           #(progress-wrap % (size-of-shape ray-scatter-shape) (* elevation-size light-elevation-size))
        ray-steps                     100
        sphere-steps                  15
        iterations                    5
        intensity                     (vec3 1 1 1)
        scatter                       [mie rayleigh]
        transmittance-planet          (partial transmittance earth scatter ray-steps)
        transmittance-space-planet    (transmittance-space earth transmittance-shape)
        surface-radiance-base-planet  (partial surface-radiance-base earth scatter ray-steps intensity)
        surface-radiance-space-planet (surface-radiance-space earth surface-radiance-shape)
        point-scatter-base-rayleigh   (partial point-scatter-component earth scatter rayleigh ray-steps intensity)
        point-scatter-strength-mie    (partial strength-component earth scatter mie ray-steps intensity)
        point-scatter-space-planet    (point-scatter-space earth ray-scatter-shape)
        ray-scatter-base-rayleigh     (bar (partial ray-scatter earth scatter ray-steps point-scatter-base-rayleigh))
        ray-scatter-strength-mie      (bar (partial ray-scatter earth scatter ray-steps point-scatter-strength-mie))
        ray-scatter-space-planet      (ray-scatter-space earth ray-scatter-shape)
        T                             (interpolate-function transmittance-planet transmittance-space-planet)
        dE                            (atom (interpolate-function surface-radiance-base-planet surface-radiance-space-planet))
        E                             (atom (constantly (vec3 0 0 0)))  ; First order incident light excluded from table.
        first-order-rayleigh          (interpolate-function ray-scatter-base-rayleigh ray-scatter-space-planet)
        first-order-mie-strength      (interpolate-function ray-scatter-strength-mie ray-scatter-space-planet)
        dS                            (atom
                                        (fn dS
                                          [x view-direction light-direction above-horizon]
                                          (add (first-order-rayleigh x view-direction light-direction above-horizon)
                                               (mult (first-order-mie-strength x view-direction light-direction above-horizon)
                                                     (phase mie (dot view-direction light-direction))))))
        S                             (atom first-order-rayleigh)]  ; First order Mie scatter strength goes into another table.
    (doseq [iteration (range iterations)]
      (.println *err* (str "Iteration " (inc iteration) "/" iterations " " (.toString (java.time.LocalDateTime/now))))
      (let [point-scatter-planet    (bar (partial point-scatter earth scatter @dS @dE intensity sphere-steps ray-steps))
            surface-radiance-planet (partial surface-radiance earth @dS ray-steps)
            dJ                      (interpolate-function point-scatter-planet point-scatter-space-planet)
            ray-scatter-planet      (bar (partial ray-scatter earth scatter ray-steps dJ))]
        (reset! dE (interpolate-function surface-radiance-planet surface-radiance-space-planet))
        (reset! dS (interpolate-function ray-scatter-planet ray-scatter-space-planet))
        (reset! E (let [E @E dE @dE] (interpolate-function (fn E+dE [x s] (add (E x s) (dE x s)))
                                                           surface-radiance-space-planet)))
        (reset! S (let [S @S dS @dS] (interpolate-function (fn S+dS [x v s a] (add (S x v s a) (dS x v s a)))
                                                           ray-scatter-space-planet)))))
    (let [lookup-table-transmittance    (make-lookup-table T transmittance-space-planet)
          lookup-table-surface-radiance (make-lookup-table @E surface-radiance-space-planet)
          lookup-table-ray-scatter      (make-lookup-table @S ray-scatter-space-planet)
          lookup-table-mie-strength     (make-lookup-table first-order-mie-strength ray-scatter-space-planet)]
      (spit-floats "data/atmosphere/transmittance.scatter"    (pack-matrices lookup-table-transmittance))
      (spit-floats "data/atmosphere/surface-radiance.scatter" (pack-matrices lookup-table-surface-radiance))
      (spit-floats "data/atmosphere/ray-scatter.scatter"      (pack-matrices (convert-4d-to-2d lookup-table-ray-scatter)))
      (spit-floats "data/atmosphere/mie-strength.scatter"     (pack-matrices (convert-4d-to-2d lookup-table-mie-strength))))))
