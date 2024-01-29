(ns sfsim.t-integration
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [clojure.math :refer (to-radians)]
              [clojure.java.io :as io]
              [sfsim.conftest :refer (is-image)]
              [fastmath.vector :refer (vec3)]
              [sfsim.quaternion :as q]
              [sfsim.render :refer :all]
              [sfsim.atmosphere :as atmosphere]
              [sfsim.opacity :as opacity]
              [sfsim.planet :as planet]
              [sfsim.clouds :as clouds]
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

(fact "Integration test rendering of planet, atmosphere, and clouds"
      (if (.exists (io/file ".integration"))
        (with-invisible-window
        (let [width                     320
              height                    240
              level                     5
              position                  (vec3 (+ 300.0 6378000.0) 0 0)
              opacity-base              250.0
              orientation               (q/rotation (to-radians 270) (vec3 0 0 1))
              cloud-data                (clouds/make-cloud-data config/cloud-config)
              atmosphere-luts           (atmosphere/make-atmosphere-luts config/max-height)
              shadow-data               (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
              opacity-renderer          (opacity/make-opacity-renderer :planet-config config/planet-config
                                                                       :shadow-data shadow-data
                                                                       :cloud-data cloud-data)

              planet-shadow-renderer    (planet/make-planet-shadow-renderer :planet-config config/planet-config
                                                                            :shadow-data shadow-data)

              cloud-planet-renderer     (planet/make-cloud-planet-renderer :render-config config/render-config
                                                                           :atmosphere-luts atmosphere-luts
                                                                           :planet-config config/planet-config
                                                                           :shadow-data shadow-data
                                                                           :cloud-data cloud-data)
              cloud-atmosphere-renderer (clouds/make-cloud-atmosphere-renderer :render-config config/render-config
                                                                               :atmosphere-luts atmosphere-luts
                                                                               :planet-config config/planet-config
                                                                               :shadow-data shadow-data
                                                                               :cloud-data cloud-data)

              planet-renderer           (planet/make-planet-renderer :render-config config/render-config
                                                                     :atmosphere-luts atmosphere-luts
                                                                     :planet-config config/planet-config
                                                                     :shadow-data shadow-data)

              atmosphere-renderer       (atmosphere/make-atmosphere-renderer :render-config config/render-config
                                                                             :atmosphere-luts atmosphere-luts
                                                                             :planet-config config/planet-config)
              tree                      (load-tile-tree planet-renderer {} width position level)
              render-vars               (make-render-vars config/planet-config cloud-data config/render-config width height
                                                          position orientation (vec3 1 0 0) 1.0)
              shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                            cloud-data render-vars tree opacity-base)

              clouds                    (texture-render-color-depth width height true
                                          (clear (vec3 0 0 0) 0.0)
                                          (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars :tree tree)
                                          (clouds/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
              tex                       (texture-render-color-depth width height true
                                          (clear (vec3 0 1 0) 0.0)
                                          (planet/render-planet planet-renderer render-vars shadow-vars :clouds clouds :tree tree)
                                          (atmosphere/render-atmosphere atmosphere-renderer render-vars :clouds clouds))]
          (texture->image tex) => (is-image "test/sfsim/fixtures/integration/planet.png" 0.0)
          (destroy-texture tex)
          (destroy-texture clouds)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          ; TODO: unload tile tree
          (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
          (planet/destroy-planet-renderer planet-renderer)
          (clouds/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
          (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
          (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
          (opacity/destroy-opacity-renderer opacity-renderer)
          (atmosphere/destroy-atmosphere-luts atmosphere-luts)
          (clouds/destroy-cloud-data cloud-data)))))

(GLFW/glfwTerminate)
