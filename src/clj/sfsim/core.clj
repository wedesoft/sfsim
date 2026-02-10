;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.core
  "Space flight simulator main program."
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.math :refer (PI to-radians to-degrees)]
    [clojure.tools.logging :as log]
    [clojure.edn]
    [clojure.pprint :refer (pprint)]
    [clojure.string :refer (trim)]
    [malli.dev :as dev]
    [malli.dev.pretty :as pretty]
    [fastmath.matrix :refer (inverse mulv mulm)]
    [fastmath.vector :refer (vec3 mag sub normalize)]
    [sfsim.astro :as astro]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.version :refer (version)]
    [sfsim.camera :as camera]
    [sfsim.clouds :as clouds]
    [sfsim.config :as config]
    [sfsim.cubemap :as cubemap]
    [sfsim.gui :as gui]
    [sfsim.util :refer (dissoc-in)]
    [sfsim.jolt :as jolt]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix get-translation get-translation)]
    [sfsim.model :as model]
    [sfsim.opacity :as opacity]
    [sfsim.planet :as planet]
    [sfsim.physics :as physics]
    [sfsim.quadtree :as quadtree]
    [sfsim.quaternion :as q]
    [sfsim.render :refer (make-window destroy-window clear onscreen-render with-stencils with-stencil-op-ref-and-mask
                          joined-render-vars quad-splits-orientations with-depth-test with-culling)]
    [sfsim.image :refer (spit-png)]
    [sfsim.texture :refer (destroy-texture)]
    [sfsim.input :refer (default-mappings make-event-buffer make-initial-state process-events add-mouse-move-event
                         add-mouse-button-event joysticks-poll ->InputHandler char-callback key-callback
                         get-joystick-sensor-for-mapping)])
  (:import
    (org.lwjgl.glfw
      GLFW
      GLFWVidMode
      GLFWCursorPosCallbackI
      GLFWMouseButtonCallbackI)
    (org.lwjgl.opengl
      GL11)
    (org.lwjgl.nuklear
      Nuklear NkRect NkColor)
    (org.lwjgl.system
      MemoryStack)))


(defmacro tabbing
  [gui edit idx cnt]
  `(do
     (when (and (@state :sfsim.input/focus-new) (= (mod (@state :sfsim.input/focus-new) ~cnt) ~idx))
       (Nuklear/nk_edit_focus (:sfsim.gui/context ~gui) Nuklear/NK_EDIT_ACTIVE)
       (swap! state dissoc :sfsim.input/focus-new))
     (when (= Nuklear/NK_EDIT_ACTIVE ~edit)
       (swap! state assoc :sfsim.input/focus ~idx))))


(try

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(log/info "starting sfsim" version)


(when (.exists (io/file ".schemas"))
  (dev/start! {:report (pretty/thrower)}))

; Ensure floating point numbers use a dot as decimal separator
(java.util.Locale/setDefault java.util.Locale/US)


(def opacity-base 100.0)

(def spk (astro/make-spk-document "data/astro/de430_1850-2150.bsp"))
(def barycenter-sun (astro/make-spk-segment-interpolator spk 0 10))
(def barycenter-earth (astro/make-spk-segment-interpolator spk 0 3))
(defn earth-sun [jd-ut] (sub (barycenter-sun jd-ut) (barycenter-earth jd-ut)))

(GLFW/glfwInit)

(jolt/jolt-init)
(jolt/set-gravity (vec3 0 0 0))

(def recording
  ; initialize recording using "echo [] > recording.edn"
  (atom (if (.exists (java.io.File. "recording.edn"))
          (mapv (fn [{:keys [julian-date-ut position orientation camera-state dist gear
                             wheel-angles suspension throttle rcs-thrust]}]
                    {:julian-date-ut julian-date-ut
                     :position (apply vec3 position)
                     :orientation (q/->Quaternion (:real orientation) (:imag orientation) (:jmag orientation) (:kmag orientation))
                     :camera-state camera-state
                     :dist dist
                     :gear gear
                     :throttle throttle
                     :rcs-thrust (apply vec3 rcs-thrust)
                     :wheel-angles wheel-angles
                     :suspension suspension})
                (clojure.edn/read-string (slurp "recording.edn")))
          false)))

(def playback false)
; (def playback true)
(def fix-fps false)
; (def fix-fps 30)

(def window-width (atom (:sfsim.render/window-width config/render-config)))
(def window-height (atom (:sfsim.render/window-height config/render-config)))

(def window (make-window "sfsim" @window-width @window-height true))

(def cloud-data (clouds/make-cloud-data config/cloud-config))
(def atmosphere-luts (atmosphere/make-atmosphere-luts config/max-height))
(def shadow-data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data))


(def earth-radius (:sfsim.planet/radius config/planet-config))
(def elevation (:sfsim.model/elevation config/model-config))


(def data
  {:sfsim.render/config config/render-config
   :sfsim.planet/config config/planet-config
   :sfsim.opacity/data shadow-data
   :sfsim.clouds/data cloud-data
   :sfsim.model/data config/model-config
   :sfsim.atmosphere/luts atmosphere-luts})


;; Program to render cascade of deep opacity maps
(def opacity-renderer (opacity/make-opacity-renderer data))


;; Program to render shadow map of planet
(def planet-shadow-renderer (planet/make-planet-shadow-renderer data))


;; Program to render low-resolution scene geometry to facilitate volumetric cloud and plume rendering
(def joined-geometry-renderer (model/make-joined-geometry-renderer data))


;; Program to render low-resolution overlay of clouds
(def cloud-renderer (clouds/make-cloud-renderer data))


;; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer (planet/make-planet-renderer data))


;; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer (atmosphere/make-atmosphere-renderer data))


;; Program to render 3D model
(def scene-renderer (model/make-scene-renderer data))


(def scene-shadow-renderer
  (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                    (:sfsim.model/object-radius config/model-config)))


(def gltf-to-aerodynamic (rotation-matrix aerodynamics/gltf-to-aerodynamic))

(def model (model/read-gltf "venturestar.glb"))
(def scene (model/load-scene scene-renderer model))
(def convex-hulls (update (model/empty-meshes-to-points model) :sfsim.model/transform #(mulm gltf-to-aerodynamic %)))

(def main-wheel-left-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Main Wheel Left"))))
(def main-wheel-right-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Main Wheel Right"))))
(def front-wheel-pos (get-translation (mulm gltf-to-aerodynamic (model/get-node-transform scene "Wheel Front"))))
(def bsp-tree (update (model/get-bsp-tree model "BSP") :sfsim.model/transform #(mulm gltf-to-aerodynamic %)))

(def thruster-transforms
  (into {}
        (remove nil?
                (map (fn [rcs-name] (some->> (model/get-node-transform model rcs-name)
                                             (mulm gltf-to-aerodynamic)
                                             (vector rcs-name)))
                     (physics/all-rcs)))))


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
                            (sub main-wheel-left-pos (vec3 0 0 (- ^double (:sfsim.jolt/suspension-max-length main-wheel-base) 0.8)))))
(def main-wheel-right (assoc main-wheel-base
                             :sfsim.jolt/position
                             (sub main-wheel-right-pos (vec3 0 0 (- ^double (:sfsim.jolt/suspension-max-length main-wheel-base) 0.8)))))
(def front-wheel (assoc front-wheel-base
                        :sfsim.jolt/position
                        (sub front-wheel-pos (vec3 0 0 (- ^double (:sfsim.jolt/suspension-max-length front-wheel-base) 0.5)))))
(def wheels [main-wheel-left main-wheel-right front-wheel])

(def tile-tree (planet/make-tile-tree))

(def split-orientations (quad-splits-orientations (:sfsim.planet/tilesize config/planet-config) 8))
(def surface (quadtree/distance-to-surface config/planet-config split-orientations))

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


(def mappings (atom default-mappings))

(def event-buffer (atom (make-event-buffer)))
(def state (atom (make-initial-state)))
(def old-state (atom @state))
(def input-handler (->InputHandler gui mappings))


(GLFW/glfwSetCharCallback window (char-callback event-buffer))
(GLFW/glfwSetKeyCallback window (key-callback event-buffer))


(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window xpos ypos]
      (swap! event-buffer add-mouse-move-event xpos ypos))))


(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window button action mods]
      (let [cx    (double-array 1)
            cy    (double-array 1)]
        (GLFW/glfwGetCursorPos ^long _window cx cy)
        (let [x        (long (aget cx 0))
              y        (long (aget cy 0))]
          (swap! event-buffer add-mouse-button-event button x y action mods))))))


(def menu (atom nil))

(declare main-dialog)


(def convex-hulls-join (jolt/compound-of-convex-hulls-settings convex-hulls 0.1 (* 26.87036336765512 1.25)))
(def body (jolt/create-and-add-dynamic-body convex-hulls-join (vec3 0 0 0) (q/->Quaternion 1 0 0 0)))
(jolt/set-friction body 0.8)
(jolt/set-restitution body 0.25)
(def mass (jolt/get-mass body))
(def thrust (* ^double mass 25.0))

; Start with fixed summer date for better illumination.

(def longitude (to-radians -1.3747))
(def latitude (to-radians 50.9672))
(def height 25.0)

(def physics-state (atom (physics/make-physics-state body)))
(swap! physics-state physics/set-geographic surface config/planet-config elevation longitude latitude 0.0)
(swap! physics-state physics/set-julian-date-ut (astro/julian-date {:sfsim.astro/year 2026 :sfsim.astro/month 6 :sfsim.astro/day 22}))

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
                                        earth-radius)
            m      (quadtree/create-local-mesh split-orientations face
                                               (:sfsim.planet/level config/planet-config)
                                               (:sfsim.planet/tilesize config/planet-config) b a tile-y tile-x
                                               earth-radius center)]
        (when @mesh (jolt/remove-and-destroy-body @mesh))
        (reset! coords c)
        (reset! mesh (jolt/create-and-add-static-body (jolt/mesh-settings m 5.9742e+24) center (q/->Quaternion 1 0 0 0)))
        (jolt/set-friction @mesh 0.8)
        (jolt/set-restitution @mesh 0.25)
        (jolt/optimize-broad-phase)))))


(defn joystick-dialog-item
  [gui sensor-type last-event text control sensor-name prompt]
  (let [[device sensor] (get-joystick-sensor-for-mapping @mappings sensor-type control)]
    (gui/layout-row gui 32 4
                    (gui/layout-row-push gui 0.2)
                    (gui/text-label gui text)
                    (gui/layout-row-push gui 0.1)
                    (when (and (gui/button-label gui "Clear") device)
                      (swap! mappings dissoc-in [:sfsim.input/joysticks :sfsim.input/devices device sensor-type sensor]))
                    (gui/layout-row-push gui 0.1)
                    (when (gui/button-label gui "Set")
                      (swap! state dissoc last-event)
                      (if (= (@state ::joystick-config) control)
                        (swap! state dissoc ::joystick-config)
                        (swap! state assoc ::joystick-config control)))
                    (when-let [[device-new sensor-new] (and (= (@state ::joystick-config) control) (@state last-event))]
                              (swap! mappings dissoc-in [:sfsim.input/joysticks :sfsim.input/devices device sensor-type sensor])
                              (swap! mappings assoc-in [:sfsim.input/joysticks :sfsim.input/devices device-new sensor-type sensor-new]
                                     control)
                              (swap! state dissoc ::joystick-config))
                    (gui/layout-row-push gui 0.6)
                    (gui/text-label gui (if (= (@state ::joystick-config) control)
                                          prompt
                                          (if device (format "%s %d of %s" sensor-name sensor device) "None"))))))


(defn joystick-dialog-axis-item
  [gui text control]
  (joystick-dialog-item gui :sfsim.input/axes :sfsim.input/last-joystick-axis text control "Axis" "Move axis to set"))


(defn joystick-dialog-button-item
  [gui text control]
  (joystick-dialog-item gui :sfsim.input/buttons :sfsim.input/last-joystick-button text control "Button" "Press button to set"))


(defn joystick-dialog
  [gui ^long window-width ^long window-height]
  (gui/nuklear-window gui "Joystick" (quot (- window-width 640) 2) (quot (- window-height (* 37 12)) 2) 640 (* 37 12) true
                      (joystick-dialog-axis-item gui "Aileron" :sfsim.input/aileron)
                      (joystick-dialog-axis-item gui "Elevator" :sfsim.input/elevator)
                      (joystick-dialog-axis-item gui "Rudder" :sfsim.input/rudder)
                      (joystick-dialog-axis-item gui "Throttle" :sfsim.input/throttle)
                      (joystick-dialog-axis-item gui "Throttle Increment" :sfsim.input/throttle-increment)
                      (gui/layout-row gui 32 2
                                      (gui/layout-row-push gui 0.2)
                                      (gui/text-label gui "Dead Zone")
                                      (gui/layout-row-push gui 0.7)
                                      (swap! mappings update-in [:sfsim.input/joysticks :sfsim.input/dead-zone]
                                             (fn [dead-zone]
                                                 (gui/slider-float gui 0.0 dead-zone 1.0 (/ 1.0 1024.0))))
                                      (gui/layout-row-push gui 0.1)
                                      (gui/text-label gui (format "%5.3f" (get-in @mappings [:sfsim.input/joysticks :sfsim.input/dead-zone]))))
                      (joystick-dialog-button-item gui "Gear" :sfsim.input/gear)
                      (joystick-dialog-button-item gui "Air Brake" :sfsim.input/air-brake)
                      (joystick-dialog-button-item gui "Brake" :sfsim.input/brake)
                      (joystick-dialog-button-item gui "Parking Brake" :sfsim.input/parking-brake)
                      (gui/layout-row-dynamic gui 32 2)
                      (when (gui/button-label gui "Save")
                        (config/write-user-config "joysticks.edn" (@mappings :sfsim.input/joysticks))
                        (reset! menu main-dialog))
                      (when (gui/button-label gui "Close")
                        (reset! menu main-dialog))))


(defn location-dialog-get
  [position-data]
  (let [longitude   (to-radians (Double/parseDouble (gui/edit-get (:longitude position-data))))
        latitude    (to-radians (Double/parseDouble (gui/edit-get (:latitude position-data))))
        height      (Double/parseDouble (gui/edit-get (:height position-data)))]
    {:longitude longitude :latitude latitude :height height}))


(defn location-dialog-set
  [position-data]
  (let [jd-ut      (physics/get-julian-date-ut @physics-state)
        geographic (physics/get-geographic @physics-state config/planet-config jd-ut)]
    (gui/edit-set (:longitude position-data) (format "%.5f" (to-degrees (:longitude geographic))))
    (gui/edit-set (:latitude position-data) (format "%.5f" (to-degrees (:latitude geographic))))
    (gui/edit-set (:height position-data) (format "%.1f" (:height geographic)))))


(defn location-dialog
  [gui ^long window-width ^long window-height]
  (gui/nuklear-window
    gui "Location" (quot (- window-width 320) 2) (quot (- window-height (* 37 5)) 2) 320 (* 37 5) true
    (gui/layout-row-dynamic gui 32 2)
    (gui/text-label gui "Longitude (East)")
    (tabbing gui (gui/edit-field gui (:longitude position-data)) 0 3)
    (gui/text-label gui "Latitude (North)")
    (tabbing gui (gui/edit-field gui (:latitude position-data)) 1 3)
    (gui/text-label gui "Height")
    (tabbing gui (gui/edit-field gui (:height position-data)) 2 3)
    (when (gui/button-label gui "Set")
      (let [{:keys [longitude latitude height]} (location-dialog-get position-data)]
        (swap! physics-state physics/destroy-vehicle-constraint)
        (swap! physics-state physics/set-geographic surface config/planet-config elevation longitude latitude height)))
    (when (gui/button-label gui "Close")
      (reset! menu main-dialog))))


(def t0 (atom (GLFW/glfwGetTime)))

(defn datetime-dialog-get
  ^double [time-data]
  (let [day    (Integer/parseInt (trim (gui/edit-get (:day time-data))))
        month  (Integer/parseInt (trim (gui/edit-get (:month time-data))))
        year   (Integer/parseInt (trim (gui/edit-get (:year time-data))))
        hour   (Integer/parseInt (trim (gui/edit-get (:hour time-data))))
        minute (Integer/parseInt (trim (gui/edit-get (:minute time-data))))
        sec    (Integer/parseInt (trim (gui/edit-get (:second time-data))))
        jd     (astro/julian-date #:sfsim.astro{:year year :month month :day day})
        clock  (/ (+ (/ (+ (/ sec 60.0) minute) 60.0) hour) 24.0)]
    (+ (- jd 0.5) clock)))


(defn datetime-dialog-set
  [time-data]
  (let [t     (+ (physics/get-julian-date-ut @physics-state) 0.5)
        date  (astro/calendar-date (int t))
        clock (astro/clock-time (- t (int t)))]
    (gui/edit-set (:day time-data) (format "%2d" (:sfsim.astro/day date)))
    (gui/edit-set (:month time-data) (format "%2d" (:sfsim.astro/month date)))
    (gui/edit-set (:year time-data) (format "%4d" (:sfsim.astro/year date)))
    (gui/edit-set (:hour time-data) (format "%2d" (:sfsim.astro/hour clock)))
    (gui/edit-set (:minute time-data) (format "%2d" (:sfsim.astro/minute clock)))
    (gui/edit-set (:second time-data) (format "%2d" (:sfsim.astro/second clock)))))


(defn datetime-dialog
  [gui ^long window-width ^long window-height]
  (gui/nuklear-window gui "Date and Time" (quot (- window-width 320) 2) (quot (- window-height (* 37 4)) 2) 320 (* 37 4) true
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
                        (swap! physics-state physics/set-julian-date-ut (datetime-dialog-get time-data)))
                      (when (gui/button-label gui "Close")
                        (reset! menu main-dialog))))


(defn main-dialog
  [gui ^long window-width ^long window-height]
  (gui/nuklear-window gui (format "sfsim %s" version)
                      (quot (- window-width 320) 2) (quot (- window-height (* 37 6)) 2) 320 (* 37 6) true
                      (gui/layout-row-dynamic gui 32 1)
                      (when (gui/button-label gui "Joystick")
                        (reset! menu joystick-dialog))
                      (when (gui/button-label gui "Location")
                        (location-dialog-set position-data)
                        (reset! menu location-dialog))
                      (when (gui/button-label gui "Date/Time")
                        (datetime-dialog-set time-data)
                        (reset! menu datetime-dialog))
                      (when (gui/button-label gui "Resume")
                        (swap! state assoc :sfsim.input/menu nil))
                      (when (gui/button-label gui "Quit")
                        (GLFW/glfwSetWindowShouldClose window true))))


(defn stick
  [gui input-state]
  (let [stack    (MemoryStack/stackPush)
        rect     (NkRect/malloc stack)
        rgb      (NkColor/malloc stack)
        throttle (:sfsim.input/throttle input-state)
        aileron  (:sfsim.input/aileron input-state)
        elevator (:sfsim.input/elevator input-state)
        rudder   (:sfsim.input/rudder input-state)]
    (gui/nuklear-window gui "Yoke" 10 10 80 80 false
                        (let [canvas (Nuklear/nk_window_get_canvas (:sfsim.gui/context gui))]
                          (gui/layout-row-dynamic gui 80 1)
                          (Nuklear/nk_widget rect (:sfsim.gui/context gui))
                          (Nuklear/nk_fill_circle canvas
                                                  (Nuklear/nk_rect (- 45 (* ^double aileron 30)) (- 45 (* ^double elevator 30)) 10 10 rect)
                                                  (Nuklear/nk_rgb 255 0 0 rgb))))
    (gui/nuklear-window gui "Rudder" 10 95 80 20 false
                        (let [canvas (Nuklear/nk_window_get_canvas (:sfsim.gui/context gui))]
                          (gui/layout-row-dynamic gui 20 1)
                          (Nuklear/nk_widget rect (:sfsim.gui/context gui))
                          (Nuklear/nk_fill_circle canvas
                                                  (Nuklear/nk_rect (- 45 (* ^double rudder 30)) 100 10 10 rect)
                                                  (Nuklear/nk_rgb 255 0 255 rgb))))
    (gui/nuklear-window gui "Throttle" 95 10 20 80 false
                        (let [canvas (Nuklear/nk_window_get_canvas (:sfsim.gui/context gui))]
                          (gui/layout-row-dynamic gui 80 1)
                          (Nuklear/nk_widget rect (:sfsim.gui/context gui))
                          (Nuklear/nk_fill_circle canvas
                                                  (Nuklear/nk_rect 100 (- 75 (* 60 ^double throttle)) 10 10 rect)
                                                  (Nuklear/nk_rgb 255 255 255 rgb)))))
  (MemoryStack/stackPop))


(def camera-state (atom (camera/make-camera-state)))


(defn info
  [gui ^long h ^String text]
  (gui/nuklear-window gui "Information" 10 (- h 42) 640 32 false
                      (gui/layout-row-dynamic gui 32 1)
                      (gui/text-label gui text)))


(def frame-index (atom 0))


(def frametime (atom 0.25))

(catch Exception e
       (log/error e "Exception at startup")
       (log/info "aborting sfsim" version)
       (System/exit 1)))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (try
  (let [n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (and (not (GLFW/glfwWindowShouldClose window)) (or (not playback) (< ^long @n (count @recording))))
      (when (not= (@state :sfsim.input/fullscreen) (@old-state :sfsim.input/fullscreen))
        (let [monitor (GLFW/glfwGetPrimaryMonitor)
              mode (GLFW/glfwGetVideoMode monitor)
              desktop-width (.width ^GLFWVidMode mode)
              desktop-height (.height ^GLFWVidMode mode)]
          (if (@state :sfsim.input/fullscreen)
            (GLFW/glfwSetWindowMonitor window monitor 0 0 desktop-width desktop-height GLFW/GLFW_DONT_CARE)
            (GLFW/glfwSetWindowMonitor window 0 (quot (- desktop-width 854) 2) (quot (- desktop-height 480) 2) 854 480 GLFW/GLFW_DONT_CARE))))
      (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
      (reset! window-width (aget w 0))
      (reset! window-height (aget h 0))
      (let [t1        (GLFW/glfwGetTime)
            dt        (if fix-fps
                        (do (Thread/sleep (long (* 1000.0 (max 0.0 ^double (- (/ 1.0 ^double fix-fps) (- ^double t1 ^double @t0))))))
                          (/ 1.0 ^double fix-fps))
                        (- t1 ^double @t0))
            jd-ut     (physics/get-julian-date-ut @physics-state)]
        (planet/update-tile-tree planet-renderer tile-tree @window-width
                                 (physics/get-position :sfsim.physics/surface jd-ut @physics-state))
        (if (@state :sfsim.input/menu)
          (swap! menu #(or % main-dialog))
          (reset! menu nil))
        (if playback
          (let [frame (nth @recording @n)]
            (swap! physics-state physics/set-julian-date-ut (:julian-date-ut frame))
            (swap! physics-state physics/set-pose :sfsim.physics/surface (:position frame) (:orientation frame))
            (reset! camera-state (:camera-state frame))
            (swap! physics-state assoc :sfsim.physics/throttle (:throttle frame))
            (swap! physics-state assoc :sfsim.physics/gear (:gear frame))
            (swap! physics-state physics/update-gear-status jd-ut wheels)
            (physics/set-wheel-angles @physics-state (:wheel-angles frame))
            (physics/set-suspension @physics-state (:suspension frame))
            (swap! physics-state assoc :sfsim.physics/rcs-thrust (:rcs-thrust frame)))
          (do
            (when (not (@state :sfsim.input/pause))
              (swap! physics-state physics/update-domain jd-ut config/planet-config)
              (swap! physics-state physics/set-control-inputs @state dt)
              (swap! physics-state physics/update-gear-status jd-ut wheels)
              (physics/update-brakes @physics-state)
              (update-mesh! (physics/get-position :sfsim.physics/surface jd-ut @physics-state))
              (physics/set-thruster-forces @physics-state jd-ut thrust)
              (physics/set-aerodynamic-forces @physics-state config/planet-config jd-ut)
              (swap! physics-state
                     physics/update-state
                     dt (physics/gravitation (vec3 0 0 0) (config/planet-config :sfsim.planet/mass))))
            (let [speed (mag (physics/get-linear-speed :sfsim.physics/surface jd-ut @physics-state))
                  mode  (if (>= speed 500.0) :sfsim.camera/fast :sfsim.camera/slow)]
              (swap! camera-state camera/set-mode mode jd-ut @physics-state)
              (swap! camera-state camera/update-camera-pose dt @state))
            (when (and @recording (not (@state :sfsim.input/pause)))
              (let [frame {:julian-date-ut (physics/get-julian-date-ut @physics-state)
                           :position (physics/get-position :sfsim.physics/surface jd-ut @physics-state)
                           :orientation (physics/get-orientation :sfsim.physics/surface jd-ut @physics-state)
                           :camera-state @camera-state
                           :dist (:sfsim.camera/distance @camera-state)
                           :gear (:sfsim.physics/gear @physics-state)
                           :throttle (:sfsim.physics/throttle @physics-state)
                           :rcs-thrust (:sfsim.physics/rcs-thrust @physics-state)
                           :wheel-angles (physics/get-wheel-angles @physics-state)
                           :suspension (physics/get-suspension @physics-state)}]
                (swap! recording conj frame)))))
        (let [object-position    (physics/get-position :sfsim.physics/surface jd-ut @physics-state)
              height             (- (mag object-position) ^double earth-radius)
              pressure           (/ (atmosphere/pressure-at-height height) (atmosphere/pressure-at-height 0.0))
              object-orientation (physics/get-orientation :sfsim.physics/surface jd-ut @physics-state)
              object-to-world    (transformation-matrix (quaternion->matrix object-orientation) object-position)
              [origin camera-orientation] ((juxt :sfsim.camera/position :sfsim.camera/orientation)
                                           (camera/get-camera-pose @camera-state @physics-state jd-ut))
              icrs-to-earth      (inverse (astro/earth-to-icrs jd-ut))
              sun-pos            (earth-sun jd-ut)
              light-direction    (normalize (mulv icrs-to-earth sun-pos))
              model-vars         (model/make-model-vars (:sfsim.physics/offset-seconds @physics-state) pressure
                                                        (:sfsim.physics/throttle @physics-state))
              planet-render-vars (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                 @window-width @window-height origin camera-orientation
                                                                 light-direction object-position object-orientation model-vars)
              scene-render-vars  (model/make-scene-render-vars config/render-config @window-width @window-height origin
                                                               camera-orientation light-direction object-position
                                                               object-orientation config/model-config model-vars)
              shadow-render-vars (joined-render-vars planet-render-vars scene-render-vars)
              shadow-vars        (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                     cloud-data shadow-render-vars
                                                                     (planet/get-current-tree tile-tree) opacity-base)
              cloud-render-vars  (clouds/make-cloud-render-vars config/render-config planet-render-vars @window-width @window-height
                                                                origin camera-orientation light-direction object-position
                                                                object-orientation)
              wheels-scene       (let [wheel-animation (map #(mod (/ ^double % (* 2.0 PI)) 1.0)
                                                            (physics/get-wheel-angles @physics-state))
                                       gear-animation
                                       (let [gear (:sfsim.physics/gear @physics-state)]
                                         (if (= ^double gear 1.0)
                                           (let [suspension (physics/get-suspension @physics-state)]
                                             [(/ (- ^double (nth suspension 0) 0.8) 0.8128)
                                              (/ (- ^double (nth suspension 1) 0.8) 0.8128)
                                              (+ 1.0 (/ (- ^double (nth suspension 2) 0.5) 0.5419))])
                                           [(- 2.0 ^double gear) (- 2.0 ^double gear) (- 3.0 ^double gear)]))]
                                   (model/apply-transforms
                                     scene
                                     (model/animations-frame
                                       model
                                       {"GearLeft" (nth gear-animation 0)
                                        "GearRight" (nth gear-animation 1)
                                        "GearFront" (nth gear-animation 2)
                                        "WheelLeft" (nth wheel-animation 0)
                                        "WheelRight" (nth wheel-animation 1)
                                        "WheelFront" (nth wheel-animation 2)})))
              moved-scene        (assoc-in wheels-scene [:sfsim.model/root :sfsim.model/transform]
                                           (mulm object-to-world gltf-to-aerodynamic))
              object-shadow      (model/scene-shadow-map scene-shadow-renderer light-direction moved-scene)
              geometry           (model/render-joined-geometry joined-geometry-renderer scene-render-vars planet-render-vars moved-scene
                                                               (planet/get-current-tree tile-tree))
              object-origin      (:sfsim.render/object-origin scene-render-vars)
              render-order       (filterv (physics/active-rcs @physics-state) (model/bsp-render-order bsp-tree object-origin))
              plume-transforms   (map (fn [thruster] [thruster (thruster-transforms thruster)]) render-order)
              clouds             (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars plume-transforms
                                                              geometry)]
          (onscreen-render window
                           (with-depth-test true
                             (if (< ^double (:sfsim.render/z-near scene-render-vars) ^double (:sfsim.render/z-near planet-render-vars))
                               (with-stencils
                                 (clear (vec3 0 1 0) 1.0 0)
                                 ;; Render model
                                 (with-stencil-op-ref-and-mask GL11/GL_ALWAYS 0x1 0x1
                                   (model/render-scenes scene-renderer scene-render-vars shadow-vars [object-shadow]
                                                        geometry clouds [moved-scene]))
                                 (clear)  ; Only clear depth buffer
                                 ;; Render planet with cloud overlay
                                 (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x0 0x1
                                   (planet/render-planet planet-renderer planet-render-vars shadow-vars [] geometry clouds
                                                         (planet/get-current-tree tile-tree))
                                   ;; Render atmosphere with cloud overlay
                                   (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars geometry clouds)))
                               (do
                                 (clear (vec3 0 1 0) 1.0)
                                 ;; Render model
                                 (model/render-scenes scene-renderer planet-render-vars shadow-vars [object-shadow]
                                                      geometry clouds [moved-scene])
                                 ;; Render planet with cloud overlay
                                 (planet/render-planet planet-renderer planet-render-vars shadow-vars [object-shadow] geometry clouds
                                                       (planet/get-current-tree tile-tree))
                                 ;; Render atmosphere with cloud overlay
                                 (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars geometry clouds))))
                           (with-culling :sfsim.render/noculling
                             (when @menu
                               (@menu gui @window-width @window-height))
                             (swap! frametime (fn [^double x] (+ (* 0.95 x) (* 0.05 ^double dt))))
                             (when (not playback)
                               (stick gui @state)
                               (info gui @window-height
                                     (format "\rheight = %10.1f m, speed = %7.1f m/s, ctrl: %s, fps = %6.1f%s%s%s"
                                             (- (mag object-position) ^double earth-radius)
                                             (:sfsim.physics/display-speed @physics-state)
                                             (if (@state :sfsim.input/rcs) "RCS" "aerofoil")
                                             (/ 1.0 ^double @frametime)
                                             (if (@state :sfsim.input/brake) ", brake" (if (@state :sfsim.input/parking-brake) ", parking brake" ""))
                                             (if (@state :sfsim.input/air-brake) ", air brake" "")
                                             (if (@state :sfsim.input/pause) ", pause" ""))))
                             (gui/render-nuklear-gui gui @window-width @window-height)))
          (destroy-texture clouds)
          (clouds/destroy-cloud-geometry geometry)
          (model/destroy-scene-shadow-map object-shadow)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          (when playback
            (let [buffer (java.nio.ByteBuffer/allocateDirect (* 4 ^long @window-width ^long @window-height))
                  data   (byte-array (* 4 ^long @window-width ^long @window-height))]
              (GL11/glFlush)
              (GL11/glFinish)
              (GL11/glReadPixels 0 0 ^long @window-width ^long @window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
              (.get buffer data)
              (spit-png (format "frame%06d.png" @frame-index) {:sfsim.image/data data
                                                               :sfsim.image/width @window-width
                                                               :sfsim.image/height @window-height
                                                               :sfsim.image/channels 4} true)
              (swap! frame-index inc))))
        (reset! old-state @state)
        (Nuklear/nk_input_begin (:sfsim.gui/context gui))
        (GLFW/glfwPollEvents)
        (swap! event-buffer joysticks-poll)
        (Nuklear/nk_input_end (:sfsim.gui/context gui))
        (swap! state process-events @event-buffer input-handler)
        (reset! event-buffer (make-event-buffer))
        (swap! n inc)
        (swap! t0 + dt))))
  (planet/destroy-tile-tree tile-tree)
  (model/destroy-scene scene)
  (model/destroy-scene-shadow-renderer scene-shadow-renderer)
  (model/destroy-scene-renderer scene-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (clouds/destroy-cloud-renderer cloud-renderer)
  (model/destroy-joined-geometry-renderer joined-geometry-renderer)
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (gui/destroy-nuklear-gui gui)
  (gui/destroy-font-texture bitmap-font)
  (destroy-window window)
  (jolt/jolt-destroy)
  (GLFW/glfwTerminate)
  (when (and (not playback) @recording)
    (spit "recording.edn" (with-out-str (pprint @recording))))
  (catch Exception e
         (log/error e "Exception in main function")
         (log/info "aborting sfsim" version)
         (System/exit 1)))
  (log/info "terminating sfsim" version)
  (System/exit 0))
