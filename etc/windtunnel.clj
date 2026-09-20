(require '[clojure.math :refer (PI to-radians)]
         '[fastmath.vector :refer (vec3 normalize add)]
         '[fastmath.matrix :refer (mulm inverse rotation-matrix-3d-x rotation-matrix-3d-y)]
         '[sfsim.config :as config]
         '[sfsim.quaternion :as q]
         '[sfsim.matrix :as matrix]
         '[sfsim.model :as model]
         '[sfsim.planet :as planet]
         '[sfsim.render :as render]
         '[sfsim.image :as image]
         '[sfsim.quadtree :as quadtree]
         '[sfsim.shaders :as shaders]
         '[sfsim.bluenoise :as bluenoise]
         '[sfsim.texture :as texture]
         '[sfsim.shockwave :refer (shockfront make-shockwave-renderer jump-flooding-algorithm destroy-shockwave-renderer
                                   normal-source depth-source)]
         '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL])

(GLFW/glfwInit)

(def width 320)
(def height 240)
(def size 512)
(def level 5)

(defn load-tile-tree
  [planet-renderer tree width position n]
  (if (zero? n)
    tree
    (let [data (planet/background-tree-update planet-renderer tree width position)
          tree (planet/load-tiles-into-opengl planet-renderer (:tree data) (:load data))]
      (load-tile-tree planet-renderer tree width position (dec n)))))



(def vertex-shockwave
"#version 450 core
uniform mat4 projection;
uniform mat4 ndc_to_camera;
in vec3 point;
void main()
{
  gl_Position = projection * ndc_to_camera * vec4(point, 1);
}")

(def fragment-shockwave
"#version 450 core
uniform sampler2D points;
uniform sampler2D flood;
uniform mat4 camera_to_ndc;
uniform float shockwave_radius;
uniform float scale;
uniform int width;
uniform int height;
uniform float step;
out vec4 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float shockfront(float distance, float Rn);
float sampling_offset();
void main()
{
  vec2 uv = gl_FragCoord.xy / vec2(width, height);
  vec3 origin = (camera_to_ndc * vec4(0, 0, 0, 1)).xyz;
  vec4 point = texture(points, uv);
  vec3 direction = (camera_to_ndc * vec4(point.xyz, 0)).xyz;
  direction = normalize(direction * vec3(1, 1, 2)) / vec3(1, 1, 2);
  vec2 segment = ray_box(vec3(-1, -1, 0), vec3(1, 1, 1), origin, direction);
  if (point.w > 0.0) {
    vec4 surface = camera_to_ndc * point;
    float dist = length(surface.xyz - origin) / length(direction);
    if (segment.x + segment.y > dist) {
      segment.y = dist - segment.x;
    };
  };
  float emission = 0.0;
  float x = segment.x + step * sampling_offset();
  while (x < segment.x + segment.y) {
    vec3 p = origin + x * direction;
    vec4 point = texture(flood, (p.xy + 1.0) / 2.0);
    float l = length(point.xy - (p.xy + 1.0) * shockwave_radius);
    float depth = point.z + shockfront(l, point.w);
    if (p.z * shockwave_radius <= depth) {
      emission += 4.0 * step * exp(1.0 * (p.z * shockwave_radius - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, l));
    };
    x += step;
  };
  fragColor = vec4(vec3(emission, emission, 0.0), 0.0);
}")


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


(render/with-invisible-window
  (let [dist                 (+ 600000.0 6378000.0)
        offset               100
        origin               (vec3 0 0 dist)
        orientation          (q/rotation (to-radians 90.0) (vec3 1 0 0))
        max-curvature-radius 3.0
        mach                 10.0
        light                (normalize (vec3 1 1 1))
        wind-from            (vec3 1 0 0)
        object-orientation   (matrix/matrix->quaternion (mulm (rotation-matrix-3d-y (* -0.15 PI))
                                                              (rotation-matrix-3d-x (* 0.5 PI))))
        model-vars           (model/make-model-vars (GLFW/glfwGetTime) 0.0 0.0)
        shockwave-radius     (* 2.0 (:sfsim.model/object-radius config/model-config))
        graphics             (graphics/make-graphics2 [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                                        :sfsim.graphics/object-radius (:sfsim.model/object-radius config/model-config)}]
                                                      [])
        model                (first (:sfsim.graphics/scenes graphics))
        model-gears          (model/apply-transforms
                               model (model/animations-frame model
                                                             {"GearLeft" 2.0 "GearRight" 2.0 "GearFront" 3.0}))
        graphics             (assoc-in graphics [:sfsim.graphics/scenes 0] model-gears)
        shockwave-renderer   (make-shockwave-renderer depth-source normal-source shockfront size shockwave-radius max-curvature-radius)
        program-shockwave     (render/make-program :sfsim.render/vertex [vertex-shockwave]
                                                   :sfsim.render/fragment [shaders/ray-box shockfront fragment-shockwave bluenoise/sampling-offset])
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
                                                     shockwave-radius
                                                     :sfsim.render/cullback
                                                     true)  ;; TODO: render smaller wind shadow
        projection           (:sfsim.render/overlay-projection (:sfsim.graphics/cloud-render-vars frame))
        matrices             (:sfsim.model/matrices wind-shadow)
        camera-to-world      (matrix/transformation-matrix (matrix/quaternion->matrix orientation) origin)
        world-to-object      (:sfsim.matrix/world-to-object matrices)
        object-to-shadow-ndc (:sfsim.matrix/object-to-shadow-ndc matrices)
        camera-to-ndc        (mulm object-to-shadow-ndc (mulm world-to-object camera-to-world))
        ndc-to-camera        (inverse camera-to-ndc)]
    ;; Perform Jump Flooding Algorithm
    (let [flood (jump-flooding-algorithm shockwave-renderer wind-shadow mach)
          bluenoise (:sfsim.clouds/bluenoise (:sfsim.clouds/data graphics))]
      ;; Render shockwave  TODO: don't overwrite clouds
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
                                 (render/render-quads vao-shockwave))
      ;; Compose render of model
      (image/spit-png "/tmp/test.png"
                      (render/render-to-image width height false
                                              (render/clear (vec3 0 1 0) 0.0)
                                              (graphics/render-lighting frame graphics)) true)
      (texture/destroy-texture flood))
    (model/destroy-scene-shadow-map wind-shadow)
    (graphics/destroy-frame frame)
    (render/destroy-vertex-array-object vao-shockwave)
    (graphics/destroy-graphics2 graphics)
    (render/destroy-program program-shockwave)
    (destroy-shockwave-renderer shockwave-renderer)
    (planet/unload-tiles-from-opengl (quadtree/quadtree-extract tree (quadtree/tiles-path-list tree)))))


(GLFW/glfwTerminate)
