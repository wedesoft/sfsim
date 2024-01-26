(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin exp)]
            [fastmath.vector :refer (vec3 add mult)]
            [sfsim25.render :refer (make-window destroy-window clear destroy-texture onscreen-render texture-render-color-depth
                                    make-render-vars)]
            [sfsim25.atmosphere :as atmosphere]
            [sfsim25.planet :as planet]
            [sfsim25.clouds :as clouds]
            [sfsim25.quaternion :as q]
            [sfsim25.opacity :as opacity]
            [sfsim25.config :as config])
  (:import [org.lwjgl.opengl GL11]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
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

(def window (make-window "sfsim25" 1280 720))
(GLFW/glfwShowWindow window)

(def cloud-data
  (clouds/make-cloud-data #:sfsim25.clouds{:cloud-octaves (clouds/octaves 4 0.7)
                                           :perlin-octaves (clouds/octaves 4 0.7)
                                           :cloud-bottom 2000.0
                                           :cloud-top 5000.0
                                           :detail-scale 4000.0
                                           :cloud-scale 100000.0
                                           :cloud-multiplier 10.0
                                           :cover-multiplier 26.0
                                           :threshold 18.2
                                           :cap 0.007
                                           :anisotropic 0.25
                                           :cloud-step 400.0
                                           :opacity-cutoff 0.01}))

(def atmosphere-luts (atmosphere/make-atmosphere-luts 35000.0))

(def shadow-data
  (opacity/make-shadow-data #:sfsim25.opacity{:num-opacity-layers 7
                                              :shadow-size 512
                                              :num-steps 3
                                              :mix 0.8
                                              :shadow-bias (exp -6.0)}
                            config/planet-config cloud-data))

; Program to render cascade of deep opacity maps
(def opacity-renderer
  (opacity/make-opacity-renderer :planet-config config/planet-config
                                 :shadow-data shadow-data
                                 :cloud-data cloud-data))

; Program to render shadow map of planet
(def planet-shadow-renderer
  (planet/make-planet-shadow-renderer :planet-config config/planet-config
                                      :shadow-data shadow-data))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer
  (planet/make-cloud-planet-renderer :render-config config/render-config
                                     :atmosphere-luts atmosphere-luts
                                     :planet-config config/planet-config
                                     :shadow-data shadow-data
                                     :cloud-data cloud-data))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer
  (clouds/make-cloud-atmosphere-renderer :render-config config/render-config
                                         :atmosphere-luts atmosphere-luts
                                         :planet-config config/planet-config
                                         :shadow-data shadow-data
                                         :cloud-data cloud-data))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer
  (planet/make-planet-renderer :render-config config/render-config
                               :atmosphere-luts atmosphere-luts
                               :planet-config config/planet-config
                               :shadow-data shadow-data))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer
  (atmosphere/make-atmosphere-renderer :render-config config/render-config
                                       :atmosphere-luts atmosphere-luts
                                       :planet-config config/planet-config))

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
             (GL11/glFinish)
             (let [render-vars  (make-render-vars config/planet-config cloud-data config/render-config (aget w 0) (aget h 0)
                                                  @position @orientation (vec3 (cos @light) (sin @light) 0) 1.0)
                   shadow-vars  (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data cloud-data
                                                                    render-vars (planet/get-current-tree tile-tree) @opacity-base)
                   w2           (quot (:sfsim25.render/window-width render-vars) 2)
                   h2           (quot (:sfsim25.render/window-height render-vars) 2)
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
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (clouds/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
