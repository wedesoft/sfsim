(ns sfsim25.t-atmosphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix mget)]
            [sfsim25.atmosphere :refer :all :as atmosphere])
  (:import [mikera.vectorz Vector]))

(def radius1 6378000.0)
(def radius2 6357000.0)

(facts "Compute approximate air density at different heights"
  (air-density 0 1.225 8429) => 1.225
  (air-density 8429 1.225 8429) => (roughly (/ 1.225 Math/E) 1e-6)
  (air-density (* 2 8429) 1.225 8429) => (roughly (/ 1.225 Math/E Math/E) 1e-6))

(facts "Create an air density table"
  (nth (air-density-table 1.0 21 10000 1000) 0) => 1.0
  (nth (air-density-table 1.0 21 10000 1000) 2) => (roughly (/ 1.0 Math/E) 1e-6)
  (nth (air-density-table 1.0 21 10000 1000) 4) => (roughly (/ 1.0 Math/E Math/E) 1e-6))

(facts "Air density for a given 3D point assuming spherical body"
  (air-density-at-point (matrix [1000 0 0]) 1.0 1000 10) => 1.0
  (air-density-at-point (matrix [1010 0 0]) 1.0 1000 10) => (roughly (/ 1.0 Math/E) 1e-6))

(facts "Compute intersection of line with sphere"
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [0 1 0])) => {:distance 0.0 :length 0.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [ 0 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 1.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [ 2 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 0.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [2 0 0])) => {:distance 0.5 :length 1.0})

(facts "Compute intersection of line with ellipsoid"
  (ray-ellipsoid (matrix [0 0 0]) 1 0.5 (matrix [-2 0  0]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
  (ray-ellipsoid (matrix [0 0 0]) 1 0.5 (matrix [ 0 0 -2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0}
  (ray-ellipsoid (matrix [0 0 4]) 1 0.5 (matrix [ 0 0  2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0})

(facts "Compute optical depth of atmosphere at different points and for different directions"
  (with-redefs [atmosphere/ray-sphere
                (fn [^Vector centre ^double radius ^Vector origin ^Vector direction]
                  (fact [centre radius origin direction] => [(matrix [0 0 0]) 1100.0 (matrix [0 1010 0]) (matrix [1 0 0])])
                  {:length 20.0})
                atmosphere/air-density-at-point
                (fn ^double [^Vector point ^double base ^double radius ^double scale]
                  (fact [base radius scale] => [1.0 1000.0 42.0])
                  (fact (.contains [(matrix [5.0 1010.0 0.0]) (matrix [15.0 1010.0 0.0])] point) => true)
                  0.25)]
    (optical-depth (matrix [0 1010 0]) (matrix [1 0 0]) 1.0 1000 100 42 2))  => (+ (* 0.25 10) (* 0.25 10)))

(facts "Lookup table for optical density"
  (let [result (optical-depth-table 5 9 1.0 1000 100 8 20)]
    (:width result) => 5
    (:height result) => 9
    (count (:data result)) => (* 5 9)
    (nth (:data result) (* 5 8)) => (roughly (optical-depth (matrix [0 1000 0]) (matrix [0 1 0]) 1.0 1000 100 8 20) 1e-4)
    (nth (:data result) (* 5 4)) => (roughly (optical-depth (matrix [0 1000 0]) (matrix [1 0 0]) 1.0 1000 100 8 20) 1e-4)
    (nth (:data result) (+ (* 5 4) 2)) => (roughly (optical-depth (matrix [0 1050 0]) (matrix [1 0 0]) 1.0 1000 100 8 20) 1e-4)))

(facts "Compute approximate air density at different heights"
  (mget (scattering          0 (matrix [5.8e-6]) 8000) 0) => 5.8e-6
  (mget (scattering       8000 (matrix [5.8e-6]) 8000) 0) => (roughly (/ 5.8e-6 Math/E) 1e-12)
  (mget (scattering (* 2 8000) (matrix [5.8e-6]) 8000) 0) => (roughly (/ 5.8e-6 Math/E Math/E) 1e-12))

(facts "Rayleigh phase function"
  (phase-rayleigh  0) => (roughly (/ 3 (* 16 Math/PI)))
  (phase-rayleigh  1) => (roughly (/ 6 (* 16 Math/PI)))
  (phase-rayleigh -1) => (roughly (/ 6 (* 16 Math/PI))))
