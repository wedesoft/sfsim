(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin)]
            [fastmath.vector :refer (vec3 add mult)]
            [sfsim.texture :refer (destroy-texture)]
            [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth make-render-vars)]
            [sfsim.atmosphere :as atmosphere]
            [sfsim.planet :as planet]
            [sfsim.clouds :as clouds]
            [sfsim.quaternion :as q]
            [sfsim.opacity :as opacity]
            [sfsim.config :as config])
  (:import [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(def opacity-base (atom 250.0))
(def position (atom (vec3 (+ 3.0 6378000.0) 0 0)))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))

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
(def cloud-atmosphere-renderer (clouds/make-cloud-atmosphere-renderer data))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer (planet/make-planet-renderer data))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer (atmosphere/make-atmosphere-renderer data))

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
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 50 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -50 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! opacity-base + (* dt to))
             (let [render-vars  (make-render-vars config/planet-config cloud-data config/render-config (aget w 0) (aget h 0)
                                                  @position @orientation (vec3 (cos @light) (sin @light) 0) 1.0)
                   shadow-vars  (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data cloud-data
                                                                    render-vars (planet/get-current-tree tile-tree) @opacity-base)
                   w2           (quot (:sfsim.render/window-width render-vars) 2)
                   h2           (quot (:sfsim.render/window-height render-vars) 2)
                   clouds       (texture-render-color-depth
                                  w2 h2 true
                                  (clear (vec3 0 0 0) 0.0)
                                  ; Render clouds in front of planet
                                  (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars
                                                              :tree (planet/get-current-tree tile-tree))
                                  ; Render clouds above the horizon
                                  (clouds/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0.0)
                                ; Render planet with cloud overlay
                                (planet/render-planet planet-renderer render-vars shadow-vars
                                                      :clouds clouds :tree (planet/get-current-tree tile-tree))
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer render-vars :clouds clouds))
               (destroy-texture clouds)
               (opacity/destroy-opacity-and-shadow shadow-vars))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\ro.-step (w/s) %.0f, dt %.3f" @opacity-base (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (clouds/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
