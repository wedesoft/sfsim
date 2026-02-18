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
    [clojure.math :refer (PI to-radians)]
    [clojure.tools.logging :as log]
    [clojure.edn]
    [clojure.pprint :refer (pprint)]
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
    [sfsim.gui :as gui]
    [sfsim.jolt :as jolt]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix get-translation get-translation)]
    [sfsim.model :as model]
    [sfsim.opacity :as opacity]
    [sfsim.planet :as planet]
    [sfsim.clock :refer (start-clock elapsed-time)]
    [sfsim.physics :as physics]
    [sfsim.quadtree :as quadtree]
    [sfsim.quaternion :as q]
    [sfsim.render :refer (make-window destroy-window clear onscreen-render with-stencils with-stencil-op-ref-and-mask
                          joined-render-vars quad-splits-orientations with-depth-test with-culling)]
    [sfsim.image :refer (spit-png)]
    [sfsim.texture :refer (destroy-texture)]
    [sfsim.input :refer (make-event-buffer make-initial-state process-events joysticks-poll ->InputHandler
                                           char-callback key-callback cursor-pos-callback mouse-button-callback)])
  (:import
    (org.lwjgl.glfw
      GLFW
      GLFWVidMode)
    (org.lwjgl.opengl
      GL11)
    (org.lwjgl.nuklear
      Nuklear)))


(try

(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(log/info "starting sfsim" version)


(when (.exists (io/file ".schemas"))
  (dev/start! {:report (pretty/thrower)}))

; Ensure floating point numbers use a dot as decimal separator
(java.util.Locale/setDefault java.util.Locale/US)


(def opacity-base 100.0)

(GLFW/glfwInit)

(jolt/jolt-init)
(jolt/set-gravity (vec3 0 0 0))

(def playback false)
; (def playback true)
(def fix-fps false)
; (def fix-fps 30)

(def window-width (:sfsim.render/window-width config/render-config))
(def window-height (:sfsim.render/window-height config/render-config))

(def window (make-window "sfsim" window-width window-height true))

(def cloud-data (clouds/make-cloud-data config/cloud-config))
(def atmosphere-luts (atmosphere/make-atmosphere-luts config/max-height))
(def shadow-data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data))


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


(def event-buffer (atom (make-event-buffer)))


(GLFW/glfwSetCharCallback window (char-callback event-buffer))
(GLFW/glfwSetKeyCallback window (key-callback event-buffer))
(GLFW/glfwSetCursorPosCallback window (cursor-pos-callback event-buffer))
(GLFW/glfwSetMouseButtonCallback window (mouse-button-callback event-buffer))


; Start with fixed summer date for better illumination.

(catch Exception e
       (log/error e "Exception at startup")
       (log/info "aborting sfsim" version)
       (System/exit 1)))


(defn -main
  "Space flight simulator main function"
  [& _args]
  (try
  (let [frame-counter       (atom 0)
        frametime           (atom 0.25)
        spk                 (astro/make-spk-document "data/astro/de430_1850-2150.bsp")  ; Spacecraft and Planet Kernel (SPK)
        barycenter-sun      (astro/make-spk-segment-interpolator spk 0 10)
        barycenter-earth    (astro/make-spk-segment-interpolator spk 0 3)
        earth-sun           (fn [jd-ut] (sub (barycenter-sun jd-ut) (barycenter-earth jd-ut)))
        jd-ut               {:sfsim.astro/year 2026 :sfsim.astro/month 6 :sfsim.astro/day 22}
        longitude           (to-radians -1.3747)
        latitude            (to-radians 50.9672)
        input-state         (-> (make-initial-state)
                                (assoc-in [:sfsim.input/mappings :sfsim.input/joysticks]
                                          (config/read-user-config "joysticks.edn" {:sfsim.input/dead-zone 0.1})))
        convex-hulls-join   (jolt/compound-of-convex-hulls-settings convex-hulls 0.1 (* 26.87036336765512 1.25))
        body                (jolt/create-and-add-dynamic-body convex-hulls-join (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
        mass                (jolt/get-mass body)
        thrust              (* ^double mass 25.0)
        elevation           (:sfsim.model/elevation config/model-config)
        physics-state       (-> (physics/make-physics-state body)
                                (physics/set-geographic surface config/planet-config elevation longitude latitude 0.0)
                                (physics/set-julian-date-ut (astro/julian-date jd-ut)))
        camera-state        (camera/make-camera-state)
        gui-state           {:sfsim.gui/menu nil
                             :sfsim.gui/window-width window-width
                             :sfsim.gui/window-height window-height}
        buffer-initial-size (* 4 1024)
        bitmap-font         (gui/setup-font-texture (gui/make-bitmap-font "resources/fonts/b612.ttf" 512 512 18))
        gui                 (gui/make-nuklear-gui (:sfsim.gui/font bitmap-font) buffer-initial-size)
        state               (atom {:gui gui-state
                                :input input-state
                                :physics physics-state
                                :camera camera-state
                                :surface surface
                                :window window})
        recording           (atom (and (.exists (java.io.File. "recording.edn"))  ; Initialize recording using "echo [] > recording.edn"
                                       (clojure.edn/read-string (slurp "recording.edn"))))
        old-state           (atom @state)]
    (start-clock)
    (jolt/set-friction body 0.8)
    (jolt/set-restitution body 0.25)
    (jolt/optimize-broad-phase)
    (gui/nuklear-dark-style gui)
    (while (and (not (GLFW/glfwWindowShouldClose window)) (or (not playback) (< ^long @frame-counter (count @recording))))
      (when (not= (-> @state :input :sfsim.input/fullscreen) (-> @old-state :input :sfsim.input/fullscreen))
        (let [monitor (GLFW/glfwGetPrimaryMonitor)
              mode (GLFW/glfwGetVideoMode monitor)
              desktop-width (.width ^GLFWVidMode mode)
              desktop-height (.height ^GLFWVidMode mode)]
          (if (-> @state :input :sfsim.input/fullscreen)
            (GLFW/glfwSetWindowMonitor window monitor 0 0 desktop-width desktop-height GLFW/GLFW_DONT_CARE)
            (GLFW/glfwSetWindowMonitor window 0 (quot (- desktop-width 854) 2) (quot (- desktop-height 480) 2) 854 480 GLFW/GLFW_DONT_CARE))))
      (let [w (int-array 1)
            h (int-array 1)]
        (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
        (swap! state assoc-in [:gui :sfsim.gui/window-width] (aget w 0))
        (swap! state assoc-in [:gui :sfsim.gui/window-height] (aget h 0)))
      (let [dt (if fix-fps (elapsed-time (/ 1.0 ^double fix-fps) (/ 1.0 ^double fix-fps)) (elapsed-time))
            window-width (-> @state :gui :sfsim.gui/window-width)
            window-height (-> @state :gui :sfsim.gui/window-height)]
        (planet/update-tile-tree planet-renderer tile-tree window-width
                                 (physics/get-position :sfsim.physics/surface (:physics @state)))
        (if (-> @state :input :sfsim.input/menu)
          (swap! state update-in [:gui :sfsim.gui/menu] #(or % gui/main-dialog))
          (swap! state assoc-in [:gui :sfsim.gui/menu] nil))
        (if playback
          (let [frame (nth @recording @frame-counter)]
            (swap! state update :physics physics/load-state (:physics frame) wheels)
            (swap! state assoc :camera (:camera frame)))
          (do
            (when (not (-> @state :input :sfsim.input/pause))
              (swap! state update :physics physics/simulation-step (-> @state :input :sfsim.input/controls) dt wheels
                     config/planet-config split-orientations thrust))
            (let [speed (mag (physics/get-linear-speed :sfsim.physics/surface (:physics @state)))
                  mode  (if (>= speed 500.0) :sfsim.camera/fast :sfsim.camera/slow)]
              (swap! state update :camera camera/set-mode mode (:physics @state))
              (swap! state update :camera camera/update-camera-pose dt (-> @state :input :sfsim.input/camera)))
            (when (and @recording (not (-> @state :input :sfsim.input/pause)))
              (let [frame {:physics (physics/save-state (:physics @state)) :camera (:camera @state)}]
                (swap! recording conj frame)))))
        (let [object-position    (physics/get-position :sfsim.physics/surface (:physics @state))
              earth-radius       (:sfsim.planet/radius config/planet-config)
              height             (- (mag object-position) ^double earth-radius)
              pressure           (/ (atmosphere/pressure-at-height height) (atmosphere/pressure-at-height 0.0))
              object-orientation (physics/get-orientation :sfsim.physics/surface (:physics @state))
              object-to-world    (transformation-matrix (quaternion->matrix object-orientation) object-position)
              [origin camera-orientation] ((juxt :sfsim.camera/position :sfsim.camera/orientation)
                                           (camera/get-camera-pose (:camera @state) (:physics @state)))
              jd-ut              (physics/get-julian-date-ut (:physics @state))
              icrs-to-earth      (inverse (astro/earth-to-icrs jd-ut))
              sun-pos            (earth-sun jd-ut)
              light-direction    (normalize (mulv icrs-to-earth sun-pos))
              model-vars         (model/make-model-vars (:sfsim.physics/offset-seconds (:physics @state)) pressure
                                                        (:sfsim.physics/throttle (:physics @state)))
              planet-render-vars (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                                 window-width window-height origin camera-orientation
                                                                 light-direction object-position object-orientation model-vars)
              scene-render-vars  (model/make-scene-render-vars config/render-config window-width window-height origin
                                                               camera-orientation light-direction object-position
                                                               object-orientation config/model-config model-vars)
              shadow-render-vars (joined-render-vars planet-render-vars scene-render-vars)
              shadow-vars        (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                     cloud-data shadow-render-vars
                                                                     (planet/get-current-tree tile-tree) opacity-base)
              cloud-render-vars  (clouds/make-cloud-render-vars config/render-config planet-render-vars window-width window-height
                                                                origin camera-orientation light-direction object-position
                                                                object-orientation)
              wheels-scene       (let [wheel-animation (map #(mod (/ ^double % (* 2.0 PI)) 1.0)
                                                            (physics/get-wheel-angles (:physics @state)))
                                       gear-animation
                                       (let [gear (:sfsim.physics/gear (:physics @state))]
                                         (if (= ^double gear 1.0)
                                           (let [suspension (physics/get-suspension (:physics @state))]
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
              render-order       (filterv (physics/active-rcs (:physics @state)) (model/bsp-render-order bsp-tree object-origin))
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
                             (when-let [menu (-> @state :gui :sfsim.gui/menu)]
                                       (swap! state menu gui window-width window-height))
                             (swap! frametime (fn [^double x] (+ (* 0.95 x) (* 0.05 ^double dt))))
                             (when (not playback)
                               (let [controls (-> @state :input :sfsim.input/controls)]
                                 (gui/flight-controls-display controls gui)
                                 (gui/information-display gui window-height @state @frametime)))
                             (gui/render-nuklear-gui gui window-width window-height)))
          (destroy-texture clouds)
          (clouds/destroy-cloud-geometry geometry)
          (model/destroy-scene-shadow-map object-shadow)
          (opacity/destroy-opacity-and-shadow shadow-vars)
          (when playback
            (let [buffer (java.nio.ByteBuffer/allocateDirect (* 4 ^long window-width ^long window-height))
                  data   (byte-array (* 4 ^long window-width ^long window-height))]
              (GL11/glFlush)
              (GL11/glFinish)
              (GL11/glReadPixels 0 0 ^long window-width ^long window-height GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
              (.get buffer data)
              (spit-png (format "frame%06d.png" @frame-counter) {:sfsim.image/data data
                                                                 :sfsim.image/width window-width
                                                                 :sfsim.image/height window-height
                                                                 :sfsim.image/channels 4} true))))
        (reset! old-state @state)
        (Nuklear/nk_input_begin (:sfsim.gui/context gui))
        (GLFW/glfwPollEvents)
        (swap! event-buffer joysticks-poll)
        (Nuklear/nk_input_end (:sfsim.gui/context gui))
        (swap! state update :input process-events @event-buffer (->InputHandler gui))
        (reset! event-buffer (make-event-buffer))
        (swap! frame-counter inc)))
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
      (spit "recording.edn" (with-out-str (pprint @recording)))))
  (catch Exception e
         (log/error e "Exception in main function")
         (log/info "aborting sfsim" version)
         (System/exit 1)))
  (log/info "terminating sfsim" version)
  (System/exit 0))
