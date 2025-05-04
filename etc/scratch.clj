(require '[sfsim.matrix :as matrix])
(require '[fastmath.matrix :refer (mulm)])
(require '[fastmath.vector :refer (vec3)])
(require '[sfsim.aerodynamics :as aerodynamics])
(require '[sfsim.model :as model])
(require '[sfsim.util :as util])

(def model (model/remove-empty-meshes (model/read-gltf "venturestar.glb")))

(keys model)
(def node-names (map :sfsim.model/name (:sfsim.model/children (:sfsim.model/root model))))
(def main-wheel-left-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Main Wheel Left")])
(def main-wheel-right-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Main Wheel Right")])
(def nose-wheel-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Nose Wheel")])


(get-in model main-wheel-left-path)
(get-in model main-wheel-right-path)
(get-in model nose-wheel-path)

(get-in (:sfsim.model/meshes model) [(get-in model (concat main-wheel-left-path [:sfsim.model/children 0 :sfsim.model/mesh-indices 0])) :sfsim.model/material-index])
(get-in (:sfsim.model/meshes model) [(get-in model (concat nose-wheel-path [:sfsim.model/children 0 :sfsim.model/mesh-indices 0])) :sfsim.model/material-index])
