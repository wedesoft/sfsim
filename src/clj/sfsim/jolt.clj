(ns sfsim.jolt
    "Interface with native Jolt physics library"
    (:require [coffi.ffi :refer (defcfn) :as ffi]
              [coffi.mem :as mem]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3)]
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

(mem/defalias ::quaternion
  [::mem/struct
   [[:real ::mem/double]
    [:imag ::mem/double]
    [:jmag ::mem/double]
    [:kmag ::mem/double]]])

(defcfn make-sphere_
  make_sphere [::mem/float ::vec3 ::quaternion] ::mem/int)

(defn make-sphere
  "Create sphere body"
  [radius center rotation]
  (make-sphere_ radius
                center
                {:real (.real rotation) :imag (.imag rotation) :jmag (.jmag rotation) :kmag (.kmag rotation)}))

(defcfn remove-and-destroy-body
  "Remove body from physics system and destroy it"
  remove_and_destroy_body [::mem/int] ::mem/void)

(defcfn get-translation
  get_translation [::mem/int] ::vec3)

(mem/defalias ::mat3x3
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

(defcfn get-rotation_
  get_rotation [::mem/int] ::mat3x3)

(defn get-rotation
  [id]
  "Get rotation matrix of a body's world transform"
  (let [result (get-rotation_ id)]
    (mat3x3
      (result :m00) (result :m01) (result :m02)
      (result :m10) (result :m11) (result :m12)
      (result :m20) (result :m21) (result :m22))))
