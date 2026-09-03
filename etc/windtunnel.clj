(require '[clojure.math :refer (PI)]
         '[fastmath.vector :refer (vec3 normalize)]
         '[sfsim.config :as config]
         '[sfsim.quaternion :as q]
         '[sfsim.model :as model]
         '[sfsim.render :as render]
         '[sfsim.texture :as texture]
         '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL30])

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(def width 1280)
(def height 720)
(def window (GLFW/glfwCreateWindow width height "Windtunnel" 0 0))
(GLFW/glfwSwapInterval 1)
(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def window2 (GLFW/glfwCreateWindow 512 512 "Wind-Shadow" 0 window))
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
(def object-radius (:sfsim.model/object-radius config/model-config))
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


(def fragment-texture-2d
"#version 450 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler2D tex;
uniform sampler2D wind;
void main()
{
  vec2 uv_fragment = gl_FragCoord.xy / 512.0;
  vec3 point = texture(tex, uv_fragment).xyz;
  float depth = point.z - 2 * length(point.xy - uv_fragment);
  if (texture(wind, uv_fragment).r > 0.0)
    fragColor = vec3(1.0, depth, depth);
  else
    fragColor = vec3(0.0, depth, depth);
}")

;; https://en.wikipedia.org/wiki/Jump_flooding_algorithm

(def fragment-init
"#version 450 core
in vec2 uv_fragment;
uniform sampler2D tex;
layout (location = 0) out vec3 point;
void main()
{
  float depth = texture(tex, uv_fragment).r;
  point = vec3(gl_FragCoord.xy / 512.0, depth);
}")


(def fragment-jump-flooding
"#version 450 core
in vec2 uv_fragment;
uniform sampler2D tex;
uniform int step;
layout (location = 0) out vec3 point;
vec3 nearest(vec3 result, vec2 uv_fragment, vec2 dpos)
{
  vec3 point = texture(tex, uv_fragment + dpos).xyz;
  float current = result.z - 2 * length(result.xy - uv_fragment);
  float candidate = point.z - 2 * length(point.xy - uv_fragment);
  if (candidate > current)
    return point;
  else
    return result;
}
void main()
{
  float delta = float(step) / 512.0;
  vec2 uv_fragment = gl_FragCoord.xy / 512.0;
  vec3 result = texture(tex, uv_fragment).xyz;
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


(GLFW/glfwMakeContextCurrent window2)
(def vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0])
(def indices [0 1 3 2])
(def program-texture  (render/make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-2d]))
(def vao-texture (render/make-vertex-array-object program-texture indices vertices ["point" 3 "uv" 2]))

(def program-init (render/make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-init]))
(def vao-init (render/make-vertex-array-object program-init indices vertices ["point" 3 "uv" 2]))
(def program-jump-flooding (render/make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-jump-flooding]))
(def vao-jump-flooding (render/make-vertex-array-object program-jump-flooding indices vertices ["point" 3 "uv" 2]))

(defn jump-flooding-step
  [flood step]
  (let [result (texture/make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGB32F 512 512)]
    (render/framebuffer-render 512 512 :sfsim.render/noculling nil [result]
                               (render/use-program program-jump-flooding)
                               (render/uniform-sampler program-texture "tex" 0)
                               (render/uniform-int program-jump-flooding "step" step)
                               (render/use-textures {0 flood})
                               (render/render-quads vao-jump-flooding))
    (texture/destroy-texture flood)
    result))


(while (and (not (GLFW/glfwWindowShouldClose window)) (not (GLFW/glfwWindowShouldClose window2)))
       (GLFW/glfwMakeContextCurrent window)
       (let [dist        (* 2 6378000)
             origin      (vec3 dist 0 100)
             orientation (q/->Quaternion 1 0 0 0)
             light       (normalize (vec3 1 1 1))
             wind-from   (vec3 1 0 0)
             yaw         (* 4 PI (/ (@mouse-pos 0) (double width)))
             pitch       (* PI (- (/ (@mouse-pos 1) (double height)) 0.5))
             obj-orient  (q/* (q/rotation yaw (vec3 0 1 0)) (q/rotation pitch (vec3 0 0 1)))
             model-vars  (model/make-model-vars (GLFW/glfwGetTime) 0.0 (:sfsim.physics/throttle 0.0))
             model       (first (:sfsim.graphics/scenes graphics))
             model-gears (model/apply-transforms model (model/animations-frame model {"GearLeft" 2.0 "GearRight" 2.0 "GearFront" 3.0}))
             graphics    (assoc-in graphics [:sfsim.graphics/scenes 0] model-gears)
             object      [{:sfsim.graphics/object-position (vec3 dist 0 0)
                           :sfsim.graphics/object-orientation obj-orient}]
             frame       (-> (graphics/make-frame graphics width height origin orientation
                                                  light object model-vars)
                             (graphics/render-shadows graphics nil)
                             (graphics/render-scene-shadows graphics)
                             (graphics/render-cloud-geometry graphics nil)
                             (graphics/render-clouds graphics [])
                             (graphics/render-geometry graphics nil))
             wind-shadow (model/scene-shadow-map (:sfsim.graphics/scene-shadow-renderer graphics)
                                                 wind-from
                                                 (first (graphics/get-moved-scenes frame graphics))
                                                 :sfsim.render/cullback)]
         (render/onscreen-render window
                                 (render/clear (vec3 0 1 0) 0.0)
                                 (graphics/render-lighting frame graphics))
         (GLFW/glfwMakeContextCurrent window2)
         (let [flood (texture/make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGB32F 512 512)]
           (render/framebuffer-render 512 512 :sfsim.render/noculling nil [flood]
                                   (render/use-program program-init)
                                   (render/uniform-sampler program-init "tex" 0)
                                   (render/use-textures {0 (:sfsim.model/shadows wind-shadow)})
                                   (render/render-quads vao-init))
           (let [flood (reduce jump-flooding-step flood [256 128 64 32 16 8 4 2 1])]
             (render/onscreen-render window2
                                   (render/clear (vec3 0 1 0) 0.0)
                                   (render/use-program program-texture)
                                   (render/uniform-sampler program-texture "tex" 0)
                                   (render/uniform-int program-texture "wind" 1)
                                   (render/use-textures {0 flood 1 (:sfsim.model/shadows wind-shadow)})
                                   (render/render-quads vao-texture)))
           (texture/destroy-texture flood))
         (model/destroy-scene-shadow-map wind-shadow)
         (graphics/destroy-frame frame)
         (GLFW/glfwPollEvents)))

(render/destroy-vertex-array-object vao-init)
(render/destroy-program program-init)

(render/destroy-vertex-array-object vao-jump-flooding)
(render/destroy-program program-jump-flooding)

(render/destroy-vertex-array-object vao-texture)
(render/destroy-program program-texture)

(graphics/destroy-graphics2 graphics)

(GLFW/glfwDestroyWindow window2)
(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
