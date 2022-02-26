(ns sfsim25.atmosphere-lut
    "Compute lookup tables for atmospheric scattering"
    (:require [clojure.core.matrix :refer :all]
              [sfsim25.atmosphere :refer :all]
              [sfsim25.interpolate :refer :all]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer :all])
    (:import [mikera.vectorz Vector]))

(def radius 6378000.0)
(def height 35000.0)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0])
                            :radius radius
                            :sfsim25.atmosphere/height height
                            :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5])
                              :scatter-scale 1200
                              :scatter-g 0.76
                              :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6])
                                   :scatter-scale 8000})

(defn -main
  "Program to generate lookup tables for atmospheric scattering"
  [& args]
  (when-not (= (count args) 4)
            (.println *err* "Syntax: lein run-atmosphere-lut [size] [ray-steps] [sphere-steps] [iterations]")
            (.println *err* "Example: lein run-atmosphere-lut 35 100 15 5")
            (System/exit 1))
  (.println *err* "Initialization")
  (let [size                          (Integer/parseInt (nth args 0))
        ray-steps                     (Integer/parseInt (nth args 1))
        sphere-steps                  (Integer/parseInt (nth args 2))
        iterations                    (Integer/parseInt (nth args 3))
        power                         2.0
        scatter                       [mie rayleigh]
        transmittance-planet          (partial transmittance earth scatter ray-extremity ray-steps)
        transmittance-space-planet    (transmittance-space earth size power)
        surface-radiance-base-planet  (partial surface-radiance-base earth scatter ray-steps (matrix [1 1 1]))
        surface-radiance-space-planet (surface-radiance-space earth size power)
        point-scatter-base-planet     (partial point-scatter-base earth scatter ray-steps (matrix [1 1 1]))
        point-scatter-space           (point-scatter-space earth size power)
        ray-scatter-base-planet       (partial ray-scatter earth scatter ray-extremity ray-steps point-scatter-base-planet)
        ray-scatter-space-planet      (ray-scatter-space earth size power)
        T                             (interpolate-function transmittance-planet transmittance-space-planet)
        dE                            (atom (interpolate-function surface-radiance-base-planet surface-radiance-space-planet))
        E                             (atom (constantly (matrix [0 0 0])))
        dS                            (atom (interpolate-function ray-scatter-base-planet ray-scatter-space-planet))
        S                             (atom @dS)]
    (doseq [iteration (range iterations)]
           (.println *err* (str "Iteration " (inc iteration) "/" iterations))
           (let [point-scatter-planet    (partial point-scatter earth scatter @dS @dE (matrix [1 1 1]) sphere-steps ray-steps)
                 surface-radiance-planet (partial surface-radiance earth @dS ray-steps)
                 dJ                      (interpolate-function point-scatter-planet point-scatter-space)
                 ray-scatter-planet      (partial ray-scatter earth scatter ray-extremity ray-steps dJ)]
             (reset! dE (interpolate-function surface-radiance-planet surface-radiance-space-planet))
             (reset! dS (interpolate-function ray-scatter-planet ray-scatter-space-planet))
             (reset! E (let [E @E dE @dE] (interpolate-function (fn [x s] (add (E x s) (dE x s))) surface-radiance-space-planet)))
             (reset! S (let [S @S dS @dS] (interpolate-function (fn [x v s] (add (S x v s) (dS x v s))) ray-scatter-space-planet)))))
    (let [lookup-table-transmittance    (make-lookup-table T transmittance-space-planet)
          lookup-table-surface-radiance (make-lookup-table @E surface-radiance-space-planet)
          lookup-table-ray-scatter      (make-lookup-table @S ray-scatter-space-planet)]
      (spit-floats "data/atmosphere/transmittance.scatter"    (pack-matrices lookup-table-transmittance))
      (spit-floats "data/atmosphere/surface-radiance.scatter" (pack-matrices lookup-table-surface-radiance))
      (spit-floats "data/atmosphere/ray-scatter.scatter"      (pack-matrices (convert-4d-to-2d lookup-table-ray-scatter))))
    (System/exit 0)))
