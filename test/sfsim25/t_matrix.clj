(ns sfsim25.t-matrix
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim25.conftest :refer (roughly-matrix roughly-vector)]
            [fastmath.matrix :as fm]
            [fastmath.vector :as fv]
            [clojure.math :refer (sqrt PI)]
            [sfsim25.matrix :refer :all]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(mi/collect! {:ns ['sfsim25.matrix]})
(mi/instrument! {:report (pretty/thrower)})

(def ca (/ (sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(facts "Rotation matrices"
  (rotation-x (/ PI 6)) => (roughly-matrix (fm/mat3x3 1 0 0, 0 ca -sa, 0 sa ca) 1e-6)
  (rotation-y (/ PI 6)) => (roughly-matrix (fm/mat3x3 ca 0 sa, 0 1 0, -sa 0 ca) 1e-6)
  (rotation-z (/ PI 6)) => (roughly-matrix (fm/mat3x3 ca -sa 0, sa ca 0, 0 0 1) 1e-6))

(facts "Comvert rotation quaternion to rotation matrix"
  (quaternion->matrix (->Quaternion 1 0 0 0))              => (roughly-matrix (fm/eye 3) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (fv/vec3 1 0 0))) => (roughly-matrix (rotation-x (/ PI 6)) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (fv/vec3 0 1 0))) => (roughly-matrix (rotation-y (/ PI 6)) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (fv/vec3 0 0 1))) => (roughly-matrix (rotation-z (/ PI 6)) 1e-6))

(fact "Project homogeneous coordinate to cartesian"
  (project (fv/vec4 4 6 10 2)) => (fv/vec3 2 3 5))

(fact "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
  (transformation-matrix (fm/mat3x3 1 2 3, 5 6 7, 9 10 11) (fv/vec3 4 8 12))
  => (fm/mat4x4 1 2 3 4, 5 6 7 8, 9 10 11 12, 0 0 0 1))

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
       (let [projection      (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             extrinsics1     (fm/eye 4)
             extrinsics2     (transformation-matrix (rotation-x (/ PI 2)) (fv/vec3 0 0 0))
             light-direction (fv/vec3 0 1 0)]
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics1 light-direction 0.0)) (fv/vec4 0 750 -1000 1))
         => (roughly-vector (fv/vec4 1 0 1 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics1 light-direction 0.0)) (fv/vec4 0 -750 -1000 1))
         => (roughly-vector (fv/vec4 1 0 0 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics2 light-direction 0.0)) (fv/vec4 0 1000 0 1))
         => (roughly-vector (fv/vec4 0 0 1 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics2 light-direction 0.0)) (fv/vec4 0 5 0 1))
         => (roughly-vector (fv/vec4 0 0 0 1) 1e-6)
         (fm/mulv (:shadow-map-matrix (shadow-matrices projection extrinsics1 light-direction 0.0)) (fv/vec4 0 -750 -1000 1))
         => (roughly-vector (fv/vec4 1 0.5 0 1) 1e-6)
         (fm/mulv (:shadow-map-matrix (shadow-matrices projection extrinsics1 light-direction 0.0)) (fv/vec4 0 750 -1000 1))
         => (roughly-vector (fv/vec4 1 0.5 1 1) 1e-6)
         (fm/mulv (:shadow-map-matrix (shadow-matrices projection extrinsics2 light-direction 0.0)) (fv/vec4 0 1000 0 1))
         => (roughly-vector (fv/vec4 0.5 0.5 1 1) 1e-6)
         (fm/mulv (:shadow-map-matrix (shadow-matrices projection extrinsics2 light-direction 500.0)) (fv/vec4 0 1500 0 1))
         => (roughly-vector (fv/vec4 0.5 0.5 1 1) 1e-6)
         (:depth (shadow-matrices projection extrinsics1 light-direction 0.0))
         => (roughly 1500 1e-6)
         (:depth (shadow-matrices projection extrinsics1 light-direction 1000.0))
         => (roughly 2500 1e-6)
         (:scale (shadow-matrices projection extrinsics1 light-direction 0.0))
         => (roughly 1497.5 1e-6)
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics1 light-direction 0.0 (/ 99.0 199) (/ 9.0 199)))
                  (fv/vec4 0 0 -10 1))
         => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (shadow-matrices projection extrinsics1 light-direction 0.0 (/ 99.0 199) (/ 9.0 199)))
                  (fv/vec4 0 0 -100 1))
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
       (split-list 0.0 10.0 40.0 2) => [10.0 25.0 40.0])

(facts "Cascade of shadow matrices"
       (let [near            10.0
             far             40.0
             projection      (projection-matrix 640 480 near far (* 0.5 PI))
             extrinsics      (fm/eye 4)
             light-direction (fv/vec3 0 1 0)]
         (fm/mulv (:shadow-ndc-matrix (nth (shadow-matrix-cascade projection extrinsics light-direction 0.0 0.0 near far 2) 0))
                  (fv/vec4 0 0 -10 1))
         => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (nth (shadow-matrix-cascade projection extrinsics light-direction 0.0 0.0 near far 2) 0))
                  (fv/vec4 0 0 -25 1))
         => (roughly-vector (fv/vec4 1 0 0.5 1) 1e-6)
         (fm/mulv (:shadow-ndc-matrix (nth (shadow-matrix-cascade projection extrinsics light-direction 0.0 1.0 near far 2) 0))
                  (fv/vec4 0 0 -20 1))
         => (roughly-vector (fv/vec4 1 0 0.5 1) 1e-6)
         (:depth (nth (shadow-matrix-cascade projection extrinsics light-direction 0.0 0.0 near far 2) 0))
         => (roughly 37.5 1e-6)
         (:depth (nth (shadow-matrix-cascade projection extrinsics light-direction 10.0 0.0 near far 2) 0))
         => (roughly 47.5 1e-6)
         (fm/mulv (:shadow-ndc-matrix (nth (shadow-matrix-cascade projection extrinsics light-direction 0.0 0.0 near far 2) 1))
                  (fv/vec4 0 0 -25 1))
         => (roughly-vector (fv/vec4 -1 0 0.5 1) 1e-6)))
