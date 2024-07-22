(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (cos sin atan2 hypot to-radians to-degrees exp PI)]
            [fastmath.vector :refer (vec3 add mult mag)]
            [sfsim.texture :refer (destroy-texture)]
            [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth with-stencils
                                  write-to-stencil-buffer mask-with-stencil-buffer joined-render-vars setup-rendering)]
            [sfsim.atmosphere :as atmosphere]
            [sfsim.matrix :refer (transformation-matrix quaternion->matrix)]
            [sfsim.planet :as planet]
            [sfsim.clouds :as clouds]
            [sfsim.model :as model]
            [sfsim.quaternion :as q]
            [sfsim.opacity :as opacity]
            [sfsim.gui :as gui]
            [sfsim.config :as config])
  (:import [fastmath.vector Vec3]
           [org.lwjgl.system MemoryStack]
           [org.lwjgl.glfw GLFW GLFWKeyCallbackI GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWCharCallbackI]
           [org.lwjgl.nuklear Nuklear])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; (require '[nrepl.server :refer [start-server stop-server]])
; (defonce server (start-server :port 7888))

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(def opacity-base (atom 250.0))
(def longitude (to-radians -1.3747))
(def latitude (to-radians 50.9672))
(def object-orientation (atom nil))
(def camera-orientation (atom nil))
(def height 30.0)
(def position (atom nil))
(def light (atom 0.0))
(def speed (atom (/ 7800 1000.0)))

(defn set-geographic-position
  [longitude latitude height]
  (let [radius (+ height 6378000.0)]
    (reset! position (vec3 (* (cos longitude) (cos latitude) radius)
                           (* (sin longitude) (cos latitude) radius)
                           (* (sin latitude) radius)))
    (reset! object-orientation (q/* (q/* (q/rotation longitude (vec3 0 0 1)) (q/rotation (- latitude) (vec3 0 1 0)))
                                    (q/rotation (/ (- PI) 2) (vec3 0 0 1))))
    (reset! camera-orientation @object-orientation)))

(set-geographic-position longitude latitude height)

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

(def buffer-initial-size (* 4 1024))
(def bitmap-font (gui/setup-font-texture (gui/make-bitmap-font "resources/fonts/b612.ttf" 512 512 18)))
(def gui (gui/make-nuklear-gui (:sfsim.gui/font bitmap-font) buffer-initial-size))
(gui/nuklear-dark-style gui)

(def longitude-data (gui/gui-edit-data "0.0" 31 :sfsim.gui/filter-float))
(def latitude-data (gui/gui-edit-data "0.0" 31 :sfsim.gui/filter-float))
(def height-data (gui/gui-edit-data "0.0" 31 :sfsim.gui/filter-float))

(def keystates (atom {}))

(def keyboard-callback
  (reify GLFWKeyCallbackI
         (invoke [_this _window k _scancode action _mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false))
           (let [press (or (= action GLFW/GLFW_PRESS) (= action GLFW/GLFW_REPEAT))]
             (cond
               (= k GLFW/GLFW_KEY_DELETE)      (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_DEL press)
               (= k GLFW/GLFW_KEY_ENTER)       (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_ENTER press)
               (= k GLFW/GLFW_KEY_TAB)         (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_TAB press)
               (= k GLFW/GLFW_KEY_BACKSPACE)   (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_BACKSPACE press)
               (= k GLFW/GLFW_KEY_UP)          (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_UP press)
               (= k GLFW/GLFW_KEY_DOWN)        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_DOWN press)
               (= k GLFW/GLFW_KEY_LEFT)        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_LEFT press)
               (= k GLFW/GLFW_KEY_RIGHT)       (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_RIGHT press)
               (= k GLFW/GLFW_KEY_HOME)        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_TEXT_START press)
               (= k GLFW/GLFW_KEY_END)         (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_TEXT_END press)
               (= k GLFW/GLFW_KEY_LEFT_SHIFT)  (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_SHIFT press)
               (= k GLFW/GLFW_KEY_RIGHT_SHIFT) (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_SHIFT press))))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(GLFW/glfwSetCharCallback
  window
  (reify GLFWCharCallbackI
         (invoke [this window codepoint]
           (Nuklear/nk_input_unicode (:sfsim.gui/context gui) codepoint))))

(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI
         (invoke [_this _window xpos ypos]
           (Nuklear/nk_input_motion (:sfsim.gui/context gui) (int xpos) (int ypos)))))

(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI
         (invoke [_this _window button action _mods]
           (let [stack (MemoryStack/stackPush)
                 cx    (.mallocDouble stack 1)
                 cy    (.mallocDouble stack 1)]
             (GLFW/glfwGetCursorPos ^long window cx cy)
             (let [x        (int (.get cx 0))
                   y        (int (.get cy 0))
                   nkbutton (cond
                              (= button GLFW/GLFW_MOUSE_BUTTON_RIGHT) Nuklear/NK_BUTTON_RIGHT
                              (= button GLFW/GLFW_MOUSE_BUTTON_MIDDLE) Nuklear/NK_BUTTON_MIDDLE
                              :else Nuklear/NK_BUTTON_LEFT)]
               (Nuklear/nk_input_button (:sfsim.gui/context gui) nkbutton x y (= action GLFW/GLFW_PRESS))
               (MemoryStack/stackPop))))))

(def dist (atom 100.0))

(def menu (atom 0))

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
                 mn (if (@keystates GLFW/GLFW_KEY_ESCAPE) true false)
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
             (when mn (reset! menu 1))
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
                     (planet/render-planet planet-renderer planet-render-vars shadow-vars [] clouds
                                           (planet/get-current-tree tile-tree))
                     ;; Render atmosphere with cloud overlay
                     (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds))
                   (do
                     (clear (vec3 0 1 0) 1.0)
                     ; Render model
                     (model/render-scenes scene-renderer planet-render-vars shadow-vars [object-shadow] [moved-scene])
                     ; Render planet with cloud overlay
                     (planet/render-planet planet-renderer planet-render-vars shadow-vars [object-shadow] clouds
                                           (planet/get-current-tree tile-tree))
                     ; Render atmosphere with cloud overlay
                     (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds)))
                 (when (not (zero? @menu))
                   (setup-rendering 1280 720 :sfsim.render/noculling false)
                   (case (int @menu)
                     1 (gui/nuklear-window gui "menu" (quot (- 1280 320) 2) (quot (- 720 (* 38 3)) 2) 320 (* 38 3)
                                           (gui/layout-row-dynamic gui 32 1)
                                           (when (gui/button-label gui "Location")
                                             (let [pos       @position
                                                   longitude (atan2 (.y ^Vec3 pos) (.x ^Vec3 pos))
                                                   latitude  (atan2 (.z ^Vec3 pos) (hypot (.x ^Vec3 pos) (.y ^Vec3 pos)))
                                                   height    (- (mag pos) 6378000.0)]
                                               (gui/gui-edit-set longitude-data (format "%.5f" (to-degrees longitude)))
                                               (gui/gui-edit-set latitude-data (format "%.5f" (to-degrees latitude)))
                                               (gui/gui-edit-set height-data (format "%.1f" height))
                                               (reset! menu 2)))
                                           (when (gui/button-label gui "Resume")
                                             (reset! menu 0))
                                           (when (gui/button-label gui "Quit")
                                           (GLFW/glfwSetWindowShouldClose window true)))
                     2 (gui/nuklear-window gui "location" (quot (- 1280 320) 2) (quot (- 720 (* 38 4)) 2) 320 (* 38 4)
                                           (gui/layout-row-dynamic gui 32 2)
                                           (gui/text-label gui "Longitude (East)")
                                           (gui/edit-field gui longitude-data)
                                           (gui/text-label gui "Latitude (North)")
                                           (gui/edit-field gui latitude-data)
                                           (gui/text-label gui "Height")
                                           (gui/edit-field gui height-data)
                                           (when (gui/button-label gui "Set")
                                             (set-geographic-position
                                               (to-radians (Double/parseDouble (gui/gui-edit-get longitude-data)))
                                               (to-radians (Double/parseDouble (gui/gui-edit-get latitude-data)))
                                               (Double/parseDouble (gui/gui-edit-get height-data))))
                                           (when (gui/button-label gui "Close")
                                             (reset! menu 1))))
                   (gui/render-nuklear-gui gui 1280 720)))
               (destroy-texture clouds)
               (model/destroy-scene-shadow-map object-shadow)
               (opacity/destroy-opacity-and-shadow shadow-vars))
             (Nuklear/nk_input_begin (:sfsim.gui/context gui))
             (GLFW/glfwPollEvents)
             (Nuklear/nk_input_end (:sfsim.gui/context gui))
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
  (gui/destroy-nuklear-gui gui)
  (gui/destroy-font-texture bitmap-font)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
