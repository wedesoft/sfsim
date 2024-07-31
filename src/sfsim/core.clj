(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (cos sin atan2 hypot to-radians to-degrees exp PI)]
            [fastmath.vector :refer (vec3 add mult mag sub normalize)]
            [fastmath.matrix :refer (inverse mulv)]
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
            [sfsim.astro :as astro]
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
(def height 30.0)
(def speed (atom (/ 7800 1000.0)))

(def spk (astro/make-spk-document "data/astro/de430_1850-2150.bsp"))
(def earth-moon (astro/make-spk-segment-interpolator spk 0 3))

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

(def position-data
  {:longitude (gui/edit-data "0.0" 32 :sfsim.gui/filter-float)
   :latitude  (gui/edit-data "0.0" 32 :sfsim.gui/filter-float)
   :height    (gui/edit-data "0.0" 32 :sfsim.gui/filter-float)})

(def time-data
  {:day    (gui/edit-data    "1" 3 :sfsim.gui/filter-decimal)
   :month  (gui/edit-data    "1" 3 :sfsim.gui/filter-decimal)
   :year   (gui/edit-data "2000" 5 :sfsim.gui/filter-decimal)
   :hour   (gui/edit-data   "12" 3 :sfsim.gui/filter-decimal)
   :minute (gui/edit-data    "0" 3 :sfsim.gui/filter-decimal)
   :second (gui/edit-data    "0" 3 :sfsim.gui/filter-decimal)})

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

(def menu (atom 0))

(defn position-from-lon-lat
  [longitude latitude height]
  (let [radius (+ height (:sfsim.planet/radius config/planet-config))]
    (vec3 (* (cos longitude) (cos latitude) radius)
          (* (sin longitude) (cos latitude) radius)
          (* (sin latitude) radius))))

(defn orientation-from-lon-lat
  [longitude latitude]
  (q/* (q/* (q/rotation longitude (vec3 0 0 1)) (q/rotation (- latitude) (vec3 0 1 0)))
       (q/rotation (/ (- PI) 2) (vec3 0 0 1))))

(def position (atom (position-from-lon-lat longitude latitude height)))
(def object-orientation (atom (orientation-from-lon-lat longitude latitude)))
(def camera-orientation (atom @object-orientation))
(def dist (atom 100.0))

(defn position-edit-get
  [position-data]
  (let [longitude   (to-radians (Double/parseDouble (gui/edit-get (:longitude position-data))))
        latitude    (to-radians (Double/parseDouble (gui/edit-get (:latitude position-data))))
        height      (Double/parseDouble (gui/edit-get (:height position-data)))
        position    (position-from-lon-lat longitude latitude height)
        orientation (orientation-from-lon-lat longitude latitude)]
    {:position position :orientation orientation}))

(defn position-edit-set
  [position-data position]
  (let [longitude (atan2 (.y ^Vec3 position) (.x ^Vec3 position))
        latitude  (atan2 (.z ^Vec3 position) (hypot (.x ^Vec3 position) (.y ^Vec3 position)))
        height    (- (mag position) 6378000.0)]
    (gui/edit-set (:longitude position-data) (format "%.5f" (to-degrees longitude)))
    (gui/edit-set (:latitude position-data) (format "%.5f" (to-degrees latitude)))
    (gui/edit-set (:height position-data) (format "%.1f" height))))

(def t0 (atom (System/currentTimeMillis)))
(def time-delta (atom (- (astro/now) (/ @t0 1000 86400.0))))

(defn time-edit-get
  [time-data t0]
  (let [day    (Integer/parseInt (clojure.string/trim (gui/edit-get (:day time-data))))
        month  (Integer/parseInt (clojure.string/trim (gui/edit-get (:month time-data))))
        year   (Integer/parseInt (clojure.string/trim (gui/edit-get (:year time-data))))
        hour   (Integer/parseInt (clojure.string/trim (gui/edit-get (:hour time-data))))
        minute (Integer/parseInt (clojure.string/trim (gui/edit-get (:minute time-data))))
        sec    (Integer/parseInt (clojure.string/trim (gui/edit-get (:second time-data))))
        jd     (astro/julian-date #:sfsim.astro{:year year :month month :day day})
        clock  (/ (+ (/ (+ (/ sec 60.0) minute) 60.0) hour) 24.0)]
    (- (+ (- jd astro/T0 0.5) clock) (/ t0 1000 86400.0))))

(defn time-edit-set
  [time-data time-delta t0]
  (let [t     (+ time-delta (/ t0 1000 86400.0))
        t     (+ astro/T0 t 0.5)
        date  (astro/calendar-date (int t))
        clock (astro/clock-time (- t (int t)))]
    (gui/edit-set (:day time-data) (format "%2d" (:sfsim.astro/day date)))
    (gui/edit-set (:month time-data) (format "%2d" (:sfsim.astro/month date)))
    (gui/edit-set (:year time-data) (format "%4d" (:sfsim.astro/year date)))
    (gui/edit-set (:hour time-data) (format "%2d" (:sfsim.astro/hour clock)))
    (gui/edit-set (:minute time-data) (format "%2d" (:sfsim.astro/minute clock)))
    (gui/edit-set (:second time-data) (format "%2d" (:sfsim.astro/second clock)))))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [n  (atom 0)
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
             (swap! opacity-base + (* dt to))
             (swap! dist * (exp d))
             (let [origin             (add @position (mult (q/rotate-vector @camera-orientation (vec3 0 0 -1)) (* -1.0 @dist)))
                   object-position    @position
                   jd-ut              (+ @time-delta (/ @t0 1000 86400.0) astro/T0)
                   icrs-to-earth      (inverse (astro/earth-to-icrs jd-ut))
                   sun-pos            (sub (earth-moon jd-ut))
                   light-direction    (normalize (mulv icrs-to-earth sun-pos))
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
                     1 (gui/nuklear-window gui "menu" (quot (- 1280 320) 2) (quot (- 720 (* 38 4)) 2) 320 (* 38 4)
                                           (gui/layout-row-dynamic gui 32 1)
                                           (when (gui/button-label gui "Location")
                                             (position-edit-set position-data @position)
                                             (reset! menu 2))
                                           (when (gui/button-label gui "Date/Time")
                                             (time-edit-set time-data @time-delta @t0)
                                             (reset! menu 3))
                                           (when (gui/button-label gui "Resume")
                                             (reset! menu 0))
                                           (when (gui/button-label gui "Quit")
                                             (GLFW/glfwSetWindowShouldClose window true)))
                     2 (gui/nuklear-window gui "location" (quot (- 1280 320) 2) (quot (- 720 (* 38 4)) 2) 320 (* 38 4)
                                           (gui/layout-row-dynamic gui 32 2)
                                           (gui/text-label gui "Longitude (East)")
                                           (gui/edit-field gui (:longitude position-data))
                                           (gui/text-label gui "Latitude (North)")
                                           (gui/edit-field gui (:latitude position-data))
                                           (gui/text-label gui "Height")
                                           (gui/edit-field gui (:height position-data))
                                           (when (gui/button-label gui "Set")
                                             (let [pose (position-edit-get position-data)]
                                               (reset! position (:position pose))
                                               (reset! object-orientation (:orientation pose))
                                               (reset! camera-orientation (:orientation pose))))
                                           (when (gui/button-label gui "Close")
                                             (reset! menu 1)))
                     3 (gui/nuklear-window gui "datetime" (quot (- 1280 320) 2) (quot (- 720 (* 38 3)) 2) 320 (* 38 3)
                                           (gui/layout-row gui 32 6
                                             (gui/layout-row-push gui 0.4)
                                             (gui/text-label gui "Date")
                                             (gui/layout-row-push gui 0.15)
                                             (gui/edit-field gui (:day time-data))
                                             (gui/layout-row-push gui 0.05)
                                             (gui/text-label gui "/")
                                             (gui/layout-row-push gui 0.15)
                                             (gui/edit-field gui (:month time-data))
                                             (gui/layout-row-push gui 0.05)
                                             (gui/text-label gui "/")
                                             (gui/layout-row-push gui 0.2)
                                             (gui/edit-field gui (:year time-data)))
                                           (gui/layout-row gui 32 6
                                             (gui/layout-row-push gui 0.45)
                                             (gui/text-label gui "Time")
                                             (gui/layout-row-push gui 0.15)
                                             (gui/edit-field gui (:hour time-data))
                                             (gui/layout-row-push gui 0.05)
                                             (gui/text-label gui ":")
                                             (gui/layout-row-push gui 0.15)
                                             (gui/edit-field gui (:minute time-data))
                                             (gui/layout-row-push gui 0.05)
                                             (gui/text-label gui ":")
                                             (gui/layout-row-push gui 0.14999)
                                             (gui/edit-field gui (:second time-data)))
                                           (gui/layout-row-dynamic gui 32 2)
                                           (when (gui/button-label gui "Set")
                                             (reset! time-delta (time-edit-get time-data @t0)))
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
