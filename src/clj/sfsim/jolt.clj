(ns sfsim.jolt
  "Interface with native Jolt physics library"
  (:require
    [clojure.spec.alpha :as s]
    [coffi.ffi :refer (defcfn) :as ffi]
    [coffi.mem :as mem]
    [fastmath.matrix :refer (mat3x3)]
    [fastmath.vector :refer (vec3)]
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


(defcfn make-sphere
  "Create sphere body"
  make_sphere [::mem/float ::mem/float ::vec3 ::quaternion ::vec3 ::vec3] ::mem/int)


(defcfn make-box
  "Create box body"
  make_box [::vec3 ::mem/float ::vec3 ::quaternion ::vec3 ::vec3] ::mem/int)


(defcfn make-mesh_
  "Create a mesh object (private function)"
  make_mesh [::mem/pointer ::mem/int ::mem/pointer ::mem/int ::mem/float ::vec3 ::quaternion] ::mem/int)


(defn make-mesh
  "Create a static mesh object"
  [{:sfsim.quadtree/keys [vertices triangles]} mass center rotation]
  (let [arena (mem/auto-arena)
        num-vertices (count vertices)
        num-triangles (count triangles)]
    (make-mesh_ (mem/serialize (apply concat vertices) [::mem/array ::mem/float (* 3 num-vertices)] arena) num-vertices
                (mem/serialize (apply concat triangles) [::mem/array ::mem/int (* 3 num-triangles)] arena) num-triangles
                mass center rotation)))


(defcfn make-convex-hull_
  "Create a convex hull object (private function)"
  make_convex_hull [::mem/pointer ::mem/int ::mem/float ::mem/float ::vec3 ::quaternion] ::mem/int)


(defn make-convex-hull
  "Create a convex hull"
  [vertices convex-radius density center rotation]
  (let [arena        (mem/auto-arena)
        num-vertices (count vertices)]
    (make-convex-hull_ (mem/serialize (apply concat vertices) [::mem/array ::mem/float (* 3 num-vertices)] arena) num-vertices
                       convex-radius density center rotation)))


(defcfn set-friction
  "Set friction constant of body surface"
  set_friction [::mem/int ::mem/float] ::mem/void)


(defcfn set-restitution
  "Set restitution constant of body surface"
  set_restitution [::mem/int ::mem/float] ::mem/void)


(defcfn add-force
  "Apply a force in the next physics update"
  add_force [::mem/int ::vec3] ::mem/void)


(defcfn add-torque
  "Apply a torque in the next physics update"
  add_torque [::mem/int ::vec3] ::mem/void)


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
