;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.matrix
  "Matrix and vector operations"
  (:require
    [clojure.math :refer (cos sin tan pow atan2)]
    [fastmath.matrix :as fm]
    [fastmath.vector :as fv]
    [malli.core :as m]
    [sfsim.quaternion :refer (quaternion rotate-vector rotation orthogonal)]
    [sfsim.util :refer (N N0)])
  (:import
    [fastmath.vector
     Vec3]
    [fastmath.matrix
     Mat4x4]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def fmat3 (m/schema [:and ifn? [:fn (fn check-3x3-matrix [m] (= (fm/ncol m) (fm/nrow m) 3))]]))
(def fmat4 (m/schema [:and ifn? [:fn (fn check-4x4-matrix [m] (= (fm/ncol m) (fm/nrow m) 4))]]))
(def fvec2 (m/schema [:tuple :double :double]))
(def fvec3 (m/schema [:tuple :double :double :double]))
(def fvec4 (m/schema [:tuple :double :double :double :double]))


(defn vec3->vec4
  "Create 4D vector from 3D vector and a scalar"
  {:malli/schema [:=> [:cat fvec3 :double] fvec4]}
  [v l]
  (fv/vec4 (v 0) (v 1) (v 2) l))


(defn vec4->vec3
  "Drop last component of 4D vector to get a 3D vector"
  {:malli/schema [:=> [:cat fvec4] fvec3]}
  [v]
  (fv/vec3 (v 0) (v 1) (v 2)))


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


(defn matrix->quaternion
  "Convert rotation matrix to quaternion"
  {:malli/schema [:=> [:cat fmat3] quaternion]}
  [m]
  (let [[l1 l2 l3]   (fm/eigenvalues m)
        eigenvectors (fm/eigenvectors m)
        det          (fm/det eigenvectors)
        [v1 v2 v3]   (fm/cols eigenvectors)
        [i1 i2 i3]   (map (comp abs #(% 1)) [l1 l2 l3])
        angle        (fn [l] (atan2 (l 1) (l 0)))]
    (condp = (min ^double i1 ^double i2 ^double i3)
           i1 (rotation (angle (if (pos? det) l3 l2)) v1)
           i2 (rotation (angle (if (pos? det) l1 l3)) v2)
           i3 (rotation (angle (if (pos? det) l2 l1)) v3))))


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
             0       0       0       1))


(defn translation-matrix
  "Create a 4x4 matrix from a translation vector"
  {:malli/schema [:=> [:cat fvec3] fmat4]}
  [v]
  (transformation-matrix (fm/eye 3) v))


(defn rotation-matrix
  "Create 4x4 rotation matrix from 3x3 rotation matrix"
  {:malli/schema [:=> [:cat fmat3] fmat4]}
  [m]
  (transformation-matrix m (fv/vec3 0.0 0.0 0.0)))


(defn get-rotation
  "Extract 3x3 rotation part from 4x4 matrix"
  {:malli/schema [:=> [:cat fmat4] fmat3]}
  [m]
  (fm/mat3x3 (m 0 0) (m 0 1) (m 0 2)
             (m 1 0) (m 1 1) (m 1 2)
             (m 2 0) (m 2 1) (m 2 2)))


(defn get-translation
  "Extract translation vector from 4x4 matrix"
  {:malli/schema [:=> [:cat fmat4] fvec3]}
  [m]
  (fv/vec3 (m 0 3) (m 1 3) (m 2 3)))


(defn projection-matrix
  "Compute OpenGL projection matrix (frustum)"
  {:malli/schema [:=> [:cat :int :int :double :double :double] fmat4]}
  [width height near far field-of-view]
  (let [dx (/ 1.0 (tan (* 0.5 ^double field-of-view)))
        dy (-> dx (* ^long width) (/ ^long height))
        a  (/ (* ^double far ^double near) (- ^double far ^double near))
        b  (/ ^double near (- ^double far ^double near))]
    (fm/mat4x4 dx  0  0  0
               0  dy  0  0
               0   0  b  a
               0   0 -1  0)))


(defn pack-matrices
  "Pack nested vector of matrices into float array"
  {:malli/schema [:=> [:cat [:vector :some]] seqable?]}
  [array]
  (float-array (flatten array)))


(defn z-to-ndc
  "Convert (flipped to positive) z-coordinate to normalized device coordinate"
  ^double [^double near ^double far ^double z]
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


(def bbox (m/schema [:map [:bottomleftnear fvec3] [:toprightfar fvec3]]))


(defn bounding-box
  "Compute 3D bounding box for a set of points"
  {:malli/schema [:=> [:cat [:sequential fvec4]] bbox]}
  [points]
  (let [x (mapv #(/ ^double (% 0) ^double (% 3)) points)
        y (mapv #(/ ^double (% 1) ^double (% 3)) points)
        z (mapv #(/ ^double (% 2) ^double (% 3)) points)]
    {:bottomleftnear (fv/vec3 (apply min x) (apply min y) (apply max z))
     :toprightfar (fv/vec3 (apply max x) (apply max y) (apply min z))}))


(defn expand-bounding-box-near
  "Enlarge bounding box towards positive z (near)"
  {:malli/schema [:=> [:cat bbox :double] bbox]}
  [bounding-box z-expand]
  (update bounding-box :bottomleftnear fv/add (fv/vec3 0 0 z-expand)))


(defn shadow-box-to-ndc
  "Scale and translate light box coordinates to normalized device coordinates"
  {:malli/schema [:=> [:cat bbox] fmat4]}
  [{:keys [^Vec3 bottomleftnear ^Vec3 toprightfar]}]
  (let [left   (bottomleftnear 0)
        right  (toprightfar 0)
        bottom (bottomleftnear 1)
        top    (toprightfar 1)
        near   (- ^double (bottomleftnear 2))
        far    (- ^double (toprightfar 2))]
    (fm/mat4x4 (/ 2.0 (- ^double right ^double left)) 0 0 (- (/ (* 2.0 ^double left) (- ^double left ^double right)) 1.0)
               0 (/ 2.0 (- ^double top ^double bottom)) 0 (- (/ (* 2.0 ^double bottom) (- ^double bottom ^double top)) 1.0)
               0 0 (/ 1.0 (- far near)) (/ far (- far near))
               0 0 0 1)))


(defn shadow-box-to-map
  "Scale and translate light box coordinates to shadow map texture coordinates"
  {:malli/schema [:=> [:cat bbox] fmat4]}
  [bounding-box]
  (fm/mulm (fm/mat4x4 0.5 0 0 0.5, 0 0.5 0 0.5, 0 0 1 0, 0 0 0 1) (shadow-box-to-ndc bounding-box)))


(defn oriented-matrix
  "Create a 3x3 isometry with given normal vector as first row"
  {:malli/schema [:=> [:cat fvec3] fmat3]}
  [n]
  (let [o1 (orthogonal n)
        o2 (fv/cross n o1)]
    (fm/mat3x3 (n 0) (n 1) (n 2) (o1 0) (o1 1) (o1 2) (o2 0) (o2 1) (o2 2))))


(defn orient-to-light
  "Return matrix to rotate points into coordinate system with z-axis pointing towards the light"
  {:malli/schema [:=> [:cat fvec3] fmat4]}
  [light-direction]
  (let [o (oriented-matrix light-direction)]
    (fm/mat4x4 (o 1 0) (o 1 1) (o 1 2) 0
               (o 2 0) (o 2 1) (o 2 2) 0
               (o 0 0) (o 0 1) (o 0 2) 0
               0       0       0       1)))


(defn- transform-point-list
  "Apply transform to frustum corners"
  {:malli/schema [:=> [:cat fmat4 [:vector fvec4]] [:vector fvec4]]}
  [matrix corners]
  (mapv #(fm/mulv matrix %) corners))


(defn- span-of-box
  "Get vector of box dimensions"
  {:malli/schema [:=> [:cat bbox] fvec3]}
  ^Vec3 [bounding-box]
  (fv/sub (:toprightfar bounding-box) (:bottomleftnear bounding-box)))


(defn- bounding-box-for-rotated-frustum
  "Determine bounding box for rotated frustum"
  {:malli/schema [:=> [:cat fmat4 fmat4 fmat4 :double :double :double]]}
  [camera-to-world light-matrix projection-matrix longest-shadow ndc1 ndc2]
  (let [corners         (frustum-corners projection-matrix ndc1 ndc2)
        rotated-corners (transform-point-list camera-to-world corners)
        light-corners   (transform-point-list light-matrix rotated-corners)]
    (expand-bounding-box-near (bounding-box light-corners) longest-shadow)))


(def shadow-config
  (m/schema [:map [:sfsim.opacity/num-opacity-layers N] [:sfsim.opacity/shadow-size N]
             [:sfsim.opacity/num-steps N] [:sfsim.opacity/scene-shadow-counts [:vector N0]]
             [:sfsim.opacity/mix :double] [:sfsim.opacity/shadow-bias :double]
             [:sfsim.opacity/opacity-biases [:vector :double]]]))


(def shadow-data (m/schema [:and shadow-config [:map [:sfsim.opacity/depth :double]]]))

(def shadow-box (m/schema [:map [::world-to-shadow-ndc fmat4] [::world-to-shadow-map fmat4] [::scale :double] [::depth :double]]))


(defn shadow-matrices
  "Choose NDC and texture coordinate matrices for shadow mapping of view frustum or part of frustum"
  {:malli/schema [:=> [:cat fmat4 fmat4 fvec3 :double [:? [:cat :double :double]]] shadow-box]}
  ([projection-matrix camera-to-world light-direction longest-shadow]
   (shadow-matrices projection-matrix camera-to-world light-direction longest-shadow 1.0 0.0))
  ([projection-matrix camera-to-world light-direction longest-shadow ndc1 ndc2]
   (let [light-matrix (orient-to-light light-direction)
         bounding-box (bounding-box-for-rotated-frustum camera-to-world light-matrix projection-matrix longest-shadow ndc1 ndc2)
         shadow-ndc   (shadow-box-to-ndc bounding-box)
         shadow-map   (shadow-box-to-map bounding-box)
         span         (span-of-box bounding-box)
         scale        (* 0.5 (+ ^double (span 0) ^double (span 1)))
         depth        (- ^double (span 2))]
     {::world-to-shadow-ndc (fm/mulm shadow-ndc light-matrix)
      ::world-to-shadow-map (fm/mulm shadow-map light-matrix)
      ::scale scale
      ::depth depth})))


(defn split-linear
  "Perform linear z-split for frustum"
  ^double [^double z-near ^double z-far ^long num-steps ^long step]
  (+ z-near (/ (* (- z-far z-near) step) num-steps)))


(defn split-exponential
  "Perform exponential z-split for frustum"
  ^double [^double z-near ^double z-far ^long num-steps ^long step]
  (* z-near (pow (/ z-far z-near) (/ step num-steps))))


(defn split-mixed
  "Mixed linear and exponential split"
  {:malli/schema [:=> [:cat :double :double :double N N0] :double]}
  [mix z-near z-far num-steps step]
  (+ (* (- 1.0 ^double mix) (split-linear z-near z-far num-steps step))
     (* ^double mix (split-exponential z-near z-far num-steps step))))


(defn split-list
  "Get list of splits including z-far"
  {:malli/schema [:=> [:cat :map :map] [:vector :double]]}
  [{:sfsim.opacity/keys [^double mix ^long num-steps]} {:sfsim.render/keys [^double z-near ^double z-far]}]
  (mapv (partial split-mixed mix z-near z-far num-steps) (range (inc num-steps))))


(defn biases-like
  "Create list of increasing biases"
  [^double opacity-bias splits]
  (mapv #(* opacity-bias ^double (/ ^double % ^double (second splits))) (rest splits)))


(defn shadow-matrix-cascade
  "Compute cascade of shadow matrices for view frustum"
  {:malli/schema [:=> [:cat :map :map] [:vector shadow-box]]}
  [{:sfsim.opacity/keys [num-steps mix depth]} {:sfsim.render/keys [projection camera-to-world light-direction z-near z-far]}]
  (mapv (fn shadow-matrices-for-segment
          [^long idx]
          (let [ndc1 (z-to-ndc z-near z-far (split-mixed mix z-near z-far num-steps idx))
                ndc2 (z-to-ndc z-near z-far (split-mixed mix z-near z-far num-steps (inc idx)))]
            (shadow-matrices projection camera-to-world light-direction depth ndc1 ndc2)))
        (range num-steps)))


(def shadow-patch
  (m/schema [:map [::object-to-shadow-ndc fmat4]
             [::object-to-shadow-map fmat4]
             [::world-to-object fmat4]
             [::scale :double]
             [::depth :double]]))


(defn shadow-patch-matrices
  "Shadow matrices for an object mapping object coordinates to shadow coordinates"
  [^Mat4x4 object-to-world ^Vec3 light-direction ^double object-radius]
  (let [a               (- object-radius)
        b               (+ object-radius)
        bounding-box    {:bottomleftnear (fv/vec3 a a b) :toprightfar (fv/vec3 b b a)}
        shadow-ndc      (shadow-box-to-ndc bounding-box)
        shadow-map      (shadow-box-to-map bounding-box)
        world-to-object (fm/inverse object-to-world)
        light-matrix    (orient-to-light (vec4->vec3 (fm/mulv world-to-object (vec3->vec4 light-direction 0.0))))]
    {::object-to-shadow-ndc (fm/mulm shadow-ndc light-matrix)
     ::object-to-shadow-map (fm/mulm shadow-map light-matrix)
     ::world-to-object      world-to-object
     ::scale                (* 2.0 object-radius)
     ::depth                (* 2.0 object-radius)}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
