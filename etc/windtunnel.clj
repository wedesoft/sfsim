(require '[clojure.math :refer (PI to-radians exp)]
         '[fastmath.vector :refer (vec3 normalize)]
         '[fastmath.matrix :refer (mulm inverse)]
         '[sfsim.config :as config]
         '[sfsim.quaternion :as q]
         '[sfsim.matrix :as matrix]
         '[sfsim.model :as model]
         '[sfsim.render :as render]
         '[sfsim.shaders :as shaders]
         '[sfsim.bluenoise :as bluenoise]
         '[sfsim.texture :as texture]
         '[sfsim.shockwave :refer (shockfront)]
         '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL30])

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(def width (- 2560 512))
(def height 1440)
(def size 1024)
(def wsize 512)
(GLFW/glfwWindowHint GLFW/GLFW_DECORATED GLFW/GLFW_FALSE)
(def window (GLFW/glfwCreateWindow width height "Windtunnel" 0 0))
(GLFW/glfwSwapInterval 1)
(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def window2 (GLFW/glfwCreateWindow wsize wsize "Wind-Shadow" 0 window))
(GLFW/glfwMakeContextCurrent window2)
(GLFW/glfwShowWindow window2)
(GL/createCapabilities)

(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window xpos ypos]
      (reset! mouse-pos [xpos (- height ypos 1)]))))

(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window _button action _mods]
      (reset! mouse-button (= action GLFW/GLFW_PRESS)))))

(GLFW/glfwMakeContextCurrent window)
(def object-radius (* 2.0 (:sfsim.model/object-radius config/model-config)))
(def graphics (graphics/make-graphics2 [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                         :sfsim.graphics/object-radius object-radius}]
                                       []))


(def vertex-texture
"#version 450 core
in vec3 point;
in vec2 uv;
out vec2 uv_fragment;
void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}")


(def max-radius 1.2)
(def M 10.0)


(def curvature
"#version 450 core
uniform int size;
uniform float object_radius;
uniform float max_radius;
float curvature(vec4 N)
{
  float h = (2.0 * object_radius) / size;
  vec3 dNdx = dFdx(N.xyz) / h;
  vec3 dNdy = dFdy(N.xyz) / h;
  float k = sqrt(max(dot(dNdx, dNdx), dot(dNdy, dNdy)));
  float scale_radius = N.z * N.z;
  return scale_radius / max(k, 1.0 / max_radius);
}")


(def cot
"#version 450 core
float cot(float x)
{
  return cos(x) / sin(x);
}")


(def fragment-texture-2d
"#version 450 core
uniform int size;
uniform float object_radius;
uniform sampler2D flood;
uniform sampler2D normals;
uniform sampler2D wind;
uniform float max_radius;
in vec2 uv_fragment;
out vec3 fragColor;
float shockfront(float distance, float Rn);
void main()
{
  vec2 uv_fragment = gl_FragCoord.xy / size;
  vec4 point = texture(flood, uv_fragment);
  float depth = point.z + shockfront(object_radius * length(point.xy - uv_fragment), point.w) / (2 * object_radius);
  vec4 N = texture(normals, uv_fragment);
  float c = N.z;
  if (texture(wind, uv_fragment).r > 0.0)
    fragColor = vec3(c, c, 0);
  else
    fragColor = vec3(0.0, depth, depth);
}")

;; https://en.wikipedia.org/wiki/Jump_flooding_algorithm

(def fragment-init
"#version 450 core
uniform sampler2D depth;
uniform sampler2D normals;
uniform int size;
uniform float max_radius;
in vec2 uv_fragment;
layout (location = 0) out vec4 point;
float curvature(vec4 N);
void main()
{
  float d = texture(depth, uv_fragment).r;
  vec4 N = texture(normals, uv_fragment);
  float Rn = curvature(N);
  point = vec4(gl_FragCoord.xy / size, d, Rn);
}")


(def fragment-jump-flooding
"#version 450 core
in vec2 uv_fragment;
uniform sampler2D flood;
uniform sampler2D normals;
uniform int step;
uniform int size;
uniform float object_radius;
layout (location = 0) out vec4 point;
float shockfront(float distance, float Rn);
vec4 nearest(vec4 result, vec2 uv_fragment, vec2 dpos)
{
  vec4 point = texture(flood, uv_fragment + dpos);
  float current = result.z + shockfront(object_radius * length(result.xy - uv_fragment), result.w) / (2 * object_radius);
  float candidate = point.z + shockfront(object_radius * length(point.xy - uv_fragment), point.w) / (2 * object_radius);
  if (candidate > current)
    return point;
  else
    return result;
}
void main()
{
  float delta = float(step) / size;
  vec2 uv_fragment = gl_FragCoord.xy / size;
  vec4 result = texture(flood, uv_fragment);
  result = nearest(result, uv_fragment, vec2(-delta, -delta));
  result = nearest(result, uv_fragment, vec2(     0, -delta));
  result = nearest(result, uv_fragment, vec2(+delta, -delta));
  result = nearest(result, uv_fragment, vec2(-delta,      0));
  result = nearest(result, uv_fragment, vec2(     0,      0));
  result = nearest(result, uv_fragment, vec2(+delta,      0));
  result = nearest(result, uv_fragment, vec2(-delta, +delta));
  result = nearest(result, uv_fragment, vec2(     0, +delta));
  result = nearest(result, uv_fragment, vec2(+delta, +delta));
  point = result;
}")


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
uniform float object_radius;
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
    vec2 uv_fragment = p.xy * 0.5 + 0.5;
    vec4 point = texture(flood, uv_fragment);
    float l = length(point.xy - uv_fragment);
    float depth = point.z + shockfront(object_radius * l, point.w) / (2 * object_radius);
    if (p.z <= depth) {
      emission += 4.0 * step * exp(100.0 * (p.z - depth)) * (1.0 - smoothstep(0.0, 0.3, l));
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


(GLFW/glfwMakeContextCurrent window)
(def program-shockwave (render/make-program :sfsim.render/vertex [vertex-shockwave]
                                            :sfsim.render/fragment [shaders/ray-box shockfront fragment-shockwave bluenoise/sampling-offset]))
(def vao-shockwave (render/make-vertex-array-object program-shockwave shockwave-indices shockwave-vertices ["point" 3]))

(def vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0])
(def indices [0 1 3 2])

(def program-init (render/make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-init curvature]))
(def vao-init (render/make-vertex-array-object program-init indices vertices ["point" 3 "uv" 2]))
(def program-jump-flooding (render/make-program :sfsim.render/vertex [vertex-texture]
                                                :sfsim.render/fragment [shockfront fragment-jump-flooding]))
(def vao-jump-flooding (render/make-vertex-array-object program-jump-flooding indices vertices ["point" 3 "uv" 2]))

(defn jump-flooding-step
  [normals flood step]
  (let [result (texture/make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
    (render/framebuffer-render size size :sfsim.render/noculling nil [result]
                               (render/use-program program-jump-flooding)
                               (render/uniform-sampler program-jump-flooding "flood" 0)
                               (render/uniform-sampler program-jump-flooding "normals" 1)
                               (render/uniform-int program-jump-flooding "size" size)
                               (render/uniform-int program-jump-flooding "step" step)
                               (render/uniform-float program-jump-flooding "object_radius" object-radius)
                               (render/uniform-float program-jump-flooding "mach" M)
                               (render/use-textures {0 flood 1 normals})
                               (render/render-quads vao-jump-flooding))
    (texture/destroy-texture flood)
    result))

(GLFW/glfwMakeContextCurrent window2)
(def program-display  (render/make-program :sfsim.render/vertex [vertex-texture]
                                           :sfsim.render/fragment [shockfront fragment-texture-2d]))
(def vao-display (render/make-vertex-array-object program-display indices vertices ["point" 3 "uv" 2]))

(while (and (not (GLFW/glfwWindowShouldClose window)) (not (GLFW/glfwWindowShouldClose window2)))
       (GLFW/glfwMakeContextCurrent window)
       (let [dist                 (* 2 6378000)
             origin               (vec3 dist 0 150)
             orientation          (q/->Quaternion 1 0 0 0)
             light                (normalize (vec3 1 1 1))
             wind-from            (q/rotate-vector (q/rotation (to-radians -60.0) (vec3 0 1 0)) (vec3 1 0 0))
             yaw                  (* 4 PI (/ (@mouse-pos 0) (double width)))
             pitch                (* PI (- (/ (@mouse-pos 1) (double height)) 0.5))
             obj-orient           (q/* (q/rotation yaw (vec3 0 1 0)) (q/rotation pitch (vec3 0 0 1)))
             model-vars           (model/make-model-vars (GLFW/glfwGetTime) 0.0 (:sfsim.physics/throttle 0.0))
             model                (first (:sfsim.graphics/scenes graphics))
             model-gears          (model/apply-transforms
                                    model (model/animations-frame model {"GearLeft" 2.0 "GearRight" 2.0 "GearFront" 3.0}))
             graphics             (assoc-in graphics [:sfsim.graphics/scenes 0] model-gears)
             object               [{:sfsim.graphics/object-position (vec3 dist 0 0)
                                    :sfsim.graphics/object-orientation obj-orient}]
             frame                (-> (graphics/make-frame graphics width height origin orientation
                                                           light object model-vars)
                                      (graphics/render-shadows graphics nil)
                                      (graphics/render-scene-shadows graphics)
                                      (graphics/render-cloud-geometry graphics nil)
                                      (graphics/render-clouds graphics [])
                                      (graphics/render-geometry graphics nil))
             wind-shadow          (model/scene-shadow-map (:sfsim.graphics/scene-shadow-renderer graphics)
                                                          wind-from
                                                          (first (graphics/get-moved-scenes frame graphics))
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
         (let [flood (texture/make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
           (render/framebuffer-render size size :sfsim.render/noculling nil [flood]
                                   (render/use-program program-init)
                                   (render/uniform-sampler program-init "depth" 0)
                                   (render/uniform-sampler program-init "normals" 1)
                                   (render/uniform-float program-init "mach" M)
                                   (render/uniform-int program-init "size" size)
                                   (render/uniform-float program-init "object_radius" object-radius)
                                   (render/uniform-float program-init "max_radius" max-radius)
                                   (render/use-textures {0 (:sfsim.model/shadows wind-shadow)
                                                         1 (:sfsim.model/normals wind-shadow)})
                                   (render/render-quads vao-init))
           (let [flood     (reduce (partial jump-flooding-step (:sfsim.model/normals wind-shadow))
                                   flood [256 128 64 32 16 8 4 2 1])
                 bluenoise (:sfsim.clouds/bluenoise (:sfsim.clouds/data graphics))]
             ;; Render shockwave
             (render/framebuffer-render (/ width 2) (/ height 2) :sfsim.render/noculling nil [(:sfsim.graphics/clouds frame)]
                                        (render/use-program program-shockwave)
                                        (render/uniform-sampler program-shockwave "points" 0)
                                        (render/uniform-sampler program-shockwave "flood" 1)
                                        (render/uniform-sampler program-shockwave "bluenoise" 2)
                                        (render/uniform-int program-shockwave "width" (/ width 2))
                                        (render/uniform-int program-shockwave "height" (/ height 2))
                                        (render/uniform-int program-shockwave "noise_size" (:sfsim.texture/width bluenoise))
                                        (render/uniform-float program-shockwave "object_radius" object-radius)
                                        (render/uniform-float program-shockwave "step" 0.01)
                                        (render/uniform-float program-shockwave "mach" M)
                                        (render/uniform-matrix4 program-shockwave "projection" projection)
                                        (render/uniform-matrix4 program-shockwave "ndc_to_camera" ndc-to-camera)
                                        (render/uniform-matrix4 program-shockwave "camera_to_ndc" camera-to-ndc)
                                        (render/use-textures {0 (:sfsim.clouds/points (:sfsim.graphics/cloud-geometry frame))
                                                              1 flood
                                                              2 bluenoise})
                                        (render/render-quads vao-shockwave))
             ;; Compose render of model
             (render/onscreen-render window
                                     (render/clear (vec3 0 1 0) 0.0)
                                     (graphics/render-lighting frame graphics))
             ;; Render JFA result
             (GLFW/glfwMakeContextCurrent window2)
             (render/onscreen-render window2
                                     (render/clear (vec3 0 1 0) 0.0)
                                     (render/use-program program-display)
                                     (render/uniform-sampler program-display "flood" 0)
                                     (render/uniform-int program-display "wind" 1)
                                     (render/uniform-int program-display "normals" 2)
                                     (render/uniform-int program-display "size" wsize)
                                     (render/uniform-float program-display "mach" M)
                                     (render/uniform-float program-display "max_radius" max-radius)
                                     (render/uniform-float program-display "object_radius" object-radius)
                                     (render/use-textures {0 flood
                                                           1 (:sfsim.model/shadows wind-shadow)
                                                           2 (:sfsim.model/normals wind-shadow)})
                                     (render/render-quads vao-display))
             (GLFW/glfwMakeContextCurrent window)
             (texture/destroy-texture flood)))
         (model/destroy-scene-shadow-map wind-shadow)
         (graphics/destroy-frame frame)
         (GLFW/glfwPollEvents)))

(GLFW/glfwMakeContextCurrent window2)
(render/destroy-vertex-array-object vao-display)
(render/destroy-program program-display)

(GLFW/glfwMakeContextCurrent window)
(render/destroy-vertex-array-object vao-shockwave)
(render/destroy-program program-shockwave)

(render/destroy-vertex-array-object vao-init)
(render/destroy-program program-init)

(render/destroy-vertex-array-object vao-jump-flooding)
(render/destroy-program program-jump-flooding)

(graphics/destroy-graphics2 graphics)

(GLFW/glfwDestroyWindow window2)
(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
