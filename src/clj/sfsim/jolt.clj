;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.jolt
  "Interface with native Jolt physics library"
  (:require
    [coffi.ffi :refer (defcfn defconst) :as ffi]
    [coffi.mem :as mem]
    [fastmath.matrix :refer (mat3x3 mulm mulv mat4x4)]
    [fastmath.vector :refer (vec3)]
    [sfsim.matrix :refer (vec3->vec4 vec4->vec3)]
    [sfsim.quaternion :as q]))


(ffi/load-library "libjolt.so")


(defcfn jolt-init
  "Initialize Jolt library"
  jolt_init [] ::mem/void)


(defconst NON-MOVING-LAYER "NON_MOVING" ::mem/short)
(defconst MOVING-LAYER "MOVING" ::mem/short)
(defconst NUM-LAYERS "NUM_LAYERS" ::mem/short)


(defcfn jolt-destroy
  "Destruct Jolt library setup"
  jolt_destroy [] ::mem/void)


(def vec3-struct
  [::mem/struct
   [[:x ::mem/double]
    [:y ::mem/double]
    [:z ::mem/double]]])


(defmethod mem/c-layout ::vec3
  [_vec3]
  (mem/c-layout vec3-struct))


(defmethod mem/serialize-into ::vec3
  [obj _vec3 segment arena]
  (mem/serialize-into {:x (obj 0) :y (obj 1) :z (obj 2)} vec3-struct segment arena))


(defmethod mem/deserialize-from ::vec3
  [segment _vec3]
  (let [result (mem/deserialize-from segment vec3-struct)]
    (vec3 (:x result) (:y result) (:z result))))


(def quaternion-struct [::mem/struct [[:real ::mem/double] [:imag ::mem/double] [:jmag ::mem/double] [:kmag ::mem/double]]])


(defmethod mem/c-layout ::quaternion
  [_quaternion]
  (mem/c-layout quaternion-struct))


(defmethod mem/serialize-into ::quaternion
  [obj _quaternion segment arena]
  (mem/serialize-into {:real (.real obj) :imag (.imag obj) :jmag (.jmag obj) :kmag (.kmag obj)} quaternion-struct segment arena))


(defmethod mem/deserialize-from ::quaternion
  [segment _quaternion]
  (let [result (mem/deserialize-from segment quaternion-struct)]
    (q/->Quaternion (:real result) (:imag result) (:jmag result) (:kmag result))))


(def mat3x3-struct
  [::mem/struct
   [[:m00 ::mem/double]
    [:m01 ::mem/double]
    [:m02 ::mem/double]
    [:m10 ::mem/double]
    [:m11 ::mem/double]
    [:m12 ::mem/double]
    [:m20 ::mem/double]
    [:m21 ::mem/double]
    [:m22 ::mem/double]]])


(defmethod mem/c-layout ::mat3x3
  [_mat3x3]
  (mem/c-layout mat3x3-struct))


(defmethod mem/serialize-into ::mat3x3
  [obj _mat3x3 segment arena]
  (mem/serialize-into {:m00 (obj 0 0) :m01 (obj 0 1) :m02 (obj 0 2)
                       :m10 (obj 1 0) :m11 (obj 1 1) :m12 (obj 1 2)
                       :m20 (obj 2 0) :m21 (obj 2 1) :m22 (obj 2 2)}
                      mat3x3-struct segment arena))


(defmethod mem/deserialize-from ::mat3x3
  [segment _mat3x3]
  (let [result (mem/deserialize-from segment mat3x3-struct)]
    (mat3x3
      (result :m00) (result :m01) (result :m02)
      (result :m10) (result :m11) (result :m12)
      (result :m20) (result :m21) (result :m22))))


(def mat4x4-struct
  [::mem/struct
   [[:m00 ::mem/double]
    [:m01 ::mem/double]
    [:m02 ::mem/double]
    [:m03 ::mem/double]
    [:m10 ::mem/double]
    [:m11 ::mem/double]
    [:m12 ::mem/double]
    [:m13 ::mem/double]
    [:m20 ::mem/double]
    [:m21 ::mem/double]
    [:m22 ::mem/double]
    [:m23 ::mem/double]
    [:m30 ::mem/double]
    [:m31 ::mem/double]
    [:m32 ::mem/double]
    [:m33 ::mem/double]]])


(defmethod mem/c-layout ::mat4x4
  [_mat4x4]
  (mem/c-layout mat4x4-struct))


(defmethod mem/serialize-into ::mat4x4
  [obj _mat4x4 segment arena]
  (mem/serialize-into {:m00 (obj 0 0) :m01 (obj 0 1) :m02 (obj 0 2) :m03 (obj 0 3)
                       :m10 (obj 1 0) :m11 (obj 1 1) :m12 (obj 1 2) :m13 (obj 1 3)
                       :m20 (obj 2 0) :m21 (obj 2 1) :m22 (obj 2 2) :m23 (obj 2 3)
                       :m30 (obj 3 0) :m31 (obj 3 1) :m32 (obj 3 2) :m33 (obj 3 3)}
                      mat4x4-struct segment arena))


(defmethod mem/deserialize-from ::mat4x4
  [segment _mat4x4]
  (let [result (mem/deserialize-from segment mat4x4-struct)]
    (mat4x4
      (result :m00) (result :m01) (result :m02) (result :m03)
      (result :m10) (result :m11) (result :m12) (result :m13)
      (result :m20) (result :m21) (result :m22) (result :m23)
      (result :m30) (result :m31) (result :m32) (result :m33))))


(defcfn set-gravity
  "Set gravity vector"
  set_gravity [::vec3] ::mem/void)


(defcfn optimize-broad-phase
  "Optimize broad phase of collision algorithm"
  optimize_broad_phase [] ::mem/void)


(defcfn update-system
  "Perform time step of physics system"
  update_system [::mem/double ::mem/int] ::mem/void)


(defcfn create-and-add-dynamic-body
  "Create and add moving body to physics system"
  create_and_add_dynamic_body [::mem/pointer ::vec3 ::quaternion] ::mem/int)


(defcfn create-and-add-static-body
  "Create and add stationary body to physics system"
  create_and_add_static_body [::mem/pointer ::vec3 ::quaternion] ::mem/int)


(defcfn sphere-settings
  "Create sphere settings object"
  sphere_settings [::mem/float ::mem/float] ::mem/pointer)


(defcfn box-settings
  "Create box settings object"
  box_settings [::vec3 ::mem/float] ::mem/pointer)


(defcfn mesh-settings-
  "Create a mesh settings object (private function)"
  mesh_settings [::mem/pointer ::mem/int ::mem/pointer ::mem/int ::mem/float] ::mem/pointer)


(defn mesh-settings
  "Create mesh settings object"
  [{:sfsim.quadtree/keys [vertices triangles]} mass]
  (let [arena (mem/auto-arena)
        num-vertices (count vertices)
        num-triangles (count triangles)]
    (mesh-settings- (mem/serialize (apply concat vertices) [::mem/array ::mem/float (* 3 num-vertices)] arena) num-vertices
                    (mem/serialize (apply concat triangles) [::mem/array ::mem/int (* 3 num-triangles)] arena) num-triangles
                    mass)))


(defcfn convex-hull-settings-
  "Create a convex hull settings object (private function)"
  convex_hull_settings [::mem/pointer ::mem/int ::mem/float ::mem/float] ::mem/pointer)


(defn convex-hull-settings
  "Create convex hull settings object"
  [vertices convex-radius density]
  (let [arena        (mem/auto-arena)
        num-vertices (count vertices)]
    (when (< num-vertices 4)
      (throw (RuntimeException. "Convex hull must have at least 4 vertices")))
    (convex-hull-settings- (mem/serialize (apply concat vertices) [::mem/array ::mem/float (* 3 num-vertices)] arena) num-vertices
                           convex-radius density)))


(defcfn static-compound-settings-
  "Create a static compound settings object (private function)"
  static_compound_settings [] ::mem/pointer)


(defcfn static-compound-add-shape-
  "Add sub shape to compound shape (private method)"
  static_compound_add_shape [::mem/pointer ::vec3 ::quaternion ::mem/pointer] ::mem/void)


(defn static-compound-settings
  "Create static compound settings object"
  [body-settings]
  (let [result (static-compound-settings-)]
    (when (empty? body-settings)
      (throw (RuntimeException. "Static compound must have at least one sub shape")))
    (doseq [{::keys [shape position rotation]} body-settings]
           (static-compound-add-shape- result position rotation shape))
    result))


(defn compound-of-convex-hulls-settings
  "Convert extracted points from model to compound of convex hulls settings"
  ([model-points convex-radius density]
   (compound-of-convex-hulls-settings (:sfsim.model/children model-points) convex-radius density
                                      (:sfsim.model/transform model-points)))
  ([child-list convex-radius density transform]
   (if (vector? (first child-list))
     (convex-hull-settings (mapv #(vec4->vec3 (mulv transform (vec3->vec4 % 1.0))) child-list) convex-radius density)
     (static-compound-settings
       (mapv (fn [shape] {::shape (compound-of-convex-hulls-settings (:sfsim.model/children shape) convex-radius density
                                                                     (mulm transform (:sfsim.model/transform shape)))
                          ::position (vec3 0 0 0)
                          ::rotation (q/->Quaternion 1 0 0 0)})
             child-list)))))


(defcfn set-friction
  "Set friction constant of body surface"
  set_friction [::mem/int ::mem/float] ::mem/void)


(defcfn set-restitution
  "Set restitution constant of body surface"
  set_restitution [::mem/int ::mem/float] ::mem/void)


(defcfn get-mass
  "Get mass of body"
  get_mass [::mem/int] ::mem/float)


(defcfn get-inertia
  "Get inertia matrix of body"
  get_inertia [::mem/int] ::mat3x3)


(defcfn get-center-of-mass
  "Get center of mass of body"
  get_center_of_mass [::mem/int] ::vec3)


(defcfn add-force
  "Apply a force in the next physics update"
  add_force [::mem/int ::vec3] ::mem/void)


(defcfn add-torque
  "Apply a torque in the next physics update"
  add_torque [::mem/int ::vec3] ::mem/void)


(defcfn add-impulse
  "Apply an impulse in the next physics update"
  add_impulse [::mem/int ::vec3] ::mem/void)


(defcfn add-angular-impulse
  "Apply an angular impulse in the next physics update"
  add_angular_impulse [::mem/int ::vec3] ::mem/void)


(defcfn activate-body
  "Make sure body is active"
  activate_body [::mem/int] ::mem/void)


(defcfn remove-and-destroy-body
  "Remove body from physics system and destroy it"
  remove_and_destroy_body [::mem/int] ::mem/void)


(defcfn get-translation
  get_translation [::mem/int] ::vec3)


(defcfn set-translation
  set_translation [::mem/int ::vec3] ::mem/void)


(defcfn get-rotation
  "Get rotation matrix of a body's world transform"
  get_rotation [::mem/int] ::mat3x3)


(defcfn get-orientation
  "Get rotation quaternion of a body's world transform"
  get_orientation [::mem/int] ::quaternion)


(defcfn set-orientation
  "Set rotation quaternion of a body's world transform"
  set_orientation [::mem/int ::quaternion] ::mem/void)


(defcfn get-linear-velocity
  "Get linear velocity of a body"
  get_linear_velocity [::mem/int] ::vec3)


(defcfn set-linear-velocity
  "Set linear velocity of a body"
  set_linear_velocity [::mem/int ::vec3] ::mem/void)


(defcfn get-angular-velocity
  "Get angular velocity of a body"
  get_angular_velocity [::mem/int] ::vec3)


(defcfn set-angular-velocity
  "Set angular velocity of a body"
  set_angular_velocity [::mem/int ::vec3] ::mem/void)


(defcfn make-wheel-settings-
  "Create wheel settings object for wheeled vehicle (private)"
  make_wheel_settings [::vec3 ::mem/float ::mem/float ::mem/float ::mem/float ::vec3 ::vec3 ::mem/float ::mem/float ::mem/float
                       ::mem/float ::mem/float] ::mem/pointer)


(defn make-wheel-settings
  "Create wheel settings object for wheeled vehicle"
  [{::keys [position width radius inertia angular-damping suspension-min-length suspension-max-length stiffness damping
            max-brake-torque]
    :or {angular-damping 0.0 max-brake-torque 0.0}}
   up forward]
  (make-wheel-settings- position width radius inertia angular-damping up forward suspension-min-length suspension-max-length
                        stiffness damping max-brake-torque))


(defcfn destroy-wheel-settings
  "Destroy wheel settings object"
  destroy_wheel_settings [::mem/pointer] ::mem/void)


(defcfn make-vehicle-constraint-settings
  "Create vehicle constraint settings object"
  make_vehicle_constraint_settings [::vec3 ::vec3] ::mem/pointer)


(defcfn vehicle-constraint-settings-add-wheel
  "Add wheel to vehicle constraint settings"
  vehicle_constraint_settings_add_wheel [::mem/pointer ::mem/pointer] ::mem/void)


(defcfn create-and-add-vehicle-constraint-
  "Create and add vehicle constraint (private method)"
  create_and_add_vehicle_constraint [::mem/int ::mem/pointer] ::mem/pointer)


(defn create-and-add-vehicle-constraint
  "Create and add vehicle constraint"
  [body up forward wheels]
  (let [constraint-settings (make-vehicle-constraint-settings up forward)]
    (doseq [wheel wheels] (vehicle-constraint-settings-add-wheel constraint-settings (make-wheel-settings wheel up forward)))
    (create-and-add-vehicle-constraint- body constraint-settings)))


(defcfn set-brake-input
  "Set brake input between 0 and 1"
  set_brake_input [::mem/pointer ::mem/float] ::mem/void)


(defcfn get-wheel-local-transform
  "Get wheel pose in local coordinate system"
  get_wheel_local_transform [::mem/pointer ::mem/int ::vec3 ::vec3] ::mat4x4)


(defcfn get-suspension-length
  "Get wheel suspension length"
  get_suspension_length [::mem/pointer ::mem/int] ::mem/float)


(defcfn get-wheel-rotation-angle
  "Get wheel rotation angle"
  get_wheel_rotation_angle [::mem/pointer ::mem/int] ::mem/float)


(defcfn has-hit-hard-point-
  "Check if wheel suspension has hit its upper limit"
  has_hit_hard_point [::mem/pointer ::mem/int] ::mem/byte)


(definline has-hit-hard-point
  "Check if wheel suspension has hit its upper limit"
  [constraint wheel]
  `(not (zero? (has-hit-hard-point- ~constraint ~wheel))))


(defcfn remove-and-destroy-constraint
  "Remove and destroy vehicle constraint"
  remove_and_destroy_constraint [::mem/pointer] ::mem/void)
