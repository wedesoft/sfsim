(ns sfsim.core
  "Space flight simulator main program."
  (:gen-class)
  (:require
    [clojure.math :refer (PI cos sin atan2 hypot to-radians to-degrees exp sqrt)]
    [clojure.edn]
    [clojure.pprint :refer (pprint)]
    [clojure.string :refer (trim)]
    [fastmath.matrix :refer (inverse mulv mulm)]
    [fastmath.vector :refer (vec3 add mult mag sub normalize)]
    [sfsim.astro :as astro]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.clouds :as clouds]
    [sfsim.config :as config]
    [sfsim.cubemap :as cubemap]
    [sfsim.gui :as gui]
    [sfsim.jolt :as jolt]
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
      Nuklear NkRect NkColor)
    (org.lwjgl.system
      MemoryStack)))


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
(def height 25.0)
;(def longitude (to-radians 2.23323))
;(def latitude (to-radians 56.04026))
;(def height 1155949.9)


;; (def height 30.0)
(def speed (atom (* 1 7800.0)))

(def spk (astro/make-spk-document "data/astro/de430_1850-2150.bsp"))
(def earth-moon (astro/make-spk-segment-interpolator spk 0 3))

(GLFW/glfwInit)

(jolt/jolt-init)

(def slew (atom true))

(def recording
  ; initialize recording using "echo [] > recording.edn"
  (atom (if (.exists (java.io.File. "recording.edn"))
          (mapv (fn [{:keys [timemillis position orientation camera-orientation dist gear wheel-angles suspension camera-dx camera-dy]}]
                    {:timemillis timemillis
                     :position (apply vec3 position)
                     :orientation (q/->Quaternion (:real orientation) (:imag orientation) (:jmag orientation) (:kmag orientation))
                     :camera-orientation (q/->Quaternion (:real camera-orientation) (:imag camera-orientation)
                                                         (:jmag camera-orientation) (:kmag camera-orientation))
                     :dist dist
                     :gear gear
                     :wheel-angles wheel-angles
                     :suspension suspension
                     :camera-dx camera-dx
                     :camera-dy camera-dy})
                (clojure.edn/read-string (slurp "recording.edn")))
          false)))

(def playback false)
; (def fix-fps 30.0)
(def fix-fps false)
(def fullscreen playback)

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
(def front-wheel-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Wheel Front"))))

; m = mass (100t) plus payload (25t), half mass on main gears, one-eighth mass on front wheels
; stiffness: k = m * v ^ 2 / stroke ^ 2 (kinetic energy conversion, use half the mass for m, v = 3 m/s, stroke is expected travel of spring (here divided by 1.5)
; damping: c = 2 * dampingratio * sqrt(k * m) (use half mass and dampingratio of 0.6)
; brake torque: m * a * r (use half mass, a = 1.5 m/s^2)
(def main-wheel-base {:sfsim.jolt/width 0.4064
                      :sfsim.jolt/radius (* 0.5 1.1303)
                      :sfsim.jolt/inertia 16.3690  ; Wheel weight 205 pounds, inertia of cylinder = 0.5 * mass * radius ^ 2
                      :sfsim.jolt/angular-damping 0.2
                      :sfsim.jolt/suspension-min-length (+ 0.8)
                      :sfsim.jolt/suspension-max-length (+ 0.8 0.8128)
                      :sfsim.jolt/stiffness 1915744.798
                      :sfsim.jolt/damping 415231.299
                      :sfsim.jolt/max-brake-torque 100000.0})
(def front-wheel-base {:sfsim.jolt/width 0.22352
                       :sfsim.jolt/radius (* 0.5 0.8128)
                       :sfsim.jolt/inertia 2.1839  ; Assuming same density as main wheel
                       :sfsim.jolt/angular-damping 0.2
                       :sfsim.jolt/suspension-min-length (+ 0.5)
                       :sfsim.jolt/suspension-max-length (+ 0.5 0.5419)
                       :sfsim.jolt/stiffness 1077473.882
                       :sfsim.jolt/damping 155702.159})
(def main-wheel-left (assoc main-wheel-base
                            :sfsim.jolt/position
                            (sub main-wheel-left-pos (vec3 0 0 (- (:sfsim.jolt/suspension-max-length main-wheel-base) 0.8)))))
(def main-wheel-right (assoc main-wheel-base
                             :sfsim.jolt/position
                             (sub main-wheel-right-pos (vec3 0 0 (- (:sfsim.jolt/suspension-max-length main-wheel-base) 0.8)))))
(def front-wheel (assoc front-wheel-base
                        :sfsim.jolt/position
                        (sub front-wheel-pos (vec3 0 0 (- (:sfsim.jolt/suspension-max-length front-wheel-base) 0.5)))))
(def wheels [main-wheel-left main-wheel-right front-wheel])

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


(def camera-orientation (atom (q/* (orientation-from-lon-lat longitude latitude)
                                   (q/rotation (to-radians -90) (vec3 1 0 0)))))
(def dist (atom 60.0))

(def convex-hulls-join (jolt/compound-of-convex-hulls-settings convex-hulls 0.1 (* 26.87036336765512 1.25)))
(def body (jolt/create-and-add-dynamic-body convex-hulls-join (:position @pose) (:orientation @pose)))
(jolt/set-angular-velocity body (vec3 0 0 0))
(jolt/set-friction body 0.8)
(jolt/set-restitution body 0.25)
(def mass (jolt/get-mass body))
(def surface 198.0)
(def chord 10.0)
(def wingspan 20.75)

(def vehicle (atom nil))

(jolt/optimize-broad-phase)

(def coords (atom nil))
(def mesh (atom nil))


(defn update-mesh!
  [position]
  (let [point  (cubemap/project-onto-cube position)
        face   (cubemap/determine-face point)
        j      (cubemap/cube-j face point)
        i      (cubemap/cube-i face point)
        c      (dissoc (quadtree/tile-coordinates j i
                                                  (:sfsim.planet/level config/planet-config)
                                                  (:sfsim.planet/tilesize config/planet-config))
                       :sfsim.quadtree/dy :sfsim.quadtree/dx)]
    (when (not= c @coords)
      (let [b      (:sfsim.quadtree/row c)
            a      (:sfsim.quadtree/column c)
            tile-y (:sfsim.quadtree/tile-y c)
            tile-x (:sfsim.quadtree/tile-x c)
            center (cubemap/tile-center face
                                        (:sfsim.planet/level config/planet-config) b a
                                        (:sfsim.planet/radius config/planet-config))
            m      (quadtree/create-local-mesh split-orientations face
                                               (:sfsim.planet/level config/planet-config)
                                               (:sfsim.planet/tilesize config/planet-config) b a tile-y tile-x
                                               (:sfsim.planet/radius config/planet-config) center)]
        (when @mesh (jolt/remove-and-destroy-body @mesh))
        (reset! coords c)
        (reset! mesh (jolt/create-and-add-static-body (jolt/mesh-settings m 5.9742e+24) center (q/->Quaternion 1 0 0 0)))
        (jolt/set-friction @mesh 0.8)
        (jolt/set-restitution @mesh 0.25)
        (jolt/optimize-broad-phase)))))


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


(defn stick
  [gui t u r]
  (let [stack (MemoryStack/stackPush)
        rect (NkRect/malloc stack)
        rgb  (NkColor/malloc stack)]
    (gui/nuklear-window gui "stick" 10 10 80 80
                        (let [canvas (Nuklear/nk_window_get_canvas (:sfsim.gui/context gui))]
                          (gui/layout-row-dynamic gui 80 1)
                          (Nuklear/nk_widget rect (:sfsim.gui/context gui))
                          (Nuklear/nk_fill_circle canvas
                                                  (Nuklear/nk_rect (- 45 (* t 30)) (- 45 (* u 30)) 10 10 rect)
                                                  (Nuklear/nk_rgb 255 0 0 rgb)))))
  (MemoryStack/stackPop))


(def frame-index (atom 0))
(def wheel-angles (atom [0.0 0.0 0.0]))
(def suspension (atom [1.0 1.0 1.0]))

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
    (while (and (not (GLFW/glfwWindowShouldClose window)) (or (not playback) (< @n (count @recording))))
      (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
      (planet/update-tile-tree planet-renderer tile-tree (aget w 0) (:position @pose))
      (when (@keystates GLFW/GLFW_KEY_P)
        (reset! slew true))
      (when (@keystates GLFW/GLFW_KEY_X)
        (jolt/set-orientation body (:orientation @pose))
        (jolt/set-translation body (:position @pose))
        (let [height (- (mag (:position @pose)) (:sfsim.planet/radius config/planet-config))
              max-speed (+ 320 (/ 21 (sqrt (exp (- (/ height 5500))))))
              s       (min @speed max-speed)]
          (jolt/set-linear-velocity body (mult (q/rotate-vector (:orientation @pose) (vec3 1 0 0)) (* s 0.3))))
         (jolt/set-angular-velocity body (vec3 0 0 0)))
      (when (@keystates GLFW/GLFW_KEY_O)
        (reset! slew false))
      (let [t1       (System/currentTimeMillis)
            dt       (if fix-fps (do (Thread/sleep (max 0 (int (- (/ 1000 fix-fps) (- t1 @t0))))) (/ 1000 fix-fps)) (- t1 @t0))
            mn       (if (@keystates GLFW/GLFW_KEY_ESCAPE) true false)
            gear-req (if (@keystates GLFW/GLFW_KEY_G) true false)
            ra       (if (@keystates GLFW/GLFW_KEY_KP_2) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.0005 0.0))
            rb       (if (@keystates GLFW/GLFW_KEY_KP_4) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.0005 0.0))
            rc       (if (@keystates GLFW/GLFW_KEY_KP_1) 0.0005 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.0005 0.0))
            brake    (if (@keystates GLFW/GLFW_KEY_B) 1.0 0.0)
            u        (if (@keystates GLFW/GLFW_KEY_W) 1 (if (@keystates GLFW/GLFW_KEY_S) -1 0))
            r        (if (@keystates GLFW/GLFW_KEY_D) -1 (if (@keystates GLFW/GLFW_KEY_A) 1 0))
            t        (if (@keystates GLFW/GLFW_KEY_Q) 1 (if (@keystates GLFW/GLFW_KEY_E) -1 0))
            thrust   (if (@keystates GLFW/GLFW_KEY_SPACE) (* 50.0 mass) 0.0)
            v        (if (@keystates GLFW/GLFW_KEY_PAGE_UP) @speed (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) (- @speed) 0))
            d        (if (@keystates GLFW/GLFW_KEY_R) 0.05 (if (@keystates GLFW/GLFW_KEY_F) -0.05 0))
            dcy      (if (@keystates GLFW/GLFW_KEY_K) 1 (if (@keystates GLFW/GLFW_KEY_J) -1 0))
            dcx      (if (@keystates GLFW/GLFW_KEY_L) 1 (if (@keystates GLFW/GLFW_KEY_H) -1 0))]
        (when mn (reset! menu main-dialog))
        (when (and gear-req (not @prev-gear-req))
          (swap! gear-down not))
        (reset! prev-gear-req gear-req)
        (if playback
          (let [frame (nth @recording @n)]
            (reset! time-delta (/ (- (:timemillis frame) @t0) 1000 86400.0))
            (reset! pose {:position (:position frame) :orientation (:orientation frame)})
            (reset! camera-orientation (:camera-orientation frame))
            (reset! camera-dx (:camera-dx frame))
            (reset! camera-dy (:camera-dy frame))
            (reset! dist (:dist frame))
            (reset! gear (:gear frame))
            (reset! wheel-angles (:wheel-angles frame))
            (reset! suspension (:suspension frame)))
          (do
            (if @slew
              (do
                (swap! pose update :orientation q/* (q/rotation (* dt 0.001 u) (vec3 0 1 0)))
                (swap! pose update :orientation q/* (q/rotation (* dt 0.001 r) (vec3 0 0 1)))
                (swap! pose update :orientation q/* (q/rotation (* dt 0.001 t) (vec3 1 0 0)))
                (swap! pose update :position add (mult (q/rotate-vector (:orientation @pose) (vec3 1 0 0)) (* dt 0.001 v))))
              (do
                (jolt/set-gravity (mult (normalize (:position @pose)) -9.81))
                (if @gear-down
                  (swap! gear - (* dt 0.0005))
                  (swap! gear + (* dt 0.0005)))
                (swap! gear min 1.0)
                (swap! gear max 0.0)
                (if (zero? @gear)
                  (when (not @vehicle)
                    (reset! vehicle (jolt/create-and-add-vehicle-constraint body (vec3 0 0 -1) (vec3 1 0 0) wheels)))
                  (when @vehicle
                    (jolt/remove-and-destroy-constraint @vehicle)
                    (reset! vehicle nil)))
                (when @vehicle (jolt/set-brake-input @vehicle brake))
                (jolt/add-force body (q/rotate-vector (:orientation @pose) (vec3 thrust 0 0)))
                (let [height (- (mag (:position @pose)) (:sfsim.planet/radius config/planet-config))
                      loads  (aerodynamics/aerodynamic-loads height (:orientation @pose) (jolt/get-linear-velocity body)
                                                             (jolt/get-angular-velocity body)
                                                             (mult (vec3 t u r) (to-radians 25)))]
                  (jolt/add-force body (:sfsim.aerodynamics/forces loads))
                  (jolt/add-torque body (:sfsim.aerodynamics/moments loads)))
                (update-mesh! (:position @pose))
                (jolt/update-system (* dt 0.001) 16)
                (reset! pose {:position (jolt/get-translation body) :orientation (jolt/get-orientation body)})
                (reset! wheel-angles (if @vehicle
                                       [(mod (/ (jolt/get-wheel-rotation-angle @vehicle 0) (* 2 PI)) 1.0)
                                        (mod (/ (jolt/get-wheel-rotation-angle @vehicle 1) (* 2 PI)) 1.0)
                                        (mod (/ (jolt/get-wheel-rotation-angle @vehicle 2) (* 2 PI)) 1.0)]
                                       [0.0 0.0 0.0]))
                (reset! suspension (if @vehicle
                                     [(/ (- (jolt/get-suspension-length @vehicle 0) 0.8) 0.8128)
                                      (/ (- (jolt/get-suspension-length @vehicle 1) 0.8) 0.8128)
                                      (+ 1 (/ (- (jolt/get-suspension-length @vehicle 2) 0.5) 0.5419))]
                                     [1.0 1.0 1.0]))
                (when @recording
                  (let [frame {:timemillis (+ (* @time-delta 1000 86400.0) @t0)
                               :position (:position @pose)
                               :orientation (:orientation @pose)
                               :camera-orientation @camera-orientation
                               :camera-dx @camera-dx
                               :camera-dy @camera-dy
                               :dist @dist
                               :gear @gear
                               :wheel-angles (if @vehicle
                                               [(mod (/ (jolt/get-wheel-rotation-angle @vehicle 0) (* 2 PI)) 1.0)
                                                (mod (/ (jolt/get-wheel-rotation-angle @vehicle 1) (* 2 PI)) 1.0)
                                                (mod (/ (jolt/get-wheel-rotation-angle @vehicle 2) (* 2 PI)) 1.0)]
                                               [0.0 0.0 0.0])
                               :suspension (if @vehicle
                                             [(/ (- (jolt/get-suspension-length @vehicle 0) 0.8) 0.8128)
                                              (/ (- (jolt/get-suspension-length @vehicle 1) 0.8) 0.8128)
                                              (+ 1 (/ (- (jolt/get-suspension-length @vehicle 2) 0.5) 0.5419))]
                                             [1.0 1.0 1.0])}]
                    (swap! recording conj frame)))))
            (swap! camera-dx + (* dt dcx 0.005))
            (swap! camera-dy + (* dt dcy 0.005))
            (swap! camera-orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
            (swap! camera-orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
            (swap! camera-orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
            (swap! dist * (exp d))))
        (let [object-position    (:position @pose)
              origin             (add object-position (q/rotate-vector @camera-orientation (vec3 @camera-dx @camera-dy @dist)))
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
              wheels-scene       (if (zero? @gear)
                                   (model/apply-transforms
                                     scene
                                     (model/animations-frame
                                       model
                                       {"GearLeft" (nth @suspension 0)
                                        "GearRight" (nth @suspension 1)
                                        "GearFront" (nth @suspension 2)
                                        "WheelLeft" (nth @wheel-angles 0)
                                        "WheelRight" (nth @wheel-angles 1)
                                        "WheelFront" (nth @wheel-angles 2)}))
                                   (if @vehicle
                                     (model/apply-transforms
                                       scene
                                       (model/animations-frame
                                         model
                                         {"GearLeft" (+ @gear 1)
                                          "GearRight" (+ @gear 1)
                                          "GearFront" (+ @gear 2)
                                          "WheelLeft" (nth @wheel-angles 0)
                                          "WheelRight" (nth @wheel-angles 1)
                                          "WheelFront" (nth @wheel-angles 2)}))
                                     (model/apply-transforms
                                       scene
                                       (model/animations-frame
                                         model
                                         {"GearLeft" (+ @gear 1)
                                          "GearRight" (+ @gear 1)
                                          "GearFront" (+ @gear 2)}))))
              moved-scene        (assoc-in wheels-scene [:sfsim.model/root :sfsim.model/transform]
                                           (mulm object-to-world gltf-to-aerodynamic))
              object-shadow      (model/scene-shadow-map scene-shadow-renderer light-direction moved-scene)
              clouds             (texture-render-color-depth
                                   (/ (:sfsim.render/window-width planet-render-vars) 2)
                                   (/ (:sfsim.render/window-height planet-render-vars) 2)
                                   true
                                   (clear (vec3 0 0 0) 1.0)
                                   ;; Render clouds in front of planet
                                   (planet/render-cloud-planet cloud-planet-renderer planet-render-vars shadow-vars
                                                               (planet/get-current-tree tile-tree))
                                   ;; Render clouds above the horizon
                                   (planet/render-cloud-atmosphere cloud-atmosphere-renderer planet-render-vars shadow-vars))]
          (onscreen-render window
                           (if (< (:sfsim.render/z-near scene-render-vars) (:sfsim.render/z-near planet-render-vars))
                             (with-stencils
                               (clear (vec3 0 1 0) 1.0 0)
                               ;; Render model
                               (write-to-stencil-buffer)
                               (model/render-scenes scene-renderer scene-render-vars shadow-vars [object-shadow] [moved-scene])
                               (clear)  ; Only clear depth buffer
                               ;; Render planet with cloud overlay
                               (mask-with-stencil-buffer)
                               (planet/render-planet planet-renderer planet-render-vars shadow-vars [] clouds
                                                     (planet/get-current-tree tile-tree))
                               ;; Render atmosphere with cloud overlay
                               (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds))
                             (do
                               (clear (vec3 0 1 0) 1.0)
                               ;; Render model
                               (model/render-scenes scene-renderer planet-render-vars shadow-vars [object-shadow] [moved-scene])
                               ;; Render planet with cloud overlay
                               (planet/render-planet planet-renderer planet-render-vars shadow-vars [object-shadow] clouds
                                                     (planet/get-current-tree tile-tree))
                               ;; Render atmosphere with cloud overlay
                               (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars clouds)))
                           (setup-rendering window-width window-height :sfsim.render/noculling false)
                           (when @menu
                             (reset! focus-old nil)
                             (@menu gui)
                             (reset! focus-new nil))
                           (stick gui t u r)
                           (gui/render-nuklear-gui gui window-width window-height))
          (destroy-texture clouds)
          (model/destroy-scene-shadow-map object-shadow)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          (when playback
            (let [buffer (java.nio.ByteBuffer/allocateDirect (* 4 window-width window-height))
                  data   (byte-array (* 4 window-width window-height))]
              (GL11/glFlush)
              (GL11/glFinish)
              (GL11/glReadPixels 0 0 ^long window-width ^long window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
              (.get buffer data)
              (spit-png (format "frame%06d.png" @frame-index) {:sfsim.image/data data
                                                               :sfsim.image/width window-width
                                                               :sfsim.image/height window-height
                                                               :sfsim.image/channels 4} true)
              (swap! frame-index inc))))
        (Nuklear/nk_input_begin (:sfsim.gui/context gui))
        (GLFW/glfwPollEvents)
        (Nuklear/nk_input_end (:sfsim.gui/context gui))
        (swap! n inc)
        (when (zero? (mod @n 10))
          (print (format "\rheight = %.1f, speed = %.1f, dist (r/f) %.0f dt %.3f"
                         (- (mag (:position @pose)) (:sfsim.planet/radius config/planet-config))
                         (mag (jolt/get-linear-velocity body)) @dist (* dt 0.001)))
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
  (jolt/jolt-destroy)
  (GLFW/glfwTerminate)
  (when (and (not playback) @recording)
    (spit "recording.edn" (with-out-str (pprint @recording))))
  (System/exit 0))
