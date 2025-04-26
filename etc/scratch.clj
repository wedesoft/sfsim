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
(map :sfsim.model/name (:sfsim.model/children (:sfsim.model/root model)))
(def main-wheel-left-pos (matrix/get-translation
                           (mulm (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
                                 (:sfsim.model/transform (util/find-if (fn [node] (= (:sfsim.model/name node) "Main Wheel Left"))
                                                                       (:sfsim.model/children (:sfsim.model/root model)))))))
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
                 :sfsim.jolt/inertia 0.1
                 :sfsim.jolt/suspension-min-length 0.5
                 :sfsim.jolt/suspension-max-length 1.5})

(def main-wheel-left (assoc wheel-base :sfsim.jolt/position main-wheel-left-pos))
(def main-wheel-right (assoc wheel-base :sfsim.jolt/position main-wheel-right-pos))
(def nose-wheel (assoc wheel-base :sfsim.jolt/position nose-wheel-pos))

(jolt/jolt-destroy)
