;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-integration
  (:require
    [clojure.java.io :as io]
    [clojure.math :refer (PI to-radians)]
    [fastmath.matrix :refer (mat3x3 mulm mulv rotation-matrix-3d-x rotation-matrix-3d-y rotation-matrix-3d-z)]
    [fastmath.vector :refer (vec2 vec3 add div sub)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.astro :refer :all]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.clouds :as clouds]
    [sfsim.config :as config]
    [sfsim.conftest :refer (roughly-vector roughly-matrix is-image)]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix
                          matrix->quaternion)]
    [sfsim.model :as model]
    [sfsim.opacity :as opacity]
    [sfsim.planet :as planet]
    [sfsim.plume :as plume]
    [sfsim.quadtree :refer :all]
    [sfsim.quaternion :as q]
    [sfsim.render :refer :all]
    [sfsim.texture :refer :all])
  (:import
    (org.lwjgl.glfw
      GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(defn load-tile-tree
  [planet-renderer tree width position n]
  (if (zero? n)
    tree
    (let [data (planet/background-tree-update planet-renderer tree width position)
          tree (planet/load-tiles-into-opengl planet-renderer (:tree data) (:load data))]
      (load-tile-tree planet-renderer tree width position (dec n)))))


(when (.exists (io/file ".integration"))
  (tabular "Integration test rendering of planet, atmosphere, and clouds"
           (fact
             (with-invisible-window
               (let [width                     320
                     height                    240
                     level                     5
                     opacity-base              250.0
                     cloud-data                (clouds/make-cloud-data config/cloud-config)
                     atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
                     shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
                     data                      {:sfsim.render/config config/render-config
                                                :sfsim.planet/config config/planet-config
                                                :sfsim.opacity/data shadow-data
                                                :sfsim.clouds/data cloud-data
                                                :sfsim.model/data config/model-config
                                                :sfsim.atmosphere/luts atmosphere-luts}
                     opacity-renderer          (opacity/make-opacity-renderer data)
                     planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                     planet-renderer           (planet/make-planet-renderer data)
                     atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                     geometry-renderer         (model/make-joined-geometry-renderer data)
                     cloud-renderer            (clouds/make-cloud-renderer data)
                     tree                      (load-tile-tree planet-renderer {} width ?position level)
                     model                     (model/read-gltf "test/clj/sfsim/fixtures/model/empty.glb")
                     light-direction           (vec3 1 0 0)
                     object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -1)))
                     model-vars                (model/make-model-vars 0.0 1.0 0.0)
                     render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                               width height ?position ?orientation light-direction
                                                                               object-position (q/->Quaternion 1 0 0 0) model-vars)
                     shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                                   cloud-data render-vars tree opacity-base)
                     cloud-render-vars         (clouds/make-cloud-render-vars config/render-config render-vars width height ?position
                                                                              ?orientation light-direction object-position
                                                                              (q/->Quaternion 1 0 0 0))
                     geometry                  (model/render-joined-geometry geometry-renderer render-vars render-vars model tree)
                     clouds                    (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars
                                                                            [] geometry)
                     tex                       (texture-render-color-depth width height true
                                                                           (clear (vec3 0 1 0) 0.0)
                                                                           (planet/render-planet planet-renderer render-vars
                                                                                                 shadow-vars [] geometry clouds tree)
                                                                           (atmosphere/render-atmosphere atmosphere-renderer
                                                                                                         render-vars geometry clouds))]
                 (texture->image tex) => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 0.5)
                 (destroy-texture tex)
                 (clouds/destroy-cloud-geometry geometry)
                 (destroy-texture clouds)
                 (opacity/destroy-opacity-and-shadow shadow-vars)
                 (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
                 (clouds/destroy-cloud-renderer cloud-renderer)
                 (model/destroy-joined-geometry-renderer geometry-renderer)
                 (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
                 (planet/destroy-planet-renderer planet-renderer)
                 (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
                 (opacity/destroy-opacity-renderer opacity-renderer)
                 (atmosphere/destroy-atmosphere-luts atmosphere-luts)
                 (clouds/destroy-cloud-data cloud-data))))
           ?position                      ?orientation                               ?result
           (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "planet.png"
           (vec3 0 0 (* 1.5 6378000.0))   (q/rotation (to-radians -20) (vec3 0 1 0)) "space.png"))


(when (.exists (io/file ".integration"))
  (tabular "Integration test rendering of object with planet, atmosphere, and clouds"
           (fact
             (with-invisible-window
               (let [width                     320
                     height                    240
                     level                     5
                     opacity-base              250.0
                     cloud-data                (clouds/make-cloud-data config/cloud-config)
                     atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
                     shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
                     data                      {:sfsim.render/config config/render-config
                                                :sfsim.planet/config config/planet-config
                                                :sfsim.opacity/data shadow-data
                                                :sfsim.clouds/data cloud-data
                                                :sfsim.model/data config/model-config
                                                :sfsim.atmosphere/luts atmosphere-luts}
                     object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
                     object-to-world           (transformation-matrix (mulm (rotation-matrix-3d-y (/ PI 4))
                                                                            (rotation-matrix-3d-x (/ PI 6)))
                                                                      object-position)
                     opacity-renderer          (opacity/make-opacity-renderer data)
                     planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                     planet-renderer           (planet/make-planet-renderer data)
                     atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                     geometry-renderer         (model/make-joined-geometry-renderer data)
                     cloud-renderer            (clouds/make-cloud-renderer data)
                     scene-renderer            (model/make-scene-renderer data)
                     model                     (model/read-gltf (str "test/clj/sfsim/fixtures/model/" ?model))
                     light-direction           (vec3 1 0 0)
                     object                    (assoc-in (model/load-scene scene-renderer model)
                                                         [:sfsim.model/root :sfsim.model/transform] object-to-world)
                     tree                      (load-tile-tree planet-renderer {} width ?position level)
                     model-vars                (model/make-model-vars 0.0 1.0 0.0)
                     render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                               width height ?position ?orientation light-direction
                                                                               object-position (q/->Quaternion 1 0 0 0) model-vars)
                     shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                                   cloud-data render-vars tree opacity-base)
                     cloud-render-vars         (clouds/make-cloud-render-vars config/render-config render-vars width height ?position
                                                                              ?orientation light-direction object-position
                                                                              (q/->Quaternion 1 0 0 0))
                     geometry                  (model/render-joined-geometry geometry-renderer render-vars render-vars object tree)
                     clouds                    (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars
                                                                            [] geometry)
                     tex                       (texture-render-color-depth width height true
                                                                           (clear (vec3 0 1 0) 0.0)
                                                                           (model/render-scenes scene-renderer render-vars model-vars
                                                                                                shadow-vars [] geometry clouds
                                                                                                [object])
                                                                           (planet/render-planet planet-renderer render-vars
                                                                                                 shadow-vars [] geometry clouds tree)
                                                                           (atmosphere/render-atmosphere atmosphere-renderer
                                                                                                         render-vars geometry clouds))]
                 (texture->image tex) => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 0.77)
                 (destroy-texture tex)
                 (clouds/destroy-cloud-geometry geometry)
                 (destroy-texture clouds)
                 (opacity/destroy-opacity-and-shadow shadow-vars)
                 (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
                 (clouds/destroy-cloud-renderer cloud-renderer)
                 (model/destroy-joined-geometry-renderer geometry-renderer)
                 (model/destroy-scene object)
                 (model/destroy-scene-renderer scene-renderer)
                 (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
                 (planet/destroy-planet-renderer planet-renderer)
                 (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
                 (opacity/destroy-opacity-renderer opacity-renderer)
                 (atmosphere/destroy-atmosphere-luts atmosphere-luts)
                 (clouds/destroy-cloud-data cloud-data))))
           ?position                      ?orientation                               ?model        ?result
           (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "cube.glb"    "cube.png"
           (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "dice.gltf"   "dice.png"
           (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bump.gltf"   "bump.png"
           (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bricks.gltf" "bricks.png"))


(when (.exists (io/file ".integration"))
  (fact "Integration test rendering of model self-shadowing"
        (with-invisible-window
          (let [width                     320
                height                    240
                level                     5
                opacity-base              250.0
                cloud-data                (clouds/make-cloud-data config/cloud-config)
                atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
                shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
                data                      {:sfsim.render/config config/render-config
                                           :sfsim.planet/config config/planet-config
                                           :sfsim.opacity/data shadow-data
                                           :sfsim.clouds/data cloud-data
                                           :sfsim.model/data config/model-config
                                           :sfsim.atmosphere/luts atmosphere-luts}
                light-direction           (vec3 1 0 0)
                orientation               (q/rotation (to-radians 270) (vec3 0 0 1))
                position                  (vec3 (+ 1.5 6378000.0) 0 0)
                object-radius             1.4
                object-position           (add position (q/rotate-vector orientation (vec3 0 0 -5)))
                object-to-world           (transformation-matrix (mulm (rotation-matrix-3d-y (/ PI 6))
                                                                       (rotation-matrix-3d-x (/ PI 6)))
                                                                 object-position)
                opacity-renderer          (opacity/make-opacity-renderer data)
                planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                planet-renderer           (planet/make-planet-renderer data)
                atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                geometry-renderer         (model/make-joined-geometry-renderer data)
                cloud-renderer            (clouds/make-cloud-renderer data)
                scene-renderer            (model/make-scene-renderer data)
                scene-shadow-renderer     (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                                            object-radius)
                model                     (model/read-gltf "test/clj/sfsim/fixtures/model/torus.gltf")
                object                    (assoc-in (model/load-scene scene-renderer model)
                                                    [:sfsim.model/root :sfsim.model/transform] object-to-world)
                tree                      (load-tile-tree planet-renderer {} width position level)
                model-vars                (model/make-model-vars 0.0 1.0 0.0)
                render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                          width height position orientation light-direction
                                                                          object-position (q/->Quaternion 1 0 0 0) model-vars)
                shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                              cloud-data render-vars tree opacity-base)
                cloud-render-vars         (clouds/make-cloud-render-vars config/render-config render-vars width height position
                                                                         orientation light-direction object-position
                                                                         (q/->Quaternion 1 0 0 0))
                object-shadow             (model/scene-shadow-map scene-shadow-renderer light-direction object)
                geometry                  (model/render-joined-geometry geometry-renderer render-vars render-vars object tree)
                clouds                    (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars
                                                                       [] geometry)
                tex                       (texture-render-color-depth width height true
                                                                      (clear (vec3 0 1 0) 0.0)
                                                                      (model/render-scenes scene-renderer render-vars model-vars
                                                                                           shadow-vars [object-shadow] geometry clouds
                                                                                           [object])
                                                                      (planet/render-planet planet-renderer render-vars shadow-vars
                                                                                            [object-shadow] geometry clouds tree)
                                                                      (atmosphere/render-atmosphere atmosphere-renderer render-vars
                                                                                                    geometry clouds))]
            (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/torus.png" 0.3)
            (destroy-texture tex)
            (clouds/destroy-cloud-geometry geometry)
            (destroy-texture clouds)
            (model/destroy-scene-shadow-map object-shadow)
            (opacity/destroy-opacity-and-shadow shadow-vars)
            (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
            (clouds/destroy-cloud-renderer cloud-renderer)
            (model/destroy-joined-geometry-renderer geometry-renderer)
            (model/destroy-scene object)
            (model/destroy-scene-shadow-renderer scene-shadow-renderer)
            (model/destroy-scene-renderer scene-renderer)
            (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
            (planet/destroy-planet-renderer planet-renderer)
            (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
            (opacity/destroy-opacity-renderer opacity-renderer)
            (atmosphere/destroy-atmosphere-luts atmosphere-luts)
            (clouds/destroy-cloud-data cloud-data)))))


(when (.exists (io/file ".integration"))
  (fact "Test rendering of model"
        (with-invisible-window
          (let [width 320
                height 240
                level                     5
                opacity-base              250.0
                position                  (vec3 (+ 100.0 6378000.0) 0 0)
                orientation               (q/rotation (to-radians 270) (vec3 0 0 1))
                object-position           (add position (q/rotate-vector orientation (vec3 0 0 -50)))
                object-to-world           (transformation-matrix (mulm (rotation-matrix-3d-x (/ PI -6))
                                                                       (rotation-matrix-3d-z (/ PI -2)))
                                                                 object-position)
                object-radius             (:sfsim.model/object-radius config/model-config)
                light-direction           (vec3 1 0 0)
                cloud-data                (clouds/make-cloud-data config/cloud-config)
                atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
                shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
                data                      {:sfsim.render/config config/render-config
                                           :sfsim.planet/config config/planet-config
                                           :sfsim.opacity/data shadow-data
                                           :sfsim.clouds/data cloud-data
                                           :sfsim.model/data config/model-config
                                           :sfsim.atmosphere/luts atmosphere-luts}
                scene-renderer            (model/make-scene-renderer data)
                model                     (model/read-gltf "venturestar.glb")
                object                    (assoc-in (model/load-scene scene-renderer model)
                                                    [:sfsim.model/root :sfsim.model/transform] object-to-world)
                scene-shadow-renderer     (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                                            object-radius)
                opacity-renderer          (opacity/make-opacity-renderer data)
                planet-renderer           (planet/make-planet-renderer data)
                atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                geometry-renderer         (model/make-joined-geometry-renderer data)
                cloud-renderer            (clouds/make-cloud-renderer data)
                tree                      (load-tile-tree planet-renderer {} width position level)
                planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                model-vars                (model/make-model-vars 0.0 1.0 0.0)
                render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                          width height position orientation light-direction
                                                                          object-position (q/->Quaternion 1 0 0 0) model-vars)
                shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                              cloud-data render-vars tree opacity-base)
                cloud-render-vars         (clouds/make-cloud-render-vars config/render-config render-vars width height position
                                                                         orientation light-direction object-position
                                                                         (q/->Quaternion 1 0 0 0))
                object-shadow             (model/scene-shadow-map scene-shadow-renderer light-direction object)
                geometry                  (model/render-joined-geometry geometry-renderer render-vars render-vars object tree)
                clouds                    (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars
                                                                       [] geometry)
                tex    (texture-render-color-depth width height true
                                                   (clear (vec3 0 0 0) 0.0)
                                                   (model/render-scenes scene-renderer render-vars model-vars
                                                                        shadow-vars [object-shadow] geometry clouds
                                                                        [object])
                                                   (planet/render-planet planet-renderer render-vars shadow-vars
                                                                         [object-shadow] geometry clouds tree)
                                                   (atmosphere/render-atmosphere atmosphere-renderer render-vars
                                                                                 geometry clouds))]
            (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/model.png" 0.3)
            (destroy-texture tex)
            (clouds/destroy-cloud-geometry geometry)
            (destroy-texture clouds)
            (clouds/destroy-cloud-renderer cloud-renderer)
            (model/destroy-joined-geometry-renderer geometry-renderer)
            (model/destroy-scene-shadow-map object-shadow)
            (opacity/destroy-opacity-and-shadow shadow-vars)
            (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
            (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
            (planet/destroy-planet-renderer planet-renderer)
            (model/destroy-scene-shadow-renderer scene-shadow-renderer)
            (opacity/destroy-opacity-renderer opacity-renderer)
            (model/destroy-scene object)
            (model/destroy-scene-renderer scene-renderer)
            (atmosphere/destroy-atmosphere-luts atmosphere-luts)
            (clouds/destroy-cloud-data cloud-data)))))


(def vertex-plume "#version 450
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")


(def fragment-plume "#version 450
uniform vec2 resolution;
out vec4 fragColor;
vec4 sample_plume_outer(vec3 object_origin, vec3 object_direction);
void main()
{
  vec2 uv = gl_FragCoord.xy / resolution;
  float offset = 7.5047;
  fragColor = vec4(sample_plume_outer(vec3(-uv.x * 80 + offset, -20, uv.y * 50 - 25), vec3(0, 1, 0)).xyz, 1.0);
}")


(when (.exists (io/file ".integration"))
  (fact "Integration test rendering of rocket plume"
        (with-invisible-window
          (let [width 320
                height 240
                indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.0, 1.0 -1.0 0.0, -1.0 1.0 0.0, 1.0 1.0 0.0]
                program (make-program :sfsim.render/vertex [vertex-plume]
                                      :sfsim.render/fragment [fragment-plume plume/sample-plume-outer])
                vao     (make-vertex-array-object program indices vertices ["point" 3])]
            (use-program program)
            (uniform-vector2 program "resolution" (vec2 width height))
            (uniform-float program "plume_nozzle" 2.7549)
            (uniform-float program "plume_min_limit" 1.2)
            (uniform-float program "plume_max_slope" 1.0)
            (uniform-float program "omega_factor" 0.2)
            (uniform-float program "diamond_strength" 0.2)
            (uniform-float program "plume_step" 0.2)
            (uniform-float program "pressure" 1.0)
            (uniform-float program "plume_throttle" 1.0)
            (uniform-float program "time" 0.0)
            (let [tex (texture-render-color width height true
                                            (clear (vec3 0 0 0))
                                            (render-quads vao))]
              (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/plume.png" 0.0)
              (destroy-texture tex))
            (destroy-vertex-array-object vao)
            (destroy-program program)))))


(when (.exists (io/file ".integration"))
  (fact "Test rendering of model with main engine plume and RCS thrusters"
        (with-invisible-window
          (let [width 320
                height 240
                level                     5
                opacity-base              250.0
                position                  (vec3 (+ 100.0 6378000.0) 0 0)
                orientation               (q/rotation (to-radians 270) (vec3 0 0 1))
                object-position           (add position (q/rotate-vector orientation (vec3 0 0 -50)))
                object-orientation        (matrix->quaternion (mulm (rotation-matrix-3d-x (* 0.8 PI))
                                                                    (rotation-matrix-3d-y (* -0.4 PI))))
                object-to-world           (transformation-matrix (quaternion->matrix object-orientation) object-position)
                object-radius             (:sfsim.model/object-radius config/model-config)
                light-direction           (vec3 1 0 0)
                cloud-data                (clouds/make-cloud-data config/cloud-config)
                atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
                shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
                data                      {:sfsim.render/config config/render-config
                                           :sfsim.planet/config config/planet-config
                                           :sfsim.opacity/data shadow-data
                                           :sfsim.clouds/data cloud-data
                                           :sfsim.model/data config/model-config
                                           :sfsim.atmosphere/luts atmosphere-luts}
                scene-renderer            (model/make-scene-renderer data)
                model                     (model/read-gltf "venturestar.glb")
                gltf-to-aerodynamic       (rotation-matrix aerodynamics/gltf-to-aerodynamic)
                bsp-tree                  (update (model/get-bsp-tree model "BSP")
                                                  :sfsim.model/transform #(mulm gltf-to-aerodynamic %))
                rcs-set                   (fn [rcs-name] [(str "RCS " rcs-name "1") (str "RCS " rcs-name "2") (str "RCS " rcs-name "3")])
                thruster-names            (conj (mapcat rcs-set ["FF" "FU" "L" "LA" "LD" "LU" "R" "RA" "RD" "RU" "LF" "RF" "LFD" "RFD"]) "Plume")
                thruster-transforms       (into {} (remove nil? (map (fn [rcs-name] (some->> (model/get-node-transform model rcs-name) (mulm gltf-to-aerodynamic) (vector rcs-name))) thruster-names)))
                object                    (assoc-in (model/load-scene scene-renderer model)
                                                    [:sfsim.model/root :sfsim.model/transform] (mulm object-to-world gltf-to-aerodynamic))
                scene-shadow-renderer     (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                                            object-radius)
                opacity-renderer          (opacity/make-opacity-renderer data)
                planet-renderer           (planet/make-planet-renderer data)
                atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                geometry-renderer         (model/make-joined-geometry-renderer data)
                cloud-renderer            (clouds/make-cloud-renderer data)
                tree                      (load-tile-tree planet-renderer {} width position level)
                planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                model-vars                (model/make-model-vars 0.0 1.0 0.5)
                render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                          width height position orientation light-direction
                                                                          object-position object-orientation model-vars)
                shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                              cloud-data render-vars tree opacity-base)
                cloud-render-vars         (clouds/make-cloud-render-vars config/render-config render-vars width height position
                                                                         orientation light-direction object-position
                                                                         object-orientation)
                object-origin             (:sfsim.render/object-origin render-vars)
                render-order              (model/bsp-render-order bsp-tree object-origin)
                plume-transforms          (map (fn [thruster] [thruster (thruster-transforms thruster)]) render-order)
                object-shadow             (model/scene-shadow-map scene-shadow-renderer light-direction object)
                geometry                  (model/render-joined-geometry geometry-renderer render-vars render-vars object tree)
                clouds                    (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars
                                                                       plume-transforms geometry)
                tex    (texture-render-color-depth width height true
                                                   (clear (vec3 0 0 0) 0.0)
                                                   (model/render-scenes scene-renderer render-vars model-vars
                                                                        shadow-vars [object-shadow] geometry clouds
                                                                        [object])
                                                   (planet/render-planet planet-renderer render-vars shadow-vars
                                                                         [object-shadow] geometry clouds tree)
                                                   (atmosphere/render-atmosphere atmosphere-renderer render-vars
                                                                                 geometry clouds))]
            (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/model-with-plume.png" 0.3)
            (destroy-texture tex)
            (clouds/destroy-cloud-geometry geometry)
            (destroy-texture clouds)
            (clouds/destroy-cloud-renderer cloud-renderer)
            (model/destroy-joined-geometry-renderer geometry-renderer)
            (model/destroy-scene-shadow-map object-shadow)
            (opacity/destroy-opacity-and-shadow shadow-vars)
            (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
            (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
            (planet/destroy-planet-renderer planet-renderer)
            (model/destroy-scene-shadow-renderer scene-shadow-renderer)
            (opacity/destroy-opacity-renderer opacity-renderer)
            (model/destroy-scene object)
            (model/destroy-scene-renderer scene-renderer)
            (atmosphere/destroy-atmosphere-luts atmosphere-luts)
            (clouds/destroy-cloud-data cloud-data)))))


(when (.exists (io/file ".integration"))
  (fact "Integration test position of Sun relative to Earth at a certain time"
        (let [spk        (make-spk-document "data/astro/de430_1850-2150.bsp")
              sun        (make-spk-segment-interpolator spk 0 10)
              earth-moon (make-spk-segment-interpolator spk 0 3)
              earth      (make-spk-segment-interpolator spk 3 399)
              tdb        2458837.9618055606
              light-time (+ (/ 8 1440) (/ 20 86400))]
          (div (sub (sun (- tdb light-time)) (add (earth-moon tdb) (earth tdb))) AU-KM)
          => (roughly-vector (vec3 -0.034666711163175164, -0.90209322168943, -0.391053015058352) 1e-6))))


(when (.exists (io/file ".integration"))
  (fact "Test Lunar reference frame"
        (let [kern  (read-frame-kernel "data/astro/moon_080317.tf")
              data  (frame-kernel-body-frame-data kern "FRAME_MOON_ME_DE421")
              frame (frame-kernel-body-frame data)]
          frame => (roughly-matrix (mat3x3 +0.999999873254714   -3.2928542237557117E-4  3.808696186713873E-4
                                           +3.29286000210947E-4  0.9999999457843058    -1.4544409378362703E-6
                                           -3.80869119096078E-4  1.5798557868269077E-6  0.9999999274681064) 1e-8))))


(when (.exists (io/file ".integration"))
  (fact "Test Lunar frame at a certain time"
        (let [kern         (read-frame-kernel "data/astro/moon_080317.tf")
              pck          (make-pck-document "data/astro/moon_pa_de421_1900-2050.bpc")
              moon-to-icrs (body-to-icrs kern pck "FRAME_MOON_ME_DE421" 31006)
              tdb          2458837.9618055606
              frame        (moon-to-icrs tdb)]
          (mulv frame (vec3 1737.4 0.0 0.0)) => (roughly-vector (vec3 1692.36463234,  339.93455383,  197.24403743) 1e-3))))


(GLFW/glfwTerminate)
