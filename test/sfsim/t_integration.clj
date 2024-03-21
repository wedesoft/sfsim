(ns sfsim.t-integration
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [clojure.math :refer (PI to-radians)]
              [clojure.java.io :as io]
              [sfsim.conftest :refer (is-image)]
              [fastmath.vector :refer (vec3 add)]
              [fastmath.matrix :refer (mulm)]
              [sfsim.matrix :refer (transformation-matrix rotation-x rotation-y)]
              [sfsim.quaternion :as q]
              [sfsim.quadtree :refer :all]
              [sfsim.texture :refer :all]
              [sfsim.render :refer :all]
              [sfsim.atmosphere :as atmosphere]
              [sfsim.opacity :as opacity]
              [sfsim.planet :as planet]
              [sfsim.clouds :as clouds]
              [sfsim.model :as model]
              [sfsim.config :as config])
    (:import [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.render]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(defn load-tile-tree
  [planet-renderer tree width position n]
  (if (zero? n)
    tree
    (let [data (planet/background-tree-update planet-renderer tree width position)
          tree (planet/load-tiles-into-opengl planet-renderer (:tree data) (:load data))]
      (load-tile-tree planet-renderer tree width position (dec n)))))

(if (.exists (io/file ".integration"))
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
                                         :sfsim.atmosphere/luts atmosphere-luts}
              opacity-renderer          (opacity/make-opacity-renderer data)
              planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
              cloud-planet-renderer     (planet/make-cloud-planet-renderer data)
              cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data)
              planet-renderer           (planet/make-planet-renderer data)
              atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
              tree                      (load-tile-tree planet-renderer {} width ?position level)
              object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
              render-vars               (make-render-vars config/planet-config cloud-data config/render-config width height
                                                          ?position ?orientation (vec3 1 0 0) 1.0)
              shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                            cloud-data render-vars tree opacity-base)

              clouds                    (texture-render-color-depth width height true
                                          (clear (vec3 0 0 0) 0.0)
                                          (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars tree)
                                          (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
              tex                       (texture-render-color-depth width height true
                                          (clear (vec3 0 1 0) 0.0)
                                          (planet/render-planet planet-renderer render-vars shadow-vars clouds tree)
                                          (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))]
          (texture->image tex) => (is-image (str "test/sfsim/fixtures/integration/" ?result) 0.0)
          (destroy-texture tex)
          (destroy-texture clouds)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
          (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
          (planet/destroy-planet-renderer planet-renderer)
          (planet/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
          (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
          (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
          (opacity/destroy-opacity-renderer opacity-renderer)
          (atmosphere/destroy-atmosphere-luts atmosphere-luts)
          (clouds/destroy-cloud-data cloud-data))))
    ?position                      ?orientation                               ?result
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "planet.png"
    (vec3 0 0 (* 1.5 6378000.0))   (q/rotation (to-radians -20) (vec3 0 1 0)) "space.png"))

(if (.exists (io/file ".integration"))
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
                                         :sfsim.atmosphere/luts atmosphere-luts}
              object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
              object-to-world           (transformation-matrix (mulm (rotation-y (/ PI 4)) (rotation-x (/ PI 6))) object-position)
              opacity-renderer          (opacity/make-opacity-renderer data)
              planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
              cloud-planet-renderer     (planet/make-cloud-planet-renderer data)
              cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data)
              planet-renderer           (planet/make-planet-renderer data)
              atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
              model-renderer            (model/make-model-renderer data)
              object                    (assoc-in (model/load-scene model-renderer (str "test/sfsim/fixtures/model/" ?model))
                                                  [:sfsim.model/root :sfsim.model/transform] object-to-world)
              tree                      (load-tile-tree planet-renderer {} width ?position level)
              render-vars               (make-render-vars config/planet-config cloud-data config/render-config width height
                                                          ?position ?orientation (vec3 1 0 0) 1.0)
              shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                            cloud-data render-vars tree opacity-base)

              clouds                    (texture-render-color-depth width height true
                                          (clear (vec3 0 0 0) 0.0)
                                          (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars tree)
                                          (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
              tex                       (texture-render-color-depth width height true
                                          (clear (vec3 0 1 0) 0.0)
                                          (model/render-models model-renderer render-vars shadow-vars [object])
                                          (planet/render-planet planet-renderer render-vars shadow-vars clouds tree)
                                          (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))]
          (texture->image tex) => (is-image (str "test/sfsim/fixtures/integration/" ?result) 0.0)
          (destroy-texture tex)
          (destroy-texture clouds)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
          (model/destroy-scene object)
          (model/destroy-model-renderer model-renderer)
          (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
          (planet/destroy-planet-renderer planet-renderer)
          (planet/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
          (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
          (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
          (opacity/destroy-opacity-renderer opacity-renderer)
          (atmosphere/destroy-atmosphere-luts atmosphere-luts)
          (clouds/destroy-cloud-data cloud-data))))
    ?position                      ?orientation                               ?model        ?result
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "cube.gltf"   "cube.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "dice.gltf"   "dice.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bump.gltf"   "bump.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bricks.gltf" "bricks.png"))

(GLFW/glfwTerminate)
