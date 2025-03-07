(ns sfsim.jolt
  "Interface with native Jolt physics library"
  (:require
    [clojure.spec.alpha :as s]
    [coffi.ffi :refer (defcfn) :as ffi]
    [coffi.mem :as mem]
    [fastmath.matrix :refer (mat3x3 mulm mulv)]
    [fastmath.vector :refer (vec3)]
    [sfsim.matrix :refer (vec3->vec4 vec4->vec3)]
    [sfsim.quaternion :as q]))


(defn const
  [symbol-or-addr type]
  (mem/deserialize (ffi/ensure-symbol symbol-or-addr) [::mem/pointer type]))


;; Temporarily keep a bugfixed implementation here.
(s/def ::defconst-args
  (s/cat :var-name simple-symbol?
         :docstring (s/? string?)
         :symbol-or-addr any?
         :type ::mem/type))


;; Temporarily implement macro using bugfixed const method.
(defmacro defconst
  {:arglists '([symbol docstring? symbol-or-addr type])}
  [& args]
  (let [args (s/conform ::defconst-args args)]
    `(let [symbol# (ffi/ensure-symbol ~(:symbol-or-addr args))]
       (def ~(:var-name args)
         ~@(when-let [doc (:docstring args)]
             (list doc))
         (const symbol# ~(:type args))))))


(s/fdef defconst
        :args ::defconst-args)


(ffi/load-library "src/c/sfsim/libjolt.so")


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
  make_wheel_settings [::vec3 ::mem/float ::mem/float ::mem/float ::mem/float ::mem/float] ::mem/pointer)


(defn make-wheel-settings
  "Create wheel settings object for wheeled vehicle"
  [{::keys [position width radius inertia suspension-min-length suspension-max-length]}]
  (make-wheel-settings- position width radius inertia suspension-min-length suspension-max-length))


(defcfn destroy-wheel-settings
  "Destroy wheel settings object"
  destroy_wheel_settings [::mem/pointer] ::mem/void)


(defcfn make-vehicle-constraint-settings
  "Create vehicle constraint settings object"
  make_vehicle_constraint_settings [] ::mem/pointer)


(defcfn vehicle-constraint-settings-add-wheel
  "Add wheel to vehicle constraint settings"
  vehicle_constraint_settings_add_wheel [::mem/pointer ::mem/pointer] ::mem/void)


(defcfn create-and-add-vehicle-constraint-
  "Create and add vehicle constraint (private method)"
  create_and_add_vehicle_constraint [::mem/int ::mem/pointer] ::mem/pointer)


(defn create-and-add-vehicle-constraint
  "Create and add vehicle constraint"
  [body wheels]
  (let [constraint-settings (make-vehicle-constraint-settings)]
    (doseq [wheel wheels] (vehicle-constraint-settings-add-wheel constraint-settings (make-wheel-settings wheel)))
    (create-and-add-vehicle-constraint- body constraint-settings)))


(defcfn remove-and-destroy-constraint
  "Remove and destroy vehicle constraint"
  remove_and_destroy_constraint [::mem/pointer] ::mem/void)
