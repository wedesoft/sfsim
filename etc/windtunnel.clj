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
(def height 720) (def window (GLFW/glfwCreateWindow width height "Windtunnel" 0 0))
(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwSwapInterval 1)
(GLFW/glfwShowWindow window)
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

(def object-radius (:sfsim.model/object-radius config/model-config))
(def graphics (graphics/make-graphics2 [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                         :sfsim.graphics/object-radius object-radius}]
                                       []))


(while (not (GLFW/glfwWindowShouldClose window))
       (let [dist        (* 2 6378000)
             origin      (vec3 dist 0 100)
             orientation (q/->Quaternion 1 0 0 0)
             light       (normalize (vec3 1 1 1))
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
                             (graphics/render-geometry graphics nil))]
         (render/onscreen-render window
                                 (render/clear (vec3 0 1 0) 0.0)
                                 (graphics/render-lighting frame graphics))
         (graphics/destroy-frame frame)
         (GLFW/glfwPollEvents)))

(graphics/destroy-graphics2 graphics)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
