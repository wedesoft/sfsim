(require '[sfsim.matrix :as matrix])
(require '[fastmath.matrix :refer (mulm)])
(require '[fastmath.vector :refer (vec3)])
(require '[sfsim.aerodynamics :as aerodynamics])
(require '[sfsim.model :as model])
(require '[sfsim.jolt :as jolt])
(require '[sfsim.util :as util])

(jolt/jolt-init)

(def model (model/read-gltf "venturestar.glb"))
(def convex-hulls (update (model/empty-meshes-to-points model)
                          :sfsim.model/transform
                          #(mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic) %)))

(keys model)
(def node-names (map :sfsim.model/name (:sfsim.model/children (:sfsim.model/root model))))
(def main-wheel-left-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Main Wheel Left")])
(def main-wheel-right-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Main Wheel Right")])
(def nose-wheel-path [:sfsim.model/root :sfsim.model/children (.indexOf node-names "Nose Wheel")])

(def main-wheel-left-pos (matrix/get-translation (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                                                       (:sfsim.model/transform (get-in model main-wheel-left-path)))))
(def main-wheel-right-pos (matrix/get-translation (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                                                        (:sfsim.model/transform (get-in model main-wheel-right-path)))))
(def nose-wheel-pos (matrix/get-translation (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                                                  (:sfsim.model/transform (get-in model nose-wheel-path)))))

(def main-wheel-right-pos (matrix/get-translation
                            (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                                  (:sfsim.model/transform (util/find-if (fn [node] (= (:sfsim.model/name node) "Main Wheel Right"))
                                                                        (:sfsim.model/children (:sfsim.model/root model)))))))
(def nose-wheel-pos (matrix/get-translation
                      (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                            (:sfsim.model/transform (util/find-if (fn [node] (= (:sfsim.model/name node) "Nose Wheel"))
                                                                  (:sfsim.model/children (:sfsim.model/root model)))))))

(def wheel-base {:sfsim.jolt/position (vec3 0.0 0.0 0.0)
                 :sfsim.jolt/width 0.4064
                 :sfsim.jolt/radius (/ 1.1303 2.0)
                 :sfsim.jolt/inertia 16.3690
                 :sfsim.jolt/suspension-min-length 0.4572
                 :sfsim.jolt/suspension-max-length 0.8128})

(def main-wheel-left (assoc wheel-base :sfsim.jolt/position main-wheel-left-pos))
(def main-wheel-right (assoc wheel-base :sfsim.jolt/position main-wheel-right-pos))
(def nose-wheel (assoc wheel-base :sfsim.jolt/position nose-wheel-pos))

(jolt/jolt-destroy)
