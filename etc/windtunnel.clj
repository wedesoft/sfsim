(require '[clojure.math :refer (PI to-radians)] '[fastmath.vector :refer (vec3 normalize)]
         '[fastmath.matrix :refer (mulm inverse)]
         '[sfsim.config :as config]
         '[sfsim.quaternion :as q]
         '[sfsim.matrix :as matrix]
         '[sfsim.model :as model]
         '[sfsim.render :as render]
         '[sfsim.shaders :as shaders]
         '[sfsim.bluenoise :as bluenoise]
         '[sfsim.texture :as texture]
         '[sfsim.shockwave :refer (shockfront make-shockwave-renderer jump-flooding-algorithm destroy-shockwave-renderer
                                   depth-source normal-source render-shockwave-overlay)]
         '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL])

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(def width 1024)
(def height 768)
(def size 512)
(def window (GLFW/glfwCreateWindow width height "Windtunnel" 0 0))
(GLFW/glfwSwapInterval 1)
(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwMakeContextCurrent window)
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

(GLFW/glfwMakeContextCurrent window)
(def shockwave-radius (* 2.0 (:sfsim.model/object-radius config/model-config)))
(def graphics (graphics/make-graphics2 [{:sfsim.graphics/model-file "data/models/venturestar.glb"
                                         :sfsim.graphics/object-radius (:sfsim.model/object-radius config/model-config)}]
                                       []))


(def max-curvature-radius 3.0)
(def mach 10.0)

(def bluenoise (:sfsim.clouds/bluenoise (:sfsim.clouds/data graphics)))
(def shockwave-renderer (make-shockwave-renderer depth-source normal-source shockfront size bluenoise shockwave-radius max-curvature-radius))

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwMakeContextCurrent window)
       (let [dist                 (* 2 6378000)
             origin               (vec3 dist 0 150)
             orientation          (q/->Quaternion 1 0 0 0)
             light                (normalize (vec3 1 1 1))
             wind-from            (q/rotate-vector (q/rotation (to-radians -60.0) (vec3 0 1 0)) (vec3 1 0 0))
             yaw                  (* 4 PI (/ (@mouse-pos 0) (double width)))
             pitch                (* PI (- (/ (@mouse-pos 1) (double height)) 0.5))
             obj-orient           (q/* (q/rotation yaw (vec3 0 1 0)) (q/rotation pitch (vec3 0 0 1)))
             model-vars           (model/make-model-vars (GLFW/glfwGetTime) 0.0 0.0)
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
         (let [flood (jump-flooding-algorithm shockwave-renderer wind-shadow mach)
               ]
           ;; Render shockwave
           (render/framebuffer-render (/ width 2) (/ height 2) :sfsim.render/noculling nil [(:sfsim.graphics/clouds frame)]
                                      (render/with-underlay-blending
                                        (render-shockwave-overlay shockwave-renderer flood (/ width 2) (/ height 2)
                                                                  mach projection ndc-to-camera camera-to-ndc frame)))
           ;; Compose render of model
           (render/onscreen-render window
                                   (render/clear (vec3 0 1 0) 0.0)
                                   (graphics/render-lighting frame graphics))
           (texture/destroy-texture flood))
         (model/destroy-scene-shadow-map wind-shadow)
         (graphics/destroy-frame frame)
         (GLFW/glfwPollEvents)))

(destroy-shockwave-renderer shockwave-renderer)

(GLFW/glfwMakeContextCurrent window)
(render/destroy-vertex-array-object vao-shockwave)
(render/destroy-program program-shockwave)

(graphics/destroy-graphics2 graphics)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
