(ns sfsim.jolt
    "Interface with native Jolt physics library"
    (:require [coffi.ffi :refer (defcfn) :as ffi]
              [coffi.mem :as mem]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3)]
              [sfsim.quaternion :as q]
              [clojure.spec.alpha :as s]))

(defn const
  [symbol-or-addr type]
  (mem/deserialize (ffi/ensure-symbol symbol-or-addr) [::mem/pointer type]))

; Temporarily keep a bugfixed implementation here.
(s/def ::defconst-args
  (s/cat :var-name simple-symbol?
         :docstring (s/? string?)
         :symbol-or-addr any?
         :type ::mem/type))

; Temporarily implement macro using bugfixed const method.
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

(def vec3-struct [::mem/struct
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

(def mat3x3-struct [::mem/struct
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

(defcfn update-system
  "Perform time step of physics system"
  update_system [::mem/double] ::mem/void)

(defcfn make-sphere
  "Create sphere body"
  make_sphere [::mem/float ::vec3 ::quaternion] ::mem/int)

(defcfn remove-and-destroy-body
  "Remove body from physics system and destroy it"
  remove_and_destroy_body [::mem/int] ::mem/void)

(defcfn get-translation
  get_translation [::mem/int] ::vec3)

(defcfn get-rotation
  "Get rotation matrix of a body's world transform"
  get_rotation [::mem/int] ::mat3x3)

(defcfn get-linear-velocity
  "Get linear velocity of a body"
  get_linear_velocity [::mem/int] ::vec3)
