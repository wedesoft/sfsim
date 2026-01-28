;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-matrix
  (:require
    [clojure.math :refer (sqrt PI to-radians)]
    [fastmath.matrix :as fm]
    [fastmath.vector :as fv]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-matrix roughly-vector roughly-quaternion)]
    [sfsim.matrix :refer :all]
    [sfsim.quaternion :refer (->Quaternion rotation)]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(def ca (/ (sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)


(facts "Comvert rotation quaternion to rotation matrix"
       (quaternion->matrix (->Quaternion 1 0 0 0))              => (roughly-matrix (fm/eye 3) 1e-6)
       (quaternion->matrix (rotation (/ PI 6) (fv/vec3 1 0 0))) => (roughly-matrix (fm/rotation-matrix-3d-x (/ PI 6)) 1e-6)
       (quaternion->matrix (rotation (/ PI 6) (fv/vec3 0 1 0))) => (roughly-matrix (fm/rotation-matrix-3d-y (/ PI 6)) 1e-6)
       (quaternion->matrix (rotation (/ PI 6) (fv/vec3 0 0 1))) => (roughly-matrix (fm/rotation-matrix-3d-z (/ PI 6)) 1e-6))


(tabular "Convert rotation matrix to quaternion"
         (fact (matrix->quaternion (quaternion->matrix ?q)) => (roughly-quaternion ?q 1e-6))
         ?q
         (->Quaternion 1 0 0 0)
         (rotation (/ PI 6) (fv/vec3 1 0 0))
         (rotation (/ PI 6) (fv/vec3 0 1 0))
         (rotation (/ PI 6) (fv/vec3 0 0 1)))


(fact "Project homogeneous coordinate to cartesian"
      (project (fv/vec4 4 6 10 2)) => (fv/vec3 2 3 5))


(fact "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
      (transformation-matrix (fm/mat3x3 1 2 3, 5 6 7, 9 10 11) (fv/vec3 4 8 12))
      => (fm/mat4x4 1 2 3 4, 5 6 7 8, 9 10 11 12, 0 0 0 1))


(facts "Create a 4x4 matrix from a translation vector"
       (translation-matrix (fv/vec3 2 3 5))
       => (fm/mat4x4 1 0 0 2, 0 1 0 3, 0 0 1 5, 0 0 0 1))


(fact "Create a 4x4 matrix from a 3x3 rotation matrix"
      (rotation-matrix (fm/mat3x3 1 2 3, 5 6 7, 9 10 11))
      => (fm/mat4x4 1 2 3 0, 5 6 7 0, 9 10 11 0, 0 0 0 1))


(fact "Extract 3x3 roation part from 4x4 matrix"
      (get-rotation (fm/mat4x4 1 2 3 4, 5 6 7 8, 9 10 11 12, 0 0 0 1))
      => (fm/mat3x3 1 2 3, 5 6 7, 9 10 11))


(fact "Extract translation vector from 4x4 matrix"
      (get-translation (fm/mat4x4 1 0 0 2, 0 1 0 3, 0 0 1 5, 0 0 0 1))
      => (fv/vec3 2 3 5))


(facts "OpenGL projection matrix"
       (let [m (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))]
         (project (fm/mulv m (fv/vec4    0    0    -5 1))) => (roughly-vector (fv/vec3 0 0 1) 1e-6)
         (project (fm/mulv m (fv/vec4    0    0 -1000 1))) => (roughly-vector (fv/vec3 0 0 0) 1e-6)
         (project (fm/mulv m (fv/vec4 1000    0 -1000 1))) => (roughly-vector (fv/vec3 1 0 0) 1e-6)
         (project (fm/mulv m (fv/vec4    5    0    -5 1))) => (roughly-vector (fv/vec3 1 0 1) 1e-6)
         (project (fm/mulv m (fv/vec4    0 3.75    -5 1))) => (roughly-vector (fv/vec3 0 1 1) 1e-6)))


(fact "Pack nested vector of matrices into float array"
      (seq (pack-matrices [[(fv/vec3 1 2 3)] [(fv/vec3 4 5 6)]])) => [1.0 2.0 3.0 4.0 5.0 6.0])


(facts "Convert z-coordinate to normalized device coordinate"
       (z-to-ndc 10.0 40.0 40.0) => (roughly 0.0 1e-6)
       (z-to-ndc 10.0 40.0 10.0) => (roughly 1.0 1e-6)
       (z-to-ndc 10.0 40.0 20.0) => (roughly (/ 1 3) 1e-6))


(facts "Corners of OpenGL frustum"
       (let [m       (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             corners (frustum-corners m)]
         (project (fm/mulv m (nth corners 0))) => (roughly-vector (fv/vec3 -1 -1 1) 1e-6)
         (project (fm/mulv m (nth corners 1))) => (roughly-vector (fv/vec3  1 -1 1) 1e-6)
         (project (fm/mulv m (nth corners 2))) => (roughly-vector (fv/vec3 -1  1 1) 1e-6)
         (project (fm/mulv m (nth corners 3))) => (roughly-vector (fv/vec3  1  1 1) 1e-6)
         (project (fm/mulv m (nth corners 4))) => (roughly-vector (fv/vec3 -1 -1 0) 1e-6)
         (project (fm/mulv m (nth corners 5))) => (roughly-vector (fv/vec3  1 -1 0) 1e-6)
         (project (fm/mulv m (nth corners 6))) => (roughly-vector (fv/vec3 -1  1 0) 1e-6)
         (project (fm/mulv m (nth corners 7))) => (roughly-vector (fv/vec3  1  1 0) 1e-6)))


(facts "Corners of part of frustum"
       (let [m       (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             corners (frustum-corners m 0.6 0.4)]
         (project (fm/mulv m (nth corners 0))) => (roughly-vector (fv/vec3 -1 -1 0.6) 1e-6)
         (project (fm/mulv m (nth corners 1))) => (roughly-vector (fv/vec3  1 -1 0.6) 1e-6)
         (project (fm/mulv m (nth corners 2))) => (roughly-vector (fv/vec3 -1  1 0.6) 1e-6)
         (project (fm/mulv m (nth corners 3))) => (roughly-vector (fv/vec3  1  1 0.6) 1e-6)
         (project (fm/mulv m (nth corners 4))) => (roughly-vector (fv/vec3 -1 -1 0.4) 1e-6)
         (project (fm/mulv m (nth corners 5))) => (roughly-vector (fv/vec3  1 -1 0.4) 1e-6)
         (project (fm/mulv m (nth corners 6))) => (roughly-vector (fv/vec3 -1  1 0.4) 1e-6)
         (project (fm/mulv m (nth corners 7))) => (roughly-vector (fv/vec3  1  1 0.4) 1e-6)))


(facts "3D bounding box for set of points"
       (:bottomleftnear (bounding-box [(fv/vec4 2 3 -5 1)])) => (fv/vec3 2 3 -5)
       (:toprightfar (bounding-box [(fv/vec4 2 3 -5 1)])) => (fv/vec3 2 3 -5)
       (:bottomleftnear (bounding-box [(fv/vec4 2 3 -5 1) (fv/vec4 1 2 -3 1)])) => (fv/vec3 1 2 -3)
       (:toprightfar (bounding-box [(fv/vec4 2 3 -5 1) (fv/vec4 5 7 -11 1)])) => (fv/vec3 5 7 -11)
       (:bottomleftnear (bounding-box [(fv/vec4 4 6 -10 2)])) => (fv/vec3 2 3 -5))


(facts "Expand near portion of bounding box"
       (let [bbox {:bottomleftnear (fv/vec3 2 3 -5) :toprightfar (fv/vec3 7 11 -13)}]
         (expand-bounding-box-near bbox 0.0) => bbox
         (expand-bounding-box-near bbox 1.0) => {:bottomleftnear (fv/vec3 2 3 -4) :toprightfar (fv/vec3 7 11 -13)}))


(facts "Scale and translate light box coordinates to normalized device coordinates"
       (let [m (shadow-box-to-ndc {:bottomleftnear (fv/vec3 2 3 -5) :toprightfar (fv/vec3 7 11 -13)})]
         (project (fm/mulv m (fv/vec4 2  3  -5 1))) => (roughly-vector (fv/vec3 -1 -1 1) 1e-6)
         (project (fm/mulv m (fv/vec4 7  3  -5 1))) => (roughly-vector (fv/vec3  1 -1 1) 1e-6)
         (project (fm/mulv m (fv/vec4 2 11  -5 1))) => (roughly-vector (fv/vec3 -1  1 1) 1e-6)
         (project (fm/mulv m (fv/vec4 2  3 -13 1))) => (roughly-vector (fv/vec3 -1 -1 0) 1e-6)))


(facts "Scale and translate light box coordinates to shadow map coordinates"
       (let [m (shadow-box-to-map {:bottomleftnear (fv/vec3 2 3 -5) :toprightfar (fv/vec3 7 11 -13)})]
         (project (fm/mulv m (fv/vec4 2  3  -5 1))) => (roughly-vector (fv/vec3  0  0 1) 1e-6)
         (project (fm/mulv m (fv/vec4 7  3  -5 1))) => (roughly-vector (fv/vec3  1  0 1) 1e-6)
         (project (fm/mulv m (fv/vec4 2 11  -5 1))) => (roughly-vector (fv/vec3  0  1 1) 1e-6)
         (project (fm/mulv m (fv/vec4 2  3 -13 1))) => (roughly-vector (fv/vec3  0  0 0) 1e-6)))


(facts "Generate orthogonal vector"
       (fv/dot (orthogonal (fv/vec3 1 0 0)) (fv/vec3 1 0 0)) => 0.0
       (fv/mag (orthogonal (fv/vec3 1 0 0))) => 1.0
       (fv/mag (orthogonal (fv/vec3 2 0 0))) => 1.0
       (fv/dot (orthogonal (fv/vec3 0 1 0)) (fv/vec3 0 1 0)) => 0.0
       (fv/mag (orthogonal (fv/vec3 0 1 0))) => 1.0
       (fv/mag (orthogonal (fv/vec3 0 2 0))) => 1.0
       (fv/dot (orthogonal (fv/vec3 0 0 1)) (fv/vec3 0 0 1)) => 0.0
       (fv/mag (orthogonal (fv/vec3 0 0 1))) => 1.0
       (fv/mag (orthogonal (fv/vec3 0 0 2))) => 1.0)


(facts "Generate isometry with given normal vector as first row"
       (let [n (fv/vec3 0.36 0.48 0.8)
             m (oriented-matrix n)]
         (fm/mulv m n) => (roughly-vector (fv/vec3 1 0 0) 1e-6)
         (fm/mulm m (fm/transpose m)) => (roughly-matrix (fm/eye 3) 1e-6)
         (fm/det m) => (roughly 1.0 1e-6)))


(facts "Rotate points into coordinate system with z-axis pointing towards the light"
       (let [m (orient-to-light (fv/vec3 0.64 0.48 0.6))]
         (fm/mulv m (fv/vec4 0 0 0 1)) => (roughly-vector (fv/vec4 0 0 0 1) 1e-6)
         (fm/mulv m (fv/vec4 0.64 0.48 0.6 1)) => (roughly-vector (fv/vec4 0 0 1 1) 1e-6)
         (fm/mulm m (fm/transpose m)) => (roughly-matrix (fm/eye 4) 1e-6)
         (fm/det m) => (roughly 1.0 1e-6)))


(facts "Choose NDC and texture coordinate matrices for shadow mapping"
       (let [projection         (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             camera-to-world-1 (fm/eye 4)
             camera-to-world-2 (transformation-matrix (fm/rotation-matrix-3d-x (/ PI 2)) (fv/vec3 0 0 0))
             light-direction   (fv/vec3 0 1 0)]
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-1 light-direction 0.0))
                  (fv/vec4 0 750 -1000 1)) => (roughly-vector (fv/vec4 1 0 1 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-1 light-direction 0.0))
                  (fv/vec4 0 -750 -1000 1)) => (roughly-vector (fv/vec4 1 0 0 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-2 light-direction 0.0))
                  (fv/vec4 0 1000 0 1)) => (roughly-vector (fv/vec4 0 0 1 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-2 light-direction 0.0))
                  (fv/vec4 0 5 0 1)) => (roughly-vector (fv/vec4 0 0 0 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-map (shadow-matrices projection camera-to-world-1 light-direction 0.0))
                  (fv/vec4 0 -750 -1000 1)) => (roughly-vector (fv/vec4 1 0.5 0 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-map (shadow-matrices projection camera-to-world-1 light-direction 0.0))
                  (fv/vec4 0 750 -1000 1)) => (roughly-vector (fv/vec4 1 0.5 1 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-map (shadow-matrices projection camera-to-world-2 light-direction 0.0))
                  (fv/vec4 0 1000 0 1)) => (roughly-vector (fv/vec4 0.5 0.5 1 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-map (shadow-matrices projection camera-to-world-2 light-direction 500.0))
                  (fv/vec4 0 1500 0 1)) => (roughly-vector (fv/vec4 0.5 0.5 1 1) 1e-6)
         (:sfsim.matrix/depth (shadow-matrices projection camera-to-world-1 light-direction 0.0))
         => (roughly 1500 1e-6)
         (:sfsim.matrix/depth (shadow-matrices projection camera-to-world-1 light-direction 1000.0))
         => (roughly 2500 1e-6)
         (:sfsim.matrix/scale (shadow-matrices projection camera-to-world-1 light-direction 0.0))
         => (roughly 1497.5 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-1 light-direction 0.0 (/ 99.0 199)
                                                                      (/ 9.0 199))) (fv/vec4 0 0 -10 1))
         => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (shadow-matrices projection camera-to-world-1 light-direction 0.0 (/ 99.0 199)
                                                                      (/ 9.0 199))) (fv/vec4 0 0 -100 1))
         => (roughly-vector (fv/vec4 1 0 0.5 1) 1e-6)))


(facts "Linear frustum split"
       (split-linear 10.0 40.0 2 0) => 10.0
       (split-linear 10.0 40.0 2 2) => 40.0
       (split-linear 10.0 40.0 2 1) => 25.0)


(facts "Exponential frustum split"
       (split-exponential 10.0 40.0 2 0) => 10.0
       (split-exponential 10.0 40.0 2 2) => 40.0
       (split-exponential 10.0 40.0 2 1) => 20.0)


(facts "Mixed linear and exponential split"
       (split-mixed 0.0 10.0 40.0 2 1) => 25.0
       (split-mixed 1.0 10.0 40.0 2 1) => 20.0
       (split-list {:sfsim.opacity/mix 0.0 :sfsim.opacity/num-steps 2}
                   {:sfsim.render/z-near 10.0 :sfsim.render/z-far 40.0})
       => [10.0 25.0 40.0])


(facts "Create list of increasing biases"
       (biases-like 100.0 [10.0 50.0]) => [100.0]
       (biases-like 100.0 [10.0 50.0 100.0]) => [100.0 200.0]
       (biases-like 100.0 [10.0 50.0 100.0 200.0]) => [100.0 200.0 400.0])


(facts "Cascade of shadow matrices"
       (let [z-near          10.0
             z-far           40.0
             projection      (projection-matrix 640 480 z-near z-far (* 0.5 PI))
             camera-to-world      (fm/eye 4)
             light-direction (fv/vec3 0 1 0)
             render-vars     #:sfsim.render{:projection projection :camera-to-world camera-to-world :light-direction light-direction
                                            :z-near z-near :z-far z-far}
             shadow-data     (fn [depth mix num-steps] #:sfsim.opacity{:depth depth :mix mix :num-steps num-steps})]
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (nth (shadow-matrix-cascade (shadow-data 0.0 0.0 2) render-vars) 0))
                  (fv/vec4 0 0 -10 1)) => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (nth (shadow-matrix-cascade (shadow-data 0.0 0.0 2) render-vars) 0))
                  (fv/vec4 0 0 -25 1)) => (roughly-vector (fv/vec4 1 0 0.5 1) 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (nth (shadow-matrix-cascade (shadow-data 0.0 1.0 2) render-vars) 0))
                  (fv/vec4 0 0 -20 1)) => (roughly-vector (fv/vec4 1 0 0.5 1) 1e-6)
         (:sfsim.matrix/depth (nth (shadow-matrix-cascade (shadow-data 0.0 0.0 2) render-vars) 0))
         => (roughly 37.5 1e-6)
         (:sfsim.matrix/depth (nth (shadow-matrix-cascade (shadow-data 10.0 0.0 2) render-vars) 0))
         => (roughly 47.5 1e-6)
         (fm/mulv (:sfsim.matrix/world-to-shadow-ndc (nth (shadow-matrix-cascade (shadow-data 0.0 0.0 2) render-vars) 1))
                  (fv/vec4 0 0 -25 1)) => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)))


(facts "Shadow matrices for an object mapping object coordinates to shadow coordinates"
       (let [unrotated (shadow-patch-matrices (fm/eye 4) (fv/vec3 0 0 1) 1.0)
             rotation  (transformation-matrix (fm/rotation-matrix-3d-x (/ PI 2)) (fv/vec3 0 0 0))
             rotated   (shadow-patch-matrices rotation (fv/vec3 0 0 1) 1.0)]
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 0 0 1 0))
         => (fv/vec4 0 0 0.5 0)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc rotated) (fv/vec4 0 0 1 0))
         => (roughly-vector (fv/vec4 -1.0 0 0 0) 1e-6)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc (shadow-patch-matrices (fm/eye 4) (fv/vec3 1 0 0) 1.0)) (fv/vec4 1 0 0 0))
         => (fv/vec4 0 0 0.5 0)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 1 0 0 1)) => (fv/vec4 0 -1 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 -1 0 0 1)) => (fv/vec4 0 1 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 0 1 0 1)) => (fv/vec4 1 0 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 0 -1 0 1)) => (fv/vec4 -1 0 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 0 0 1 1)) => (fv/vec4 0 0 1 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-ndc unrotated) (fv/vec4 0 0 -1 1)) => (fv/vec4 0 0 0 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 1 0 0 1)) => (fv/vec4 0.5 0.0 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 -1 0 0 1)) => (fv/vec4 0.5 1 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 0 1 0 1)) => (fv/vec4 1 0.5 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 0 -1 0 1)) => (fv/vec4 0 0.5 0.5 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 0 0 1 1)) => (fv/vec4 0.5 0.5 1 1)
         (fm/mulv (:sfsim.matrix/object-to-shadow-map unrotated) (fv/vec4 0 0 -1 1)) => (fv/vec4 0.5 0.5 0 1)
         (:sfsim.matrix/depth unrotated) => 2.0
         (:sfsim.matrix/scale unrotated) => 2.0
         (:sfsim.matrix/world-to-object rotated) => (fm/inverse rotation)))
