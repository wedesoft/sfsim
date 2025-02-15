(ns sfsim.t-integration
  (:require
    [clojure.java.io :as io]
    [clojure.math :refer (PI to-radians)]
    [fastmath.matrix :refer (mat3x3 mulm mulv)]
    [fastmath.vector :refer (vec3 add div sub)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.astro :refer :all]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.clouds :as clouds]
    [sfsim.config :as config]
    [sfsim.conftest :refer (roughly-vector roughly-matrix is-image)]
    [sfsim.matrix :refer (transformation-matrix rotation-x rotation-y)]
    [sfsim.model :as model]
    [sfsim.opacity :as opacity]
    [sfsim.planet :as planet]
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
                                                :sfsim.atmosphere/luts atmosphere-luts}
                     opacity-renderer          (opacity/make-opacity-renderer data)
                     planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                     cloud-planet-renderer     (planet/make-cloud-planet-renderer data)
                     cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data)
                     planet-renderer           (planet/make-planet-renderer data)
                     atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                     tree                      (load-tile-tree planet-renderer {} width ?position level)
                     object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
                     render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                               width height ?position ?orientation (vec3 1 0 0))
                     shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                                   cloud-data render-vars tree opacity-base)

                     clouds                    (texture-render-color-depth width height true
                                                                           (clear (vec3 0 0 0) 0.0)
                                                                           (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars tree)
                                                                           (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
                     tex                       (texture-render-color-depth width height true
                                                                           (clear (vec3 0 1 0) 0.0)
                                                                           (planet/render-planet planet-renderer render-vars shadow-vars [] clouds tree)
                                                                           (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))]
                 (texture->image tex) => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 0.0)
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
                                                :sfsim.atmosphere/luts atmosphere-luts}
                     object-position           (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
                     object-to-world           (transformation-matrix (mulm (rotation-y (/ PI 4)) (rotation-x (/ PI 6))) object-position)
                     opacity-renderer          (opacity/make-opacity-renderer data)
                     planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                     cloud-planet-renderer     (planet/make-cloud-planet-renderer data)
                     cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data)
                     planet-renderer           (planet/make-planet-renderer data)
                     atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                     scene-renderer            (model/make-scene-renderer data)
                     model                     (model/read-gltf (str "test/clj/sfsim/fixtures/model/" ?model))
                     object                    (assoc-in (model/load-scene scene-renderer model)
                                                         [:sfsim.model/root :sfsim.model/transform] object-to-world)
                     tree                      (load-tile-tree planet-renderer {} width ?position level)
                     render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                               width height ?position ?orientation (vec3 1 0 0))
                     shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                                   cloud-data render-vars tree opacity-base)
                     clouds                    (texture-render-color-depth width height true
                                                                           (clear (vec3 0 0 0) 0.0)
                                                                           (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars tree)
                                                                           (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
                     tex                       (texture-render-color-depth width height true
                                                                           (clear (vec3 0 1 0) 0.0)
                                                                           (model/render-scenes scene-renderer render-vars shadow-vars [] [object])
                                                                           (planet/render-planet planet-renderer render-vars shadow-vars [] clouds tree)
                                                                           (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))]
                 (texture->image tex) => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 0.0)
                 (destroy-texture tex)
                 (destroy-texture clouds)
                 (opacity/destroy-opacity-and-shadow shadow-vars)
                 (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
                 (model/destroy-scene object)
                 (model/destroy-scene-renderer scene-renderer)
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
                                           :sfsim.atmosphere/luts atmosphere-luts}
                light-direction           (vec3 1 0 0)
                orientation               (q/rotation (to-radians 270) (vec3 0 0 1))
                position                  (vec3 (+ 1.5 6378000.0) 0 0)
                object-radius             1.4
                object-position           (add position (q/rotate-vector orientation (vec3 0 0 -5)))
                object-to-world           (transformation-matrix (mulm (rotation-y (/ PI 6)) (rotation-x (/ PI 6))) object-position)
                opacity-renderer          (opacity/make-opacity-renderer data)
                planet-shadow-renderer    (planet/make-planet-shadow-renderer data)
                cloud-planet-renderer     (planet/make-cloud-planet-renderer data)
                cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data)
                planet-renderer           (planet/make-planet-renderer data)
                atmosphere-renderer       (atmosphere/make-atmosphere-renderer data)
                scene-renderer            (model/make-scene-renderer data)
                scene-shadow-renderer     (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                                            object-radius)
                model                     (model/read-gltf "test/clj/sfsim/fixtures/model/torus.gltf")
                object                    (assoc-in (model/load-scene scene-renderer model)
                                                    [:sfsim.model/root :sfsim.model/transform] object-to-world)
                tree                      (load-tile-tree planet-renderer {} width position level)
                render-vars               (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                          width height position orientation light-direction)
                shadow-vars               (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                              cloud-data render-vars tree opacity-base)
                object-shadow             (model/scene-shadow-map scene-shadow-renderer light-direction object)
                clouds                    (texture-render-color-depth width height true
                                                                      (clear (vec3 0 0 0) 0.0)
                                                                      (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars tree)
                                                                      (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))
                tex                       (texture-render-color-depth width height true
                                                                      (clear (vec3 0 1 0) 0.0)
                                                                      (model/render-scenes scene-renderer render-vars shadow-vars [object-shadow] [object])
                                                                      (planet/render-planet planet-renderer render-vars shadow-vars [object-shadow] clouds tree)
                                                                      (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))]
            (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/torus.png" 0.0)
            (destroy-texture tex)
            (destroy-texture clouds)
            (model/destroy-scene-shadow-map object-shadow)
            (opacity/destroy-opacity-and-shadow shadow-vars)
            (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
            (model/destroy-scene object)
            (model/destroy-scene-shadow-renderer scene-shadow-renderer)
            (model/destroy-scene-renderer scene-renderer)
            (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
            (planet/destroy-planet-renderer planet-renderer)
            (planet/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
            (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
            (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
            (opacity/destroy-opacity-renderer opacity-renderer)
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
