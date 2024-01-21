(ns sfsim25.matrix
  "Matrix and vector operations"
  (:require [clojure.math :refer (cos sin tan pow)]
            [malli.core :as m]
            [fastmath.matrix :as fm]
            [fastmath.vector :as fv]
            [sfsim25.quaternion :refer (quaternion rotate-vector)]
            [sfsim25.util :refer (N N0)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def fmat3 (m/schema [:and ifn? [:fn (fn [m] (= (fm/ncol m) (fm/nrow m) 3))]]))
(def fmat4 (m/schema [:and ifn? [:fn (fn [m] (= (fm/ncol m) (fm/nrow m) 4))]]))
(def fvec3 (m/schema [:tuple :double :double :double]))
(def fvec4 (m/schema [:tuple :double :double :double :double]))

(defn rotation-x
  "Rotation matrix around x-axis"
  {:malli/schema [:=> [:cat :double] fmat3]}
  [angle]
  (let [ca (cos angle) sa (sin angle)]
    (fm/mat3x3 1 0 0, 0 ca (- sa), 0 sa ca)))

(defn rotation-y
  "Rotation matrix around y-axis"
  {:malli/schema [:=> [:cat :double] fmat3]}
  [angle]
  (let [ca (cos angle) sa (sin angle)]
    (fm/mat3x3 ca 0 sa, 0 1 0, (- sa) 0 ca)))

(defn rotation-z
  "Rotation matrix around z-axis"
  {:malli/schema [:=> [:cat :double] fmat3]}
  [angle]
  (let [ca (cos angle) sa (sin angle)]
    (fm/mat3x3 ca (- sa) 0, sa ca 0, 0 0 1)))

(defn quaternion->matrix
  "Convert rotation quaternion to rotation matrix"
  {:malli/schema [:=> [:cat quaternion] fmat3]}
  [q]
  (let [a (rotate-vector q (fv/vec3 1 0 0))
        b (rotate-vector q (fv/vec3 0 1 0))
        c (rotate-vector q (fv/vec3 0 0 1))]
    (fm/mat3x3 (a 0) (b 0) (c 0)
               (a 1) (b 1) (c 1)
               (a 2) (b 2) (c 2))))

(defn project
  "Project homogeneous coordinate to cartesian"
  {:malli/schema [:=> [:cat fvec4] fvec3]}
  [v]
  (fv/div (fv/vec3 (v 0) (v 1) (v 2)) (v 3)))

(defn transformation-matrix
  "Create homogeneous 4x4 transformation matrix from 3x3 rotation matrix and translation vector"
  {:malli/schema [:=> [:cat fmat3 fvec3] fmat4]}
  [m v]
  (fm/mat4x4 (m 0 0) (m 0 1) (m 0 2) (v 0)
             (m 1 0) (m 1 1) (m 1 2) (v 1)
             (m 2 0) (m 2 1) (m 2 2) (v 2)
                   0       0       0     1))

(defn projection-matrix
  "Compute OpenGL projection matrix (frustum)"
  {:malli/schema [:=> [:cat :int :int :double :double :double] fmat4]}
  [width height near far field-of-view]
  (let [dx (/ 1.0 (tan (* 0.5 field-of-view)))
        dy (-> dx (* width) (/ height))
        a  (/ (* far near) (- far near))
        b  (/ near (- far near))]
    (fm/mat4x4 dx  0  0  0
                0 dy  0  0
                0  0  b  a
                0  0 -1  0)))

(defn pack-matrices
  "Pack nested vector of matrices into float array"
  {:malli/schema [:=> [:cat [:vector :some]] seqable?]}
  [array]
  (float-array (flatten array)))

(defn z-to-ndc
  "Convert (flipped to positive) z-coordinate to normalized device coordinate"
  {:malli/schema [:=> [:cat :double :double :double] :double]}
  [near far z]
  (let [a (/ (* far near) (- far near))
        b (/ near (- far near))]
    (/ (- a (* z b)) z)))

(defn frustum-corners
  "Determine corners of OpenGL frustum (or part of frustum)"
  {:malli/schema [:=> [:cat fmat4 [:? [:cat :double :double]]] [:vector fvec4]]}
  ([projection-matrix]
   (frustum-corners projection-matrix 1.0 0.0))
  ([projection-matrix ndc1 ndc2]
   (let [minv (fm/inverse projection-matrix)]
     (mapv #(fm/mulv minv %)
           [(fv/vec4 -1.0 -1.0 ndc1 1.0)
            (fv/vec4  1.0 -1.0 ndc1 1.0)
            (fv/vec4 -1.0  1.0 ndc1 1.0)
            (fv/vec4  1.0  1.0 ndc1 1.0)
            (fv/vec4 -1.0 -1.0 ndc2 1.0)
            (fv/vec4  1.0 -1.0 ndc2 1.0)
            (fv/vec4 -1.0  1.0 ndc2 1.0)
            (fv/vec4  1.0  1.0 ndc2 1.0)]))))

(defn bounding-box
  "Compute 3D bounding box for a set of points"
  {:malli/schema [:=> [:cat [:sequential fvec4]] [:map [:bottomleftnear fvec3] [:toprightfar fvec3]]]}
  [points]
  (let [x (map #(/ (% 0) (% 3)) points)
        y (map #(/ (% 1) (% 3)) points)
        z (map #(/ (% 2) (% 3)) points)]
    {:bottomleftnear (fv/vec3 (apply min x) (apply min y) (apply max z))
     :toprightfar (fv/vec3 (apply max x) (apply max y) (apply min z))}))

(defn expand-bounding-box-near
  "Enlarge bounding box towards positive z (near)"
  {:malli/schema [:=> [:cat [:map [:bottomleftnear fvec3] [:toprightfar fvec3]] :double]
                      [:map [:bottomleftnear fvec3] [:toprightfar fvec3]]]}
  [bbox z-expand]
  (update bbox :bottomleftnear fv/add (fv/vec3 0 0 z-expand)))

(defn shadow-box-to-ndc
  "Scale and translate light box coordinates to normalized device coordinates"
  {:malli/schema [:=> [:cat [:map [:bottomleftnear fvec3] [:toprightfar fvec3]]] fmat4]}
  [{:keys [bottomleftnear toprightfar]}]
  (let [left   (bottomleftnear 0)
        right  (toprightfar 0)
        bottom (bottomleftnear 1)
        top    (toprightfar 1)
        near   (- (bottomleftnear 2))
        far    (- (toprightfar 2))]
    (fm/mat4x4 (/ 2 (- right left))                    0                  0   (- (/ (* 2 left) (- left right)) 1)
                                  0 (/ 2 (- top bottom))                  0 (- (/ (* 2 bottom) (- bottom top)) 1)
                                  0                    0 (/ 1 (- far near))                  (/ far (- far near))
                                  0                    0                  0                                     1)))

(defn shadow-box-to-map
  "Scale and translate light box coordinates to shadow map texture coordinates"
  [bounding-box]
  (fm/mulm (fm/mat4x4 0.5 0 0 0.5, 0 0.5 0 0.5, 0 0 1 0, 0 0 0 1) (shadow-box-to-ndc bounding-box)))

(defn orthogonal
  "Create orthogonal vector to specified 3D vector"
  [n]
  (let [b (first (sort-by #(abs (fv/dot n %)) (fm/rows (fm/eye 3))))]
    (fv/normalize (fv/cross n b))))

(defn oriented-matrix
  "Create a 3x3 isometry with given normal vector as first row"
  [n]
  (let [o1 (orthogonal n)
        o2 (fv/cross n o1)]
    (fm/mat3x3 (n 0) (n 1) (n 2) (o1 0) (o1 1) (o1 2) (o2 0) (o2 1) (o2 2))))

(defn orient-to-light
  "Return matrix to rotate points into coordinate system with z-axis pointing towards the light"
  [light-vector]
  (let [o (oriented-matrix light-vector)]
    (fm/mat4x4 (o 1 0) (o 1 1) (o 1 2) 0
               (o 2 0) (o 2 1) (o 2 2) 0
               (o 0 0) (o 0 1) (o 0 2) 0
                     0       0       0 1)))

(defn- transform-point-list
  "Apply transform to frustum corners"
  [matrix corners]
  (map #(fm/mulv matrix %) corners))

(defn- span-of-box
  "Get vector of box dimensions"
  [bounding-box]
  (fv/sub (:toprightfar bounding-box) (:bottomleftnear bounding-box)))

(defn- bounding-box-for-rotated-frustum
  "Determine bounding box for rotated frustum"
  {:malli/schema [:=> [:cat fmat4 fmat4 fmat4 :double :double :double]]}
  [extrinsics light-matrix projection-matrix longest-shadow ndc1 ndc2]
  (let [corners         (frustum-corners projection-matrix ndc1 ndc2)
        rotated-corners (transform-point-list extrinsics corners)
        light-corners   (transform-point-list light-matrix rotated-corners)]
    (expand-bounding-box-near (bounding-box light-corners) longest-shadow)))

(def shadow-box (m/schema [:map [:shadow-ndc-matrix fmat4] [:shadow-map-matrix fmat4] [:scale :double] [:depth :double]]))

(defn shadow-matrices
  "Choose NDC and texture coordinate matrices for shadow mapping"
  {:malli/schema [:=> [:cat fmat4 fmat4 fvec3 :double [:? [:cat :double :double]]] shadow-box]}
  ([projection-matrix extrinsics light-vector longest-shadow]
   (shadow-matrices projection-matrix extrinsics light-vector longest-shadow 1.0 0.0))
  ([projection-matrix extrinsics light-vector longest-shadow ndc1 ndc2]
   (let [light-matrix (orient-to-light light-vector)
         bounding-box (bounding-box-for-rotated-frustum extrinsics light-matrix projection-matrix longest-shadow ndc1 ndc2)
         shadow-ndc   (shadow-box-to-ndc bounding-box)
         shadow-map   (shadow-box-to-map bounding-box)
         span         (span-of-box bounding-box)
         scale        (* 0.5 (+ (span 0) (span 1)))
         depth        (- (span 2))]
     {:shadow-ndc-matrix (fm/mulm shadow-ndc light-matrix)
      :shadow-map-matrix (fm/mulm shadow-map light-matrix)
      :scale scale
      :depth depth})))

(defn split-linear
  "Perform linear z-split for frustum"
  {:malli/schema [:=> [:cat :double :double N N0] :double]}
  [z-near z-far num-steps step]
  (+ z-near (/ (* (- z-far z-near) step) num-steps)))

(defn split-exponential
  "Perform exponential z-split for frustum"
  {:malli/schema [:=> [:cat :double :double N N0] :double]}
  [z-near z-far num-steps step]
  (* z-near (pow (/ z-far z-near) (/ step num-steps))))

(defn split-mixed
  "Mixed linear and exponential split"
  {:malli/schema [:=> [:cat :double :double :double N N0] :double]}
  [mix z-near z-far num-steps step]
  (+ (* (- 1 mix) (split-linear z-near z-far num-steps step)) (* mix (split-exponential z-near z-far num-steps step))))

(defn split-list
  "Get list of splits including z-far"
  {:malli/schema [:=> [:cat :map :map] [:vector :double]]}
  [{:sfsim25.opacity/keys [mix num-steps]} {:sfsim25.render/keys [z-near z-far]}]
  (mapv (fn [step] (split-mixed mix z-near z-far num-steps step)) (range (inc num-steps))))

(defn shadow-matrix-cascade
  "Compute cascade of shadow matrices"
  {:malli/schema [:=> [:cat :map :map] [:vector shadow-box]]}
  [{:sfsim25.opacity/keys [num-steps mix depth]} {:sfsim25.render/keys [projection extrinsics light-direction z-near z-far]}]
  (mapv (fn [idx]
            (let [ndc1 (z-to-ndc z-near z-far (split-mixed mix z-near z-far num-steps idx))
                  ndc2 (z-to-ndc z-near z-far (split-mixed mix z-near z-far num-steps (inc idx)))]
              (shadow-matrices projection extrinsics light-direction depth ndc1 ndc2)))
        (range num-steps)))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
