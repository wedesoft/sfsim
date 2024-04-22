(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (cos sin to-radians exp)]
            [fastmath.vector :refer (vec3 add mult)]
            [sfsim.texture :refer (destroy-texture)]
            [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth with-stencils
                                  write-to-stencil-buffer mask-with-stencil-buffer joined-render-vars)]
            [sfsim.atmosphere :as atmosphere]
            [sfsim.matrix :refer (transformation-matrix quaternion->matrix)]
            [sfsim.planet :as planet]
            [sfsim.clouds :as clouds]
            [sfsim.model :as model]
            [sfsim.quaternion :as q]
            [sfsim.opacity :as opacity]
            [sfsim.config :as config])
  (:import [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; (require '[nrepl.server :refer [start-server stop-server]])
; (defonce server (start-server :port 7888))

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(def opacity-base (atom 250.0))
(def position (atom (vec3 (+ 3.0 6378000.0) 0 0)))
(def camera-orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def object-orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def speed (atom 0.3))

(GLFW/glfwInit)

(def window (make-window "sfsim" 1280 720))
(GLFW/glfwShowWindow window)

(def cloud-data (clouds/make-cloud-data config/cloud-config))
(def atmosphere-luts (atmosphere/make-atmosphere-luts config/max-height))
(def shadow-data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data))
(def data {:sfsim.render/config config/render-config
           :sfsim.planet/config config/planet-config
           :sfsim.opacity/data shadow-data
           :sfsim.clouds/data cloud-data
           :sfsim.atmosphere/luts atmosphere-luts})

; Program to render cascade of deep opacity maps
(def opacity-renderer (opacity/make-opacity-renderer data))

; Program to render shadow map of planet
(def planet-shadow-renderer (planet/make-planet-shadow-renderer data))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer (planet/make-cloud-planet-renderer data))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer (planet/make-planet-renderer data))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer (atmosphere/make-atmosphere-renderer data))

(def scene-renderer (model/make-scene-renderer data))

(def scene-shadow-renderer (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                             config/object-radius))

(def scene (model/load-scene scene-renderer "venturestar.gltf"))

(def tile-tree (planet/make-tile-tree))

(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(def dist (atom 100.0))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [t0 (atom (System/currentTimeMillis))
        n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (not (GLFW/glfwWindowShouldClose window))
           (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
           (planet/update-tile-tree planet-renderer tile-tree (aget w 0) @position)
           (let [t1 (System/currentTimeMillis)
                 dt (- t1 @t0)
                 u  (if (@keystates GLFW/GLFW_KEY_S) 0.001 (if (@keystates GLFW/GLFW_KEY_W) -0.001 0.0))
                 r  (if (@keystates GLFW/GLFW_KEY_A) 0.001 (if (@keystates GLFW/GLFW_KEY_D) -0.001 0.0))
                 t  (if (@keystates GLFW/GLFW_KEY_E) 0.001 (if (@keystates GLFW/GLFW_KEY_Q) -0.001 0.0))
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0.0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0.0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0.0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) @speed (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) (- @speed) 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 d  (if (@keystates GLFW/GLFW_KEY_R) 0.05 (if (@keystates GLFW/GLFW_KEY_F) -0.05 0))
                 to (if (@keystates GLFW/GLFW_KEY_T) 0.05 (if (@keystates GLFW/GLFW_KEY_G) -0.05 0))]
             (swap! object-orientation q/* (q/rotation (* dt u) (vec3 0 0 1)))
             (swap! object-orientation q/* (q/rotation (* dt r) (vec3 0 1 0)))
             (swap! object-orientation q/* (q/rotation (* dt t) (vec3 1 0 0)))
             (swap! camera-orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! camera-orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! camera-orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @object-orientation (vec3 1 0 0)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! opacity-base + (* dt to))
             (swap! dist * (exp d))
             (let [origin             (add @position (mult (q/rotate-vector @camera-orientation (vec3 0 0 -1)) (* -1.0 @dist)))
                   object-position    @position
                   light-direction    (vec3 (cos @light) (sin @light) 0)
                   planet-render-vars (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                      (aget w 0) (aget h 0) origin @camera-orientation
                                                                      light-direction)
                   scene-render-vars  (model/make-scene-render-vars config/render-config (aget w 0) (aget h 0) origin
                                                                    @camera-orientation light-direction object-position
                                                                    config/object-radius)
                   shadow-render-vars (joined-render-vars planet-render-vars scene-render-vars)
                   shadow-vars        (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                          cloud-data shadow-render-vars
                                                                          (planet/get-current-tree tile-tree) @opacity-base)
                   object-to-world    (transformation-matrix (quaternion->matrix @object-orientation) object-position)
                   moved-scene        (assoc-in scene [:sfsim.model/root :sfsim.model/transform] object-to-world)
                   object-shadow      (model/scene-shadow-map scene-shadow-renderer light-direction moved-scene)
                   w2                 (quot (:sfsim.render/window-width planet-render-vars) 2)
                   h2                 (quot (:sfsim.render/window-height planet-render-vars) 2)
                   clouds             (texture-render-color-depth
                                        w2 h2 true
                                        (clear (vec3 0 0 0) 1.0)
                                        ; Render clouds in front of planet
                                        (planet/render-cloud-planet cloud-planet-renderer planet-render-vars shadow-vars
                                                                    (planet/get-current-tree tile-tree))
                                        ; Render clouds above the horizon
                                        (planet/render-cloud-atmosphere cloud-atmosphere-renderer planet-render-vars shadow-vars))]
               (onscreen-render window
                                (if (< (:sfsim.render/z-near scene-render-vars) (:sfsim.render/z-near planet-render-vars))
                                  (with-stencils
                                    (clear (vec3 0 1 0) 1.0 0)
                                    ; Render model
                                    (write-to-stencil-buffer)
                                    (model/render-scenes scene-renderer scene-render-vars shadow-vars [object-shadow] [moved-scene])
                                    (clear)
                                    ;; Render planet with cloud overlay
                                    (mask-with-stencil-buffer)
                                    (planet/render-planet planet-renderer planet-render-vars shadow-vars clouds
                                                          (planet/get-current-tree tile-tree))
                                    ;; Render atmosphere with cloud overlay
                                    (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds))
                                  (do
                                    (clear (vec3 0 1 0) 1.0)
                                    ; Render model
                                    (model/render-scenes scene-renderer planet-render-vars shadow-vars [object-shadow] [moved-scene])
                                    ; Render planet with cloud overlay
                                    (planet/render-planet planet-renderer planet-render-vars shadow-vars clouds
                                                          (planet/get-current-tree tile-tree))
                                    ; Render atmosphere with cloud overlay
                                    (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds))))
               (destroy-texture clouds)
               (model/destroy-scene-shadow-map object-shadow)
               (opacity/destroy-opacity-and-shadow shadow-vars))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\ro.-step (t/g) %.0f, dist (r/f) %.0f dt %.3f" @opacity-base @dist (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  (planet/destroy-tile-tree tile-tree)
  (model/destroy-scene scene)
  (model/destroy-scene-shadow-renderer scene-shadow-renderer)
  (model/destroy-scene-renderer scene-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (planet/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
