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
    [sfsim.config :as config]
    [sfsim.gui :as gui]
    [sfsim.jolt :as jolt]
    [sfsim.matrix :refer (transformation-matrix rotation-matrix quaternion->matrix get-translation get-translation get-translation)]
    [sfsim.model :as model]
    [sfsim.planet :as planet]
    [sfsim.clock :refer (start-clock elapsed-time)]
    [sfsim.physics :as physics]
    [sfsim.quadtree :as quadtree]
    [sfsim.quaternion :as q]
    [sfsim.render :refer (make-window destroy-window onscreen-render quad-splits-orientations with-culling)]
    [sfsim.graphics :as graphics]
    [sfsim.image :refer (spit-png)]
    [sfsim.audio :as audio]
    [sfsim.input :refer (make-event-buffer make-initial-state read-joystick-config process-events joysticks-poll ->InputHandler
                         char-callback key-callback cursor-pos-callback mouse-button-callback scroll-callback)])
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

(def monitor (GLFW/glfwGetPrimaryMonitor))
(def mode (GLFW/glfwGetVideoMode monitor))
(def window-width (.width ^GLFWVidMode mode))
(def window-height (.height ^GLFWVidMode mode))

(def window (make-window "sfsim" window-width window-height false))

(def graphics (graphics/make-graphics ["venturestar.glb"] (:sfsim.model/object-radius config/model-config)))

(def gltf-to-aerodynamic (rotation-matrix aerodynamics/gltf-to-aerodynamic))

(def model (first (:sfsim.graphics/models graphics)))
(def convex-hulls (update (model/empty-meshes-to-points model) :sfsim.model/transform #(mulm gltf-to-aerodynamic %)))

(def bsp-tree (update (model/get-bsp-tree model "BSP") :sfsim.model/transform #(mulm gltf-to-aerodynamic %)))

(def tile-tree (planet/make-tile-tree))

(def split-orientations (quad-splits-orientations (:sfsim.planet/tilesize config/planet-config) 8))
(def surface (quadtree/distance-to-surface config/planet-config split-orientations))


(def event-buffer (atom (make-event-buffer)))


(GLFW/glfwSetCharCallback window (char-callback event-buffer))
(GLFW/glfwSetKeyCallback window (key-callback event-buffer))
(GLFW/glfwSetCursorPosCallback window (cursor-pos-callback event-buffer))
(GLFW/glfwSetMouseButtonCallback window (mouse-button-callback event-buffer))
(GLFW/glfwSetScrollCallback window (scroll-callback event-buffer))


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
        input-state         (-> (make-initial-state) read-joystick-config)
        convex-hulls-join   (jolt/compound-of-convex-hulls-settings convex-hulls 0.1 (* 26.87036336765512 1.25))
        body                (jolt/create-and-add-dynamic-body convex-hulls-join (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
        mass                (jolt/get-mass body)
        thrust              (* ^double mass 25.0)
        elevation           (:sfsim.model/elevation config/model-config)
        physics-state       (-> (physics/make-physics-state body)
                                (physics/initialize-wheels model)
                                (physics/initialize-thrusters model)
                                (physics/set-geographic surface config/planet-config elevation longitude latitude 0.0)
                                (physics/set-julian-date-ut (astro/julian-date jd-ut)))
        camera-state        (camera/make-camera-state)
        gui-state           {:sfsim.gui/menu nil
                             :sfsim.gui/window-width window-width
                             :sfsim.gui/window-height window-height
                             :sfsim.gui/fullscreen false}
        buffer-initial-size (* 4 1024)
        bitmap-font         (gui/setup-font-texture (gui/make-bitmap-font "resources/fonts/b612.ttf" 512 512 18))
        gui                 (gui/make-nuklear-gui (:sfsim.gui/font bitmap-font) buffer-initial-size)
        audio-state         (audio/make-audio-state)
        state               (atom {:gui gui-state
                                   :input input-state
                                   :physics physics-state
                                   :camera camera-state
                                   :audio audio-state
                                   :surface surface
                                   :window window})
        recording           (atom (and (.exists (java.io.File. "recording.edn"))  ; Initialize recording with "echo [] > recording.edn"
                                       (clojure.edn/read-string (slurp "recording.edn"))))]
    (start-clock)
    (jolt/set-friction body 0.8)
    (jolt/set-restitution body 0.25)
    (jolt/optimize-broad-phase)
    (gui/nuklear-dark-style gui)
    (while (and (not (GLFW/glfwWindowShouldClose window)) (or (not playback) (< ^long @frame-counter (count @recording))))
      (when (not= (-> @state :input :sfsim.input/fullscreen) (-> @state :gui :sfsim.gui/fullscreen))
        (let [fullscreen     (-> @state :input :sfsim.input/fullscreen)
              monitor        (GLFW/glfwGetPrimaryMonitor)
              mode           (GLFW/glfwGetVideoMode monitor)
              desktop-width  (.width ^GLFWVidMode mode)
              desktop-height (.height ^GLFWVidMode mode)]
          (if fullscreen
            (GLFW/glfwSetWindowMonitor window monitor 0 0 desktop-width desktop-height GLFW/GLFW_DONT_CARE)
            (GLFW/glfwSetWindowMonitor window 0 (quot (- desktop-width 854) 2) (quot (- desktop-height 480) 2)
                                       window-width window-height GLFW/GLFW_DONT_CARE))
          (swap! state assoc-in [:gui :sfsim.gui/fullscreen] fullscreen)))
      (let [w (int-array 1)
            h (int-array 1)]
        (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
        (swap! state assoc-in [:gui :sfsim.gui/window-width] (aget w 0))
        (swap! state assoc-in [:gui :sfsim.gui/window-height] (aget h 0)))
      (let [dt (if fix-fps (elapsed-time (/ 1.0 ^double fix-fps) (/ 1.0 ^double fix-fps)) (elapsed-time))
            window-width (-> @state :gui :sfsim.gui/window-width)
            window-height (-> @state :gui :sfsim.gui/window-height)]
        (planet/update-tile-tree (:sfsim.graphics/planet-renderer graphics) tile-tree window-width
                                 (physics/get-position :sfsim.physics/surface (:physics @state)))
        (if (-> @state :input :sfsim.input/menu)
          (swap! state update-in [:gui :sfsim.gui/menu] #(or % gui/main-dialog))
          (swap! state assoc-in [:gui :sfsim.gui/menu] nil))
        (if playback
          (let [frame (nth @recording @frame-counter)]
            (swap! state update :physics physics/load-state (:physics frame))
            (swap! state assoc :camera (:camera frame)))
          (do
            (when (not (-> @state :input :sfsim.input/pause))
              (swap! state update :physics physics/simulation-step (-> @state :input :sfsim.input/controls) dt
                     config/planet-config split-orientations thrust))
            (swap! state update :camera camera/camera-step (:physics @state) (-> @state :input :sfsim.input/camera) dt)
            (swap! state update :audio audio/update-state (:physics @state) (:input @state) (:camera @state))
            (when (and @recording (not (-> @state :input :sfsim.input/pause)))
              (let [frame {:physics (physics/save-state (:physics @state)) :camera (:camera @state)}]
                (swap! recording conj frame)))))
        (let [object-position    (physics/get-position :sfsim.physics/surface (:physics @state))
              earth-radius       (:sfsim.planet/radius config/planet-config)
              height             (- (mag object-position) ^double earth-radius)
              pressure           (/ (atmosphere/pressure-at-height height) (atmosphere/pressure-at-height 0.0))
              object-orientation (physics/get-orientation :sfsim.physics/surface (:physics @state))
              [origin camera-orientation] ((juxt :sfsim.camera/position :sfsim.camera/orientation)
                                           (camera/get-camera-pose (:camera @state) (:physics @state)))
              jd-ut              (physics/get-julian-date-ut (:physics @state))
              icrs-to-earth      (inverse (astro/earth-to-icrs jd-ut))
              sun-pos            (earth-sun jd-ut)
              light-direction    (normalize (mulv icrs-to-earth sun-pos))
              model-vars         (model/make-model-vars (:sfsim.physics/offset-seconds (:physics @state)) pressure
                                                        (:sfsim.physics/throttle (:physics @state)))
              camera-to-world    (transformation-matrix (quaternion->matrix camera-orientation) origin)
              world-to-object    (inverse (transformation-matrix (quaternion->matrix object-orientation) object-position))
              camera-to-object   (mulm world-to-object camera-to-world)
              object-origin      (get-translation camera-to-object)
              plume-transforms   (physics/active-rcs-transforms (:physics @state) (model/bsp-render-order bsp-tree object-origin))
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
                                     (first (:sfsim.graphics/scenes graphics))
                                     (model/animations-frame
                                       model
                                       {"GearLeft" (nth gear-animation 0)
                                        "GearRight" (nth gear-animation 1)
                                        "GearFront" (nth gear-animation 2)
                                        "WheelLeft" (nth wheel-animation 0)
                                        "WheelRight" (nth wheel-animation 1)
                                        "WheelFront" (nth wheel-animation 2)})))
              frame              (graphics/prepare-frame (assoc graphics :sfsim.graphics/scenes [wheels-scene]) model-vars
                                                         (planet/get-current-tree tile-tree) window-width window-height
                                                         origin camera-orientation light-direction object-position
                                                          object-orientation plume-transforms opacity-base)]
          (onscreen-render window
                           (graphics/render-frame graphics frame (planet/get-current-tree tile-tree))
                           (with-culling :sfsim.render/noculling
                             (let [menu (-> @state :gui :sfsim.gui/menu)]
                               (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR
                                                      (if menu GLFW/GLFW_CURSOR_NORMAL GLFW/GLFW_CURSOR_HIDDEN))
                               (when menu (swap! state menu gui window-width window-height)))
                             (when (not playback)
                               (let [controls (-> @state :input :sfsim.input/controls)]
                                 (gui/flight-controls-display controls gui)
                                 (gui/information-display gui window-height @state @frametime)))
                             (gui/render-nuklear-gui gui window-width window-height)))
          (graphics/finalise-frame frame)
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
        (Nuklear/nk_input_begin (:sfsim.gui/context gui))
        (GLFW/glfwPollEvents)
        (swap! event-buffer joysticks-poll)
        (Nuklear/nk_input_end (:sfsim.gui/context gui))
        (swap! state update :input process-events @event-buffer (->InputHandler gui))
        (reset! event-buffer (make-event-buffer))
        (swap! frametime (fn [^double x] (+ (* 0.95 x) (* 0.05 ^double dt))))
        (swap! frame-counter inc)))
    (planet/destroy-tile-tree tile-tree)
    (graphics/destroy-graphics graphics)
    (audio/destroy-audio-state audio-state)
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
