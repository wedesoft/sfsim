;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-integration
  (:require
    [clojure.java.io :as io]
    [clojure.math :refer (PI to-radians)]
    [fastmath.matrix :refer (mat3x3 mulm mulv rotation-matrix-3d-x rotation-matrix-3d-y rotation-matrix-3d-z inverse)]
    [fastmath.vector :refer (vec2 vec3 add div sub normalize)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.astro :refer :all]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.config :as config]
    [sfsim.conftest :refer (roughly-vector roughly-matrix is-image)]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix matrix->quaternion vec4->vec3) :as matrix]
    [sfsim.model :as model]
    [sfsim.graphics :as graphics]
    [sfsim.planet :as planet]
    [sfsim.plume :as plume]
    [sfsim.physics :as physics]
    [sfsim.quadtree :refer :all]
    [sfsim.quaternion :as q]
    [sfsim.render :refer (with-invisible-window render-to-image) :as render]
    [sfsim.texture :as texture]
    [sfsim.image :as image]
    [sfsim.shaders :as shaders]
    [sfsim.bluenoise :as bluenoise]
    [sfsim.shockwave :as shockwave]
    [sfsim.texture :refer :all])
  (:import
    (org.lwjgl.opengl
      GL11 GL14)
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
        (let [width           320
              height          240
              level           5
              light-direction (vec3 1 0 0)
              graphics        (graphics/make-graphics2 [] [])
              tree            (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                     :sfsim.planet/config config/planet-config
                                                     :sfsim.planet/programs [(:sfsim.planet/program
                                                                               (:sfsim.graphics/planet-geometry-renderer graphics))])
                                              {} width ?position level)
              frame           (-> (graphics/make-frame graphics width height ?position ?orientation light-direction []
                                                       (model/make-model-vars 0.0 1.0 0.0))
                                  (graphics/render-shadows graphics tree)
                                  (graphics/render-cloud-geometry graphics tree)
                                  (graphics/render-clouds graphics [])
                                  (graphics/render-geometry graphics tree))]
          (render-to-image width height false
                           (graphics/render-lighting frame graphics))
          => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 1.1)
          (graphics/destroy-frame frame)
          (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
          (graphics/destroy-graphics2 graphics))))
    ?position                      ?orientation                               ?result
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "planet.png"
    (vec3 0 0 (* 1.5 6378000.0))   (q/rotation (to-radians -20) (vec3 0 1 0)) "space.png"))


(when (.exists (io/file ".integration"))
  (tabular "Integration test rendering of object, planet, atmosphere, and clouds"
    (fact
      (with-invisible-window
        (let [width              320
              height             240
              level              5
              object-radius      1.4
              light-direction    (vec3 1 0 0)
              graphics           (graphics/make-graphics2
                                   [{:sfsim.graphics/model-file (str "test/clj/sfsim/fixtures/model/" ?model)
                                     :sfsim.graphics/object-radius object-radius}]
                                   [])
              object-position    (add ?position (q/rotate-vector ?orientation (vec3 0 0 -5)))
              object-orientation (matrix->quaternion (mulm (mulm (rotation-matrix-3d-y (/ PI 4))
                                                                 (rotation-matrix-3d-x (/ PI 6)))
                                                           aerodynamics/gltf-to-aerodynamic))
              model-vars         (model/make-model-vars 0.0 1.0 0.0)
              tree               (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                        :sfsim.planet/config config/planet-config
                                                        :sfsim.planet/programs [(:sfsim.planet/program
                                                                                  (:sfsim.graphics/planet-geometry-renderer graphics))])
                                                 {} width ?position level)
              frame              (-> (graphics/make-frame graphics width height ?position ?orientation light-direction
                                                          [{:sfsim.graphics/object-position object-position
                                                            :sfsim.graphics/object-orientation object-orientation}]
                                                          (model/make-model-vars 0.0 1.0 0.0))
                                     (graphics/render-shadows graphics tree)
                                     (graphics/render-cloud-geometry graphics tree)
                                     (graphics/render-clouds graphics [])
                                     (graphics/render-geometry graphics tree))]
          (render-to-image width height false
                           (graphics/render-lighting frame graphics))
          => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 1.1)
          (graphics/destroy-frame frame)
          (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
          (graphics/destroy-graphics2 graphics))))
    ?position                      ?orientation                               ?model        ?result
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "cube.glb"    "cube.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "dice.gltf"   "dice.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bump.gltf"   "bump.png"
    (vec3 (+ 300.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "bricks.gltf" "bricks.png"))


(when (.exists (io/file ".integration"))
  (fact "Integration test rendering of model self-shadowing"
    (with-invisible-window
      (let [width              320
            height             240
            level              5
            object-radius      1.4
            light-direction    (vec3 1 0 0)
            graphics           (graphics/make-graphics2
                                 [{:sfsim.graphics/model-file "test/clj/sfsim/fixtures/model/torus.gltf"
                                   :sfsim.graphics/object-radius object-radius}]
                                 [])
            position           (vec3 (+ 1.5 6378000.0) 0 0)
            orientation        (q/rotation (to-radians 270) (vec3 0 0 1))
            object-position    (add position (q/rotate-vector orientation (vec3 0 0 -5)))
            object-orientation (matrix->quaternion (mulm (mulm (rotation-matrix-3d-z (/ PI 6))
                                                               (rotation-matrix-3d-x (/ PI 6)))
                                                         aerodynamics/gltf-to-aerodynamic))
            tree               (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                      :sfsim.planet/config config/planet-config
                                                      :sfsim.planet/programs [(:sfsim.planet/program
                                                                                (:sfsim.graphics/planet-geometry-renderer graphics))])
                                               {} width position level)
            frame              (-> (graphics/make-frame graphics width height position orientation light-direction
                                                        [{:sfsim.graphics/object-position object-position
                                                          :sfsim.graphics/object-orientation object-orientation}]
                                                        (model/make-model-vars 0.0 1.0 0.0))
                                   (graphics/render-shadows graphics tree)
                                   (graphics/render-scene-shadows graphics)
                                   (graphics/render-cloud-geometry graphics tree)
                                   (graphics/render-clouds graphics [])
                                   (graphics/render-geometry graphics tree))]
        (render-to-image width height false
                         (graphics/render-lighting frame graphics))
        => (is-image "test/clj/sfsim/fixtures/integration/torus.png" 0.5)
        (graphics/destroy-frame frame)
        (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
        (graphics/destroy-graphics2 graphics)))))


(when (.exists (io/file ".integration"))
  (tabular "Test rendering of model"
    (fact
      (with-invisible-window
        (let [width              320
              height             240
              level              5
              object-radius      (:sfsim.model/object-radius config/model-config)
              light-direction    (vec3 1 0 0)
              graphics           (graphics/make-graphics2
                                   [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                     :sfsim.graphics/object-radius object-radius}]
                                   [])
              object-position    (add ?position (q/rotate-vector ?orientation (vec3 0 0 -50)))
              object-orientation (matrix->quaternion (mulm (mulm (rotation-matrix-3d-x (/ PI 6))
                                                                 (rotation-matrix-3d-y (/ PI -2)))
                                                           aerodynamics/gltf-to-aerodynamic))
              tree               (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                        :sfsim.planet/config config/planet-config
                                                        :sfsim.planet/programs [(:sfsim.planet/program
                                                                                  (:sfsim.graphics/planet-geometry-renderer graphics))])
                                                 {} width ?position level)
              frame              (-> (graphics/make-frame graphics width height ?position ?orientation light-direction
                                                          [{:sfsim.graphics/object-position object-position
                                                            :sfsim.graphics/object-orientation object-orientation}]
                                                          (model/make-model-vars 0.0 1.0 0.0))
                                     (graphics/render-shadows graphics tree)
                                     (graphics/render-scene-shadows graphics)
                                     (graphics/render-cloud-geometry graphics tree)
                                     (graphics/render-clouds graphics [])
                                     (graphics/render-geometry graphics tree))]
          (render-to-image width height false
                           (graphics/render-lighting frame graphics))
          => (is-image (str "test/clj/sfsim/fixtures/integration/" ?result) 0.5)
          (graphics/destroy-frame frame)
          (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
          (graphics/destroy-graphics2 graphics))))
    ?position                      ?orientation                               ?result
    (vec3 (+ 100.0 6378000.0) 0 0) (q/rotation (to-radians 270) (vec3 0 0 1)) "model.png"
    (vec3 0 0 (* 1.5 6378000.0))   (q/rotation (to-radians -20) (vec3 0 1 0)) "two-projections.png"))


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
                program (render/make-program :sfsim.render/vertex [vertex-plume]
                                             :sfsim.render/fragment [fragment-plume (plume/sample-plume-segment true)])
                vao     (render/make-vertex-array-object program indices vertices ["point" 3])]
            (render/use-program program)
            (render/uniform-vector2 program "resolution" (vec2 width height))
            (render/uniform-float program "plume_nozzle" 2.7549)
            (render/uniform-float program "plume_min_limit" 1.2)
            (render/uniform-float program "plume_max_slope" 1.0)
            (render/uniform-float program "omega_factor" 0.2)
            (render/uniform-float program "diamond_strength" 0.2)
            (render/uniform-float program "plume_step" 0.2)
            (render/uniform-float program "pressure" 1.0)
            (render/uniform-float program "plume_throttle" 1.0)
            (render/uniform-float program "time" 0.0)
            (let [tex (render/texture-render-color width height true (render/clear (vec3 0 0 0)) (render/render-quads vao))]
              (texture->image tex) => (is-image "test/clj/sfsim/fixtures/integration/plume.png" 0.0)
              (destroy-texture tex))
            (render/destroy-vertex-array-object vao)
            (render/destroy-program program)))))


(when (.exists (io/file ".integration"))
  (fact "Test rendering of model with main engine plume and RCS thrusters"
    (with-invisible-window
      (let [width               320
            height              240
            level               5
            object-radius       (:sfsim.model/object-radius config/model-config)
            light-direction     (vec3 1 0 0)
            graphics            (graphics/make-graphics2
                                  [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                    :sfsim.graphics/object-radius object-radius}]
                                  [])
            position            (vec3 (+ 100.0 6378000.0) 0 0)
            orientation         (q/rotation (to-radians 270) (vec3 0 0 1))
            object-position     (add position (q/rotate-vector orientation (vec3 0 0 -50)))
            object-orientation  (matrix->quaternion (mulm (mulm (rotation-matrix-3d-x (* 0.8 PI))
                                                                (rotation-matrix-3d-y (* -0.4 PI)))
                                                          aerodynamics/gltf-to-aerodynamic))
            tree                (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                       :sfsim.planet/config config/planet-config
                                                       :sfsim.planet/programs [(:sfsim.planet/program
                                                                                 (:sfsim.graphics/planet-geometry-renderer graphics))])
                                                {} width position level)
            frame               (-> (graphics/make-frame graphics width height position orientation light-direction
                                                         [{:sfsim.graphics/object-position object-position
                                                           :sfsim.graphics/object-orientation object-orientation}]
                                                         (model/make-model-vars 0.0 1.0 0.5))
                                    (graphics/render-shadows graphics tree)
                                    (graphics/render-scene-shadows graphics)
                                    (graphics/render-cloud-geometry graphics tree)
                                    (graphics/render-clouds graphics (physics/all-rcs))
                                    (graphics/render-geometry graphics tree))]
        (render-to-image width height false
                         (graphics/render-lighting frame graphics))
        => (is-image "test/clj/sfsim/fixtures/integration/model-with-plume.png" 0.5)
        (graphics/destroy-frame frame)
        (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
        (graphics/destroy-graphics2 graphics)))))


(def fragment-texture-2d
"#version 450 core
uniform int size;
uniform float scale;
uniform float shockwave_radius;
uniform sampler2D flood;
out vec3 fragColor;
float shockfront(float distance, float Rn);
void main()
{
  vec2 uv_fragment = gl_FragCoord.xy / size;
  vec4 point = texture(flood, uv_fragment);
  float depth = (point.z + shockfront(length(point.xy - gl_FragCoord.xy * scale), point.w)) / shockwave_radius;
  fragColor = vec3(depth);
}")


(when (.exists (io/file ".integration"))
  (fact "Test shockwave shape estimation"
        (with-invisible-window
          (let [size                 256
                object-radius        1.4
                shockwave-radius     (* 5 1.4)
                max-curvature-radius 3.0
                M                    10.0
                wind-from            (vec3 1 0 0)
                graphics             (graphics/make-graphics2
                                       [{:sfsim.graphics/model-file (str "test/clj/sfsim/fixtures/model/cube.glb")
                                         :sfsim.graphics/object-radius object-radius}]
                                       [])
                vertices             [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                indices              [0 1 3 2]
                program-display      (render/make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                                          :sfsim.render/fragment [shockwave/shockfront fragment-texture-2d])
                vao-display          (render/make-vertex-array-object program-display indices vertices ["point" 3])
                wind-shadow          (model/scene-shadow-map (:sfsim.graphics/scene-shadow-renderer graphics)
                                                             wind-from
                                                             (first (:sfsim.graphics/scenes graphics))
                                                             size
                                                             shockwave-radius
                                                             :sfsim.render/cullback
                                                             true)
                shockwave-renderer   (shockwave/make-shockwave-renderer shockwave/depth-source shockwave/normal-source
                                                                        shockwave/shockfront size shockwave-radius
                                                                        max-curvature-radius)
                flood                (shockwave/jump-flooding-algorithm shockwave-renderer wind-shadow M)]
            (render-to-image size size false
                             (render/clear (vec3 0 1 0) 0.0)
                             (render/use-program program-display)
                             (render/uniform-sampler program-display "flood" 0)
                             (render/uniform-int program-display "size" size)
                             (render/uniform-float program-display "mach" M)
                             (render/uniform-float program-display "scale" (/ (* 2.0 shockwave-radius) size))
                             (render/uniform-float program-display "max_curvature_radius" max-curvature-radius)
                             (render/uniform-float program-display "shockwave_radius" shockwave-radius)
                             (render/use-textures {0 flood})
                             (render/render-quads vao-display))
            => (is-image (str "test/clj/sfsim/fixtures/integration/jump-flooding-algorithm.png") 1.0)
            (destroy-texture flood)
            (model/destroy-scene-shadow-map wind-shadow)
            (shockwave/destroy-shockwave-renderer shockwave-renderer)
            (render/destroy-vertex-array-object vao-display)
            (render/destroy-program program-display)
            (graphics/destroy-graphics2 graphics)))))


(def shockwave-indices
  [4 5 7 6    ; front (+z)
   1 0 2 3    ; back  (-z)
   0 4 6 2    ; left  (-x)
   5 1 3 7    ; right (+x)
   2 6 7 3    ; top   (+y)
   0 1 5 4])  ; bottom (-y)


(def shockwave-vertices
  [-1.0 -1.0  0.0
    1.0 -1.0  0.0
   -1.0  1.0  0.0
    1.0  1.0  0.0
   -1.0 -1.0  1.0
    1.0 -1.0  1.0
   -1.0  1.0  1.0
    1.0  1.0  1.0])


(when (.exists (io/file ".integration"))
  (fact "Test rendering of model with shockwave"
        (render/with-invisible-window
          (let [width                320
                height               240
                size                 1024
                level                5
                dist                 (+ 60000.0 6378000.0)
                offset               100
                origin               (vec3 0 0 dist)
                orientation          (q/rotation (to-radians 90.0) (vec3 1 0 0))
                max-curvature-radius 3.0
                mach                 10.0
                light                (normalize (vec3 1 1 1))
                wind-from            (vec3 1 0 0)
                object-orientation   (matrix/matrix->quaternion (mulm (rotation-matrix-3d-y (* -0.15 PI))
                                                                      (rotation-matrix-3d-x (* 0.5 PI))))
                model-vars           (model/make-model-vars 0.0 0.0 0.0)
                shockwave-radius     (* 2.0 (:sfsim.model/object-radius config/model-config))
                graphics             (graphics/make-graphics2 [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                                                :sfsim.graphics/object-radius (:sfsim.model/object-radius config/model-config)}]
                                                              [])
                model                (first (:sfsim.graphics/scenes graphics))
                model-gears          (model/apply-transforms
                                       model (model/animations-frame model
                                                                     {"GearLeft" 2.0 "GearRight" 2.0 "GearFront" 3.0}))
                graphics             (assoc-in graphics [:sfsim.graphics/scenes 0] model-gears)
                shockwave-renderer   (shockwave/make-shockwave-renderer shockwave/depth-source shockwave/normal-source shockwave/shockfront
                                                                        size shockwave-radius max-curvature-radius)
                program-shockwave     (render/make-program :sfsim.render/vertex [shockwave/vertex-shockwave]
                                                           :sfsim.render/fragment [shaders/ray-box shockwave/shockfront
                                                                                   shockwave/fragment-shockwave
                                                                                   bluenoise/sampling-offset])
                vao-shockwave        (render/make-vertex-array-object program-shockwave shockwave-indices shockwave-vertices ["point" 3])
                tree                 (load-tile-tree (assoc (:sfsim.graphics/planet-geometry-renderer graphics)
                                                            :sfsim.planet/config config/planet-config
                                                            :sfsim.planet/programs [(:sfsim.planet/program
                                                                                      (:sfsim.graphics/planet-geometry-renderer graphics))])
                                                     {} width origin level)
                object               [{:sfsim.graphics/object-position (add origin (q/rotate-vector orientation (vec3 0 0 (- offset))))
                                       :sfsim.graphics/object-orientation object-orientation}]
                frame                (-> (graphics/make-frame graphics width height origin orientation
                                                              light object model-vars)
                                         (graphics/render-shadows graphics tree)
                                         (graphics/render-scene-shadows graphics)
                                         (graphics/render-cloud-geometry graphics tree)
                                         (graphics/render-clouds graphics [])
                                         (graphics/render-geometry graphics tree))
                wind-shadow          (model/scene-shadow-map (:sfsim.graphics/scene-shadow-renderer graphics)
                                                             wind-from
                                                             (first (graphics/get-moved-scenes frame graphics))
                                                             size
                                                             shockwave-radius
                                                             :sfsim.render/cullback
                                                             true)
                projection           (:sfsim.render/overlay-projection (:sfsim.graphics/cloud-render-vars frame))
                matrices             (:sfsim.model/matrices wind-shadow)
                camera-to-world      (matrix/transformation-matrix (matrix/quaternion->matrix orientation) origin)
                world-to-object      (:sfsim.matrix/world-to-object matrices)
                object-to-shadow-ndc (:sfsim.matrix/object-to-shadow-ndc matrices)
                camera-to-ndc        (mulm object-to-shadow-ndc (mulm world-to-object camera-to-world))
                ndc-to-camera        (inverse camera-to-ndc)]
            ;; Perform Jump Flooding Algorithm
            (let [flood (shockwave/jump-flooding-algorithm shockwave-renderer wind-shadow mach)
                  bluenoise (:sfsim.clouds/bluenoise (:sfsim.clouds/data graphics))]
              (render/framebuffer-render (/ width 2) (/ height 2) :sfsim.render/noculling nil [(:sfsim.graphics/clouds frame)]
                                         (render/use-program program-shockwave)
                                         (render/uniform-sampler program-shockwave "points" 0)
                                         (render/uniform-sampler program-shockwave "flood" 1)
                                         (render/uniform-sampler program-shockwave "bluenoise" 2)
                                         (render/uniform-int program-shockwave "width" (/ width 2))
                                         (render/uniform-int program-shockwave "height" (/ height 2))
                                         (render/uniform-int program-shockwave "noise_size" (:sfsim.texture/width bluenoise))
                                         (render/uniform-float program-shockwave "shockwave_radius" shockwave-radius)
                                         (render/uniform-float program-shockwave "scale" (/ (* 2.0 shockwave-radius) size))
                                         (render/uniform-float program-shockwave "step" 0.01)
                                         (render/uniform-float program-shockwave "mach" mach)
                                         (render/uniform-matrix4 program-shockwave "projection" projection)
                                         (render/uniform-matrix4 program-shockwave "ndc_to_camera" ndc-to-camera)
                                         (render/uniform-matrix4 program-shockwave "camera_to_ndc" camera-to-ndc)
                                         (render/use-textures {0 (:sfsim.clouds/points (:sfsim.graphics/cloud-geometry frame))
                                                               1 flood
                                                               2 bluenoise})
                                         (render/render-quads vao-shockwave)
                                         (texture/destroy-texture flood)))
                     ;; Compose render of model
                     (render/render-to-image width height false
                                             (render/clear (vec3 0 1 0) 0.0)
                                             (graphics/render-lighting frame graphics))
                     => (is-image (str "test/clj/sfsim/fixtures/integration/model-with-shockwave.png") 1.0)
            (model/destroy-scene-shadow-map wind-shadow)
            (graphics/destroy-frame frame)
            (render/destroy-vertex-array-object vao-shockwave)
            (planet/unload-tiles-from-opengl (quadtree-extract tree (tiles-path-list tree)))
            (graphics/destroy-graphics2 graphics)
            (render/destroy-program program-shockwave)
            (shockwave/destroy-shockwave-renderer shockwave-renderer)))))


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
