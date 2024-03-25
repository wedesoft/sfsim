(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (cos sin to-radians exp)]
            [fastmath.matrix :refer (eye)]
            [fastmath.vector :refer (vec3 add mult)]
            [sfsim.texture :refer (destroy-texture)]
            [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth)]
            [sfsim.atmosphere :as atmosphere]
            [sfsim.matrix :refer (transformation-matrix)]
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
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def speed (atom 10.0))

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

(def model-renderer (model/make-model-renderer data))

(def scene (model/load-scene model-renderer "test/sfsim/fixtures/model/bricks.gltf"))

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

(def dist (atom 30.0))

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
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0.0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0.0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0.0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) @speed (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) (- @speed) 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 d  (if (@keystates GLFW/GLFW_KEY_Q) 0.05 (if (@keystates GLFW/GLFW_KEY_A) -0.05 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! opacity-base + (* dt to))
             (swap! dist * (exp d))
             (let [origin             (add @position (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* -1.0 @dist)))
                   object-position    @position
                   planet-render-vars (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                      (aget w 0) (aget h 0) origin @orientation
                                                                      (vec3 (cos @light) (sin @light) 0) 1.0)
                   shadow-vars        (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                          cloud-data planet-render-vars
                                                                          (planet/get-current-tree tile-tree) @opacity-base)
                   object-to-world    (transformation-matrix (eye 3) object-position)
                   moved-scene        (assoc-in scene [:sfsim.model/root :sfsim.model/transform] object-to-world)
                   w2                 (quot (:sfsim.render/window-width planet-render-vars) 2)
                   h2                 (quot (:sfsim.render/window-height planet-render-vars) 2)
                   clouds             (texture-render-color-depth
                                        w2 h2 true
                                        (clear (vec3 0 0 0) 0.0)
                                        ; Render clouds in front of planet
                                        (planet/render-cloud-planet cloud-planet-renderer planet-render-vars shadow-vars
                                                                    (planet/get-current-tree tile-tree))
                                        ; Render clouds above the horizon
                                        (planet/render-cloud-atmosphere cloud-atmosphere-renderer planet-render-vars shadow-vars))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0.0)
                                ; Render cube model
                                (model/render-scenes model-renderer planet-render-vars shadow-vars [moved-scene])
                                ; Render planet with cloud overlay
                                (planet/render-planet planet-renderer planet-render-vars shadow-vars clouds
                                                      (planet/get-current-tree tile-tree))
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds))
               (destroy-texture clouds)
               (opacity/destroy-opacity-and-shadow shadow-vars))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\ro.-step (w/s) %.0f, dist (q/a) %.0f dt %.3f" @opacity-base @dist (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  (planet/destroy-tile-tree tile-tree)
  (model/destroy-scene scene)
  (model/destroy-model-renderer model-renderer)
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
