(ns sfsim25.atmosphere-lut
    "Compute lookup tables for atmospheric scattering"
    (:require [clojure.core.matrix :refer :all]
              [sfsim25.atmosphere :refer :all]
              [sfsim25.interpolate :refer :all])
    (:import [mikera.vectorz Vector]))

(def radius 6378000.0)
(def height 100000.0)
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
  (when-not (= (count args) 2)
            (.println *err* "Syntax: lein run-atmosphere-lut [size] [samples]")
            (System/exit 1))
  (let [size             (Integer/parseInt (nth args 0))
        samples          (Integer/parseInt (nth args 1))
        scatter          [mie rayleigh]
        transmit-earth   (fn [^Vector x ^Vector v] (transmittance earth scatter samples #:sfsim25.ray{:origin x :direction v}))
        transmit-space   (transmittance-space earth size)
        surf-base-earth  (fn [^Vector x ^Vector s] (surface-radiance-base earth scatter samples (matrix [1 1 1]) x s))
        surf-space       (surface-radiance-space earth size)
        point-base-earth (partial point-scatter-base earth scatter samples (matrix [1 1 1]))
        point-space      (point-scatter-space earth size)
        ray-base-earth   (partial ray-scatter earth scatter samples point-base-earth)
        ray-space        (ray-scatter-space earth size)
        T                (interpolate-function transmit-earth transmit-space)
        dE               (atom (interpolate-function surf-base-earth surf-space))
        E                (atom (fn [^Vector x ^Vector s] (matrix [0 0 0])))
        dS               (atom (interpolate-function ray-base-earth ray-space))
        S                (atom @dS)
        dJ               (atom nil)]
    0))
