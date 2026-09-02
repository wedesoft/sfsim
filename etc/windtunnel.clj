(require '[clojure.math :refer (PI)]
         '[fastmath.vector :refer (vec3 normalize)]
         '[sfsim.config :as config]
         '[sfsim.quaternion :as q]
         '[sfsim.model :as model]
         '[sfsim.render :as render]
         '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL11 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils])

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
void main()
{
  float depth = texture(tex, uv_fragment).r;
  fragColor = vec3(depth, depth, depth);
}")

(GLFW/glfwMakeContextCurrent window2)
(def vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0])
(def indices  [0 1 3 2])
(def program  (render/make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-2d]))
(def vao      (render/make-vertex-array-object program indices vertices ["point" 3 "uv" 2]))

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
         (render/onscreen-render window2
                                 (render/clear (vec3 0 0 0) 0.0)
                                 (render/use-program program)
                                 (render/uniform-sampler program "tex" 0)
                                 (render/use-textures {0 (:sfsim.model/shadows wind-shadow)})
                                 (render/render-quads vao))
         (model/destroy-scene-shadow-map wind-shadow)
         (graphics/destroy-frame frame)
         (GLFW/glfwPollEvents)))

(render/destroy-vertex-array-object vao)
(render/destroy-program program)

(graphics/destroy-graphics2 graphics)

(GLFW/glfwDestroyWindow window2)
(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
