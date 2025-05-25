(ns sfsim.core
  "Space flight simulator main program."
  (:gen-class)
  (:require
    [clojure.math :refer (cos sin atan2 hypot to-radians to-degrees exp sqrt)]
    [clojure.edn]
    [clojure.pprint :refer (pprint)]
    [clojure.string :refer (trim)]
    [malli.instrument :as mi]
    [malli.dev.pretty :as pretty]
    [fastmath.matrix :refer (inverse mulv mulm)]
    [fastmath.vector :refer (vec3 add mult mag sub normalize)]
    [sfsim.astro :as astro]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.clouds :as clouds]
    [sfsim.config :as config]
    [sfsim.cubemap :as cubemap]
    [sfsim.gui :as gui]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix get-translation get-translation)]
    [sfsim.model :as model]
    [sfsim.opacity :as opacity]
    [sfsim.planet :as planet]
    [sfsim.quadtree :as quadtree]
    [sfsim.quaternion :as q]
    [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth with-stencils
                                      texture-render-color write-to-stencil-buffer mask-with-stencil-buffer joined-render-vars
                                      setup-rendering quad-splits-orientations)]
    [sfsim.image :refer (spit-png)]
    [sfsim.util :refer (sqr)]
    [sfsim.texture :refer (destroy-texture texture->image)])
  (:import
    (fastmath.vector
      Vec3)
    (org.lwjgl.glfw
      GLFW
      GLFWVidMode
      GLFWCharCallbackI
      GLFWCursorPosCallbackI
      GLFWKeyCallbackI
      GLFWMouseButtonCallbackI)
    (org.lwjgl.opengl
      GL11)
    (org.lwjgl.nuklear
      Nuklear)
    (org.lwjgl.system
      MemoryStack)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; clj -M:nrepl -m sfsim.core
;; (require '[nrepl.server :refer [start-server stop-server]])
;; (defonce server (start-server :port 7888))

;; (require '[malli.dev :as dev])
;; (require '[malli.dev.pretty :as pretty])
;; (dev/start! {:report (pretty/thrower)})

; Ensure floating point numbers use a dot as decimal separator
(java.util.Locale/setDefault java.util.Locale/US)

(def opacity-base (atom 100.0))
(def longitude (to-radians -1.3747))
(def latitude (to-radians 50.9672))
(def height 40.0)
;(def longitude (to-radians 2.23323))
;(def latitude (to-radians 56.04026))
;(def height 1155949.9)


;; (def height 30.0)
(def speed (atom (* 1 7800.0)))

(def spk (astro/make-spk-document "data/astro/de430_1850-2150.bsp"))
(def earth-moon (astro/make-spk-segment-interpolator spk 0 3))

(GLFW/glfwInit)

(def fix-fps false)
(def fullscreen false)
; (def fullscreen true)

(def monitor (GLFW/glfwGetPrimaryMonitor))
(def mode (GLFW/glfwGetVideoMode monitor))

(def desktop-width (.width ^GLFWVidMode mode))
(def desktop-height (.height ^GLFWVidMode mode))
(def window-width (if fullscreen desktop-width 854))
(def window-height (if fullscreen desktop-height 480))
;(def window-width (if fullscreen desktop-width 1920))
;(def window-height (if fullscreen desktop-height 620))
(def window (make-window "sfsim" window-width window-height (not fullscreen)))
(GLFW/glfwShowWindow window)

(def cloud-data (clouds/make-cloud-data config/cloud-config))
(def atmosphere-luts (atmosphere/make-atmosphere-luts config/max-height))
(def shadow-data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data))


(def data
  {:sfsim.render/config config/render-config
   :sfsim.planet/config config/planet-config
   :sfsim.opacity/data shadow-data
   :sfsim.clouds/data cloud-data
   :sfsim.atmosphere/luts atmosphere-luts})


;; Program to render cascade of deep opacity maps
(def opacity-renderer (opacity/make-opacity-renderer data))


;; Program to render shadow map of planet
(def planet-shadow-renderer (planet/make-planet-shadow-renderer data))


;; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer (planet/make-cloud-planet-renderer data))


;; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data))


;; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer (planet/make-planet-renderer data))


;; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer (atmosphere/make-atmosphere-renderer data))

(def scene-renderer (model/make-scene-renderer data))


(def scene-shadow-renderer
  (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                    config/object-radius))


(def gltf-to-aerodynamic (rotation-matrix aerodynamics/gltf-to-aerodynamic))
(def aerodynamic-to-gltf (rotation-matrix (inverse aerodynamics/gltf-to-aerodynamic)))

(def model (model/read-gltf "venturestar.glb"))
(def scene (model/load-scene scene-renderer model))
(def convex-hulls (update (model/empty-meshes-to-points model) :sfsim.model/transform #(mulm gltf-to-aerodynamic %)))

; (def main-wheel-left-path (model/get-node-path scene "Main Wheel Left"))
; (def main-wheel-right-path (model/get-node-path scene "Main Wheel Right"))
; (def front-wheel-path (model/get-node-path scene "Front Wheel"))

(def main-wheel-left-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Main Wheel Left"))))
(def main-wheel-right-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Main Wheel Right"))))
(def front-wheel-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Front Wheel"))))

; m = mass (100t) plus payload (25t)
; stiffness: k = m * g * v ^ 2 / stroke ^ 2 (kinetic energy conversion, use half the mass for m, v = 3 m/s, stroke is expected travel of spring
; damping: c = 2 * dampingratio * sqrt(k * m) (use half mass and dampingratio of 0.6)

(def tile-tree (planet/make-tile-tree))

(def split-orientations (quad-splits-orientations (:sfsim.planet/tilesize config/planet-config) 8))

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

(def focus-old (atom 0))
(def focus-new (atom nil))


(def keyboard-callback
  (reify GLFWKeyCallbackI
    (invoke
      [_this _window k _scancode action mods]
      (when (= action GLFW/GLFW_PRESS)
        (swap! keystates assoc k true))
      (when (= action GLFW/GLFW_RELEASE)
        (swap! keystates assoc k false))
      (let [press (or (= action GLFW/GLFW_PRESS) (= action GLFW/GLFW_REPEAT))]
        (when (and press (= k GLFW/GLFW_KEY_TAB))
          (if @focus-old
            (if (not (zero? (bit-and mods GLFW/GLFW_MOD_SHIFT)))
              (reset! focus-new (dec @focus-old))
              (reset! focus-new (inc @focus-old)))
            (reset! focus-new 0)))
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
    (invoke
      [_this _window codepoint]
      (Nuklear/nk_input_unicode (:sfsim.gui/context gui) codepoint))))


(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI
    (invoke
      [_this _window xpos ypos]
      (Nuklear/nk_input_motion (:sfsim.gui/context gui) (int xpos) (int ypos)))))


(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI
    (invoke
      [_this _window button action _mods]
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


(def menu (atom nil))

(declare main-dialog)


(defmacro tabbing
  [gui edit idx cnt]
  `(do
     (when (and @focus-new (= (mod @focus-new ~cnt) ~idx))
       (Nuklear/nk_edit_focus (:sfsim.gui/context ~gui) Nuklear/NK_EDIT_ACTIVE))
     (when (= Nuklear/NK_EDIT_ACTIVE ~edit)
       (reset! focus-old ~idx))))


(defn position-from-lon-lat
  [longitude latitude height]
  (let [point      (vec3 (* (cos longitude) (cos latitude)) (* (sin longitude) (cos latitude)) (sin latitude))
        min-radius (quadtree/distance-to-surface point
                                                 (:sfsim.planet/level config/planet-config)
                                                 (:sfsim.planet/tilesize config/planet-config)
                                                 (:sfsim.planet/radius config/planet-config)
                                                 split-orientations)
        radius     (+ height (:sfsim.planet/radius config/planet-config))]
    (mult point (max radius min-radius))))


(defn orientation-from-lon-lat
  [longitude latitude]
  (let [radius-vector (position-from-lon-lat longitude latitude 1.0)]
    (q/vector-to-vector-rotation (vec3 0 0 1) (sub radius-vector))))


(def pose
  (atom {:position (position-from-lon-lat longitude latitude height)
         :orientation (orientation-from-lon-lat longitude latitude)}))

(def pose
  (atom {:position (position-from-lon-lat longitude latitude height)
         :orientation (q/->Quaternion 0.2814353323842392 -0.18141854626013024 -0.8246546977675215 -0.45587947333543516)}))


(def camera-orientation (atom (q/* (orientation-from-lon-lat longitude latitude)
                                   (q/rotation (to-radians -90) (vec3 1 0 0)))))
(def dist (atom 60.0))
(def dist (atom 20.996264946669374))

(def surface 198.0)
(def chord 10.0)
(def wingspan 20.75)

(def coords (atom nil))
(def mesh (atom nil))


(defn location-dialog-get
  [position-data]
  (let [longitude   (to-radians (Double/parseDouble (gui/edit-get (:longitude position-data))))
        latitude    (to-radians (Double/parseDouble (gui/edit-get (:latitude position-data))))
        height      (Double/parseDouble (gui/edit-get (:height position-data)))
        position    (position-from-lon-lat longitude latitude height)
        orientation (orientation-from-lon-lat longitude latitude)]
    {:position position :orientation orientation}))


(defn location-dialog-set
  [position-data pose]
  (let [position  (:position pose)
        longitude (atan2 (.y ^Vec3 position) (.x ^Vec3 position))
        latitude  (atan2 (.z ^Vec3 position) (hypot (.x ^Vec3 position) (.y ^Vec3 position)))
        height    (- (mag position) 6378000.0)]
    (gui/edit-set (:longitude position-data) (format "%.5f" (to-degrees longitude)))
    (gui/edit-set (:latitude position-data) (format "%.5f" (to-degrees latitude)))
    (gui/edit-set (:height position-data) (format "%.1f" height))))


(defn location-dialog
  [gui]
  (gui/nuklear-window gui "location" (quot (- window-width 320) 2) (quot (- window-height (* 38 4)) 2) 320 (* 38 4)
                      (gui/layout-row-dynamic gui 32 2)
                      (gui/text-label gui "Longitude (East)")
                      (tabbing gui (gui/edit-field gui (:longitude position-data)) 0 3)
                      (gui/text-label gui "Latitude (North)")
                      (tabbing gui (gui/edit-field gui (:latitude position-data)) 1 3)
                      (gui/text-label gui "Height")
                      (tabbing gui (gui/edit-field gui (:height position-data)) 2 3)
                      (when (gui/button-label gui "Set")
                        (reset! pose (location-dialog-get position-data))
                        (reset! camera-orientation (q/* (:orientation @pose)
                                                        (q/rotation (to-radians -90) (vec3 1 0 0)))))
                      (when (gui/button-label gui "Close")
                        (reset! menu main-dialog))))


(def t0 (atom (System/currentTimeMillis)))
(def time-delta (atom (- (+ (int (astro/now)) (+ (/ -5.0 24.0) (/ 27.0 60.0 24.0))) (/ @t0 1000 86400.0))))

(def camera-dx (atom 0.0))
(def camera-dy (atom 0.0))

(defn datetime-dialog-get
  [time-data t0]
  (let [day    (Integer/parseInt (trim (gui/edit-get (:day time-data))))
        month  (Integer/parseInt (trim (gui/edit-get (:month time-data))))
        year   (Integer/parseInt (trim (gui/edit-get (:year time-data))))
        hour   (Integer/parseInt (trim (gui/edit-get (:hour time-data))))
        minute (Integer/parseInt (trim (gui/edit-get (:minute time-data))))
        sec    (Integer/parseInt (trim (gui/edit-get (:second time-data))))
        jd     (astro/julian-date #:sfsim.astro{:year year :month month :day day})
        clock  (/ (+ (/ (+ (/ sec 60.0) minute) 60.0) hour) 24.0)]
    (- (+ (- jd astro/T0 0.5) clock) (/ t0 1000 86400.0))))


(defn datetime-dialog-set
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


(defn datetime-dialog
  [gui]
  (gui/nuklear-window gui "datetime" (quot (- window-width 320) 2) (quot (- window-height (* 38 3)) 2) 320 (* 38 3)
                      (gui/layout-row gui 32 6
                                      (gui/layout-row-push gui 0.4)
                                      (gui/text-label gui "Date")
                                      (gui/layout-row-push gui 0.15)
                                      (tabbing gui (gui/edit-field gui (:day time-data)) 0 6)
                                      (gui/layout-row-push gui 0.05)
                                      (gui/text-label gui "/")
                                      (gui/layout-row-push gui 0.15)
                                      (tabbing gui (gui/edit-field gui (:month time-data)) 1 6)
                                      (gui/layout-row-push gui 0.05)
                                      (gui/text-label gui "/")
                                      (gui/layout-row-push gui 0.2)
                                      (tabbing gui (gui/edit-field gui (:year time-data)) 2 6))
                      (gui/layout-row gui 32 6
                                      (gui/layout-row-push gui 0.45)
                                      (gui/text-label gui "Time")
                                      (gui/layout-row-push gui 0.15)
                                      (tabbing gui (gui/edit-field gui (:hour time-data)) 3 6)
                                      (gui/layout-row-push gui 0.05)
                                      (gui/text-label gui ":")
                                      (gui/layout-row-push gui 0.15)
                                      (tabbing gui (gui/edit-field gui (:minute time-data)) 4 6)
                                      (gui/layout-row-push gui 0.05)
                                      (gui/text-label gui ":")
                                      (gui/layout-row-push gui 0.14999)
                                      (tabbing gui (gui/edit-field gui (:second time-data)) 5 6))
                      (gui/layout-row-dynamic gui 32 2)
                      (when (gui/button-label gui "Set")
                        (reset! time-delta (datetime-dialog-get time-data @t0)))
                      (when (gui/button-label gui "Close")
                        (reset! menu main-dialog))))


(defn main-dialog
  [gui]
  (gui/nuklear-window gui "menu" (quot (- window-width 320) 2) (quot (- window-height (* 38 4)) 2) 320 (* 38 4)
                      (gui/layout-row-dynamic gui 32 1)
                      (when (gui/button-label gui "Location")
                        (location-dialog-set position-data @pose)
                        (reset! menu location-dialog))
                      (when (gui/button-label gui "Date/Time")
                        (datetime-dialog-set time-data @time-delta @t0)
                        (reset! menu datetime-dialog))
                      (when (gui/button-label gui "Resume")
                        (reset! menu nil))
                      (when (gui/button-label gui "Quit")
                        (GLFW/glfwSetWindowShouldClose window true))))

(def frame-index (atom 0))
(def wheelposes (atom nil))

(defmacro render-frame
  [_window & body]
  `(let [tex# (texture-render-color 1920 1080 true ~@body)
         img# (texture->image tex#)]
     (spit-png (format "%06d.png" @frame-index) img#)
     (swap! frame-index inc)
     (destroy-texture tex#)))


(def gear (atom 0.0))
(def gear-down (atom true))
(def prev-gear-req (atom false))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (not (GLFW/glfwWindowShouldClose window))
      (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
      (planet/update-tile-tree planet-renderer tile-tree (aget w 0) (:position @pose))
      (let [t1       (System/currentTimeMillis)
            dt       (if fix-fps (do (Thread/sleep (max 0 (int (- (/ 1000 fix-fps) (- t1 @t0))))) (/ 1000 fix-fps)) (- t1 @t0))
            mn       (if (@keystates GLFW/GLFW_KEY_ESCAPE) true false)
            gear-req (if (@keystates GLFW/GLFW_KEY_G) true false)
            ra       (if (@keystates GLFW/GLFW_KEY_KP_2) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.0005 0.0))
            rb       (if (@keystates GLFW/GLFW_KEY_KP_4) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.0005 0.0))
            rc       (if (@keystates GLFW/GLFW_KEY_KP_1) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.0005 0.0))
            u        (if (@keystates GLFW/GLFW_KEY_S) 1 (if (@keystates GLFW/GLFW_KEY_W) -1 0))
            r        (if (@keystates GLFW/GLFW_KEY_A) -1 (if (@keystates GLFW/GLFW_KEY_D) 1 0))
            t        (if (@keystates GLFW/GLFW_KEY_E) 1 (if (@keystates GLFW/GLFW_KEY_Q) -1 0))
            v        (if (@keystates GLFW/GLFW_KEY_PAGE_UP) @speed (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) (- @speed) 0))
            d        (if (@keystates GLFW/GLFW_KEY_R) 0.05 (if (@keystates GLFW/GLFW_KEY_F) -0.05 0))
            dcy      (if (@keystates GLFW/GLFW_KEY_K) 1 (if (@keystates GLFW/GLFW_KEY_J) -1 0))
            dcx      (if (@keystates GLFW/GLFW_KEY_L) 1 (if (@keystates GLFW/GLFW_KEY_H) -1 0))
            to       (if (@keystates GLFW/GLFW_KEY_T) 0.05 (if (@keystates GLFW/GLFW_KEY_G) -0.05 0))]
        (when mn (reset! menu main-dialog))
        (swap! pose update :orientation q/* (q/rotation (* dt 0.001 u) (vec3 0 1 0)))
        (swap! pose update :orientation q/* (q/rotation (* dt 0.001 r) (vec3 0 0 1)))
        (swap! pose update :orientation q/* (q/rotation (* dt 0.001 t) (vec3 1 0 0)))
        (swap! pose update :position add (mult (q/rotate-vector (:orientation @pose) (vec3 1 0 0)) (* dt 0.001 v)))
        (when (and gear-req (not @prev-gear-req))
          (swap! gear-down not))
        (if @gear-down
          (swap! gear - (* dt 0.0005))
          (swap! gear + (* dt 0.0005)))
        (swap! gear min 1.0)
        (swap! gear max 0.0)
        (reset! prev-gear-req gear-req)
        (swap! camera-dx + (* dt dcx 0.0001))
        (swap! camera-dy + (* dt dcy 0.0001))
        (swap! camera-orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
        (swap! camera-orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
        (swap! camera-orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
        (swap! opacity-base + (* dt to))
        (swap! dist * (exp d))
        (let [object-position    (:position @pose)
              origin             (add object-position (mult (q/rotate-vector @camera-orientation (vec3 @camera-dx @camera-dy -1)) (* -1.0 @dist)))
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
              object-to-world    (transformation-matrix (quaternion->matrix (:orientation @pose)) object-position)
              wheels-scene       (model/apply-transforms scene (model/animations-frame model {"GearLeft" (+ @gear 1.0)
                                                                                              "GearRight" (+ @gear 1.0)
                                                                                              "GearFront" (+ @gear 2.0)}))
              moved-scene        (assoc-in wheels-scene [:sfsim.model/root :sfsim.model/transform]
                                           (mulm object-to-world gltf-to-aerodynamic))]
          (onscreen-render window
                           (clear (vec3 0 1 0) 1.0)
                           ;; Render model
                           (model/render-scenes scene-renderer planet-render-vars shadow-vars [] [moved-scene])
                           (when @menu
                             (setup-rendering window-width window-height :sfsim.render/noculling false)
                             (reset! focus-old nil)
                             (@menu gui)
                             (reset! focus-new nil)
                             (gui/render-nuklear-gui gui window-width window-height)))
          (opacity/destroy-opacity-and-shadow shadow-vars))
        (Nuklear/nk_input_begin (:sfsim.gui/context gui))
        (GLFW/glfwPollEvents)
        (Nuklear/nk_input_end (:sfsim.gui/context gui))
        (swap! n inc)
        (when (zero? (mod @n 10))
          (print (format "\ro.-step (t/g) %.0f, dist (r/f) %.0f dt %.3f" @opacity-base @dist (* dt 0.001)))
          (flush))
        (if fix-fps (reset! t0 (System/currentTimeMillis)) (swap! t0 + dt)))))
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
