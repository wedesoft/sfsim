;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-input
    (:require
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [midje.sweet :refer :all]
      [sfsim.input :refer :all :as input])
    (:import
      [org.lwjgl.glfw
       GLFW]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Process character events"
       (let [event-buffer (make-event-buffer)
             playback     (atom [])
             handler      (reify InputHandlerProtocol
                                 (process-char [_this codepoint] (swap! playback conj codepoint)))
             handle-one   (reify InputHandlerProtocol
                                 (process-char [_this codepoint] (swap! playback conj codepoint) false))]
         (process-events event-buffer handler) => []
         @playback => []
         (process-events (add-char-event event-buffer 0x20) handler)
         @playback => [0x20]
         (reset! playback [])
         (-> event-buffer (add-char-event 0x61) (add-char-event 0x62) (process-events handler)) => []
         @playback => [0x61 0x62]
         (reset! playback [])
         (-> event-buffer (add-char-event 0x61) (add-char-event 0x62) (process-events handle-one) count) => 1
         @playback => [0x61]))


(facts "Process key events"
       (let [event-buffer (make-event-buffer)
             playback (atom [])
             handler  (reify InputHandlerProtocol
                             (process-key [_this k action mods] (swap! playback conj {:key k :action action :mods mods})))]
         (process-events event-buffer handler) => []
         (-> event-buffer (add-key-event GLFW/GLFW_KEY_A GLFW/GLFW_PRESS 0) (process-events handler)) => []
         @playback => [{:key GLFW/GLFW_KEY_A :action GLFW/GLFW_PRESS :mods 0}]))


(def gui-key (atom nil))


(facts "Test GUI tab focus"
       (let [state (make-initial-state)
             gui   {:sfsim.gui/context :ctx}]
         (@state :sfsim.input/focus) => 0
         (menu-key GLFW/GLFW_KEY_TAB state gui GLFW/GLFW_PRESS 0)
         (@state :sfsim.input/focus-new) => 1
         (menu-key GLFW/GLFW_KEY_TAB state gui GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (@state :sfsim.input/focus-new) => -1
         (@state :sfsim.input/menu) => false
         (menu-key GLFW/GLFW_KEY_ESCAPE state gui GLFW/GLFW_PRESS 0) => false
         (@state :sfsim.input/menu) => true
         (menu-key GLFW/GLFW_KEY_ESCAPE state gui GLFW/GLFW_PRESS 0) => false
         (@state :sfsim.input/menu) => false))


(defn menu-key-mock
  [k state _gui action _mods]
  (reset! gui-key k)
  (when (and (= action GLFW/GLFW_PRESS) (= k GLFW/GLFW_KEY_ESCAPE))
    (swap! state update :sfsim.input/menu not)
    false))


(facts "Test the integrated behaviour of some keys"
       (with-redefs [input/menu-key menu-key-mock]
         (let [event-buffer (make-event-buffer)
               state        (make-initial-state)
               gui          {:sfsim.gui/context :ctx}
               handler      (->InputHandler state gui (atom default-mappings))]
           ; Test gear up
           (:sfsim.input/gear-down @state) => true
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (process-events handler))
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events handler))
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events handler))
           (:sfsim.input/gear-down @state) => false
           ; Test fullscreen
           (:sfsim.input/fullscreen @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ENTER GLFW/GLFW_PRESS GLFW/GLFW_MOD_ALT)
               (add-key-event GLFW/GLFW_KEY_ENTER GLFW/GLFW_RELEASE GLFW/GLFW_MOD_ALT)
               (process-events handler))
           (:sfsim.input/fullscreen @state) => true
           ; Test menu toggle
           (:sfsim.input/menu @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events handler))
           (:sfsim.input/menu @state) => true
           ; Hiding menu should postpone processing of remaining events
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events handler)
               count) => 3
           (:sfsim.input/menu @state) => false
           ; Showing menu should postpone processing of remaining events
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events handler)
               count) => 3
           (:sfsim.input/menu @state) => true
           ; Test no gear operation when menu is shown
           (swap! state assoc :sfsim.input/menu true)
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events handler))
           (:sfsim.input/gear-down @state) => false
           ; Test no fullscreen toggle when menu is shown
           (:sfsim.input/fullscreen @state) => true
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_RELEASE 0)
               (process-events handler))
           (:sfsim.input/fullscreen @state) => true
           ; Use alternate method for handling keys when menu is shown
           @gui-key => GLFW/GLFW_KEY_F)))


(facts "Test camera keys"
       (let [state    (make-initial-state)
             mappings (:sfsim.input/keyboard default-mappings)]
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_2 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-x @state) => 0.5
         (-> GLFW/GLFW_KEY_KP_2 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_8 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-x @state) => -0.5
         (-> GLFW/GLFW_KEY_KP_8 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_6 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-y @state) => 0.5
         (-> GLFW/GLFW_KEY_KP_6 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_4 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-y @state) => -0.5
         (-> GLFW/GLFW_KEY_KP_4 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_1 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-z @state) => 0.5
         (-> GLFW/GLFW_KEY_KP_1 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (-> GLFW/GLFW_KEY_KP_3 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-rotate-z @state) => -0.5
         (-> GLFW/GLFW_KEY_KP_3 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (:sfsim.input/camera-distance-change @state) => 0.0
         (-> GLFW/GLFW_KEY_COMMA mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-distance-change @state) => 1.0
         (-> GLFW/GLFW_KEY_COMMA mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-distance-change @state) => 0.0
         (-> GLFW/GLFW_KEY_PERIOD mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/camera-distance-change @state) => -1.0
         (-> GLFW/GLFW_KEY_PERIOD mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/camera-distance-change @state) => 0.0))


(facts "Test some simulator key bindings directly"
       (let [state    (make-initial-state)
             mappings (:sfsim.input/keyboard default-mappings)]
         ; Pause
         (:sfsim.input/pause @state) => true
         (-> GLFW/GLFW_KEY_P mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_P mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/pause @state) => false
         (-> GLFW/GLFW_KEY_P mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_P mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/pause @state) => true
         ; Brakes
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_PRESS 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         ; Parking brakes
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false true]
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_PRESS 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         ; Aileron
         (:sfsim.input/aileron @state) => 0.0
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 0.0625
         (swap! state assoc :sfsim.input/aileron 0.0)
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => -0.0625
         (swap! state assoc :sfsim.input/aileron 0.0)
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => (* 2 -0.0625)
         (-> GLFW/GLFW_KEY_KP_5 mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_KP_5 mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 0.0
         (swap! state assoc :sfsim.input/aileron -1.0)
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => -1.0
         (swap! state assoc :sfsim.input/aileron 1.0)
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 1.0
         ; Elevator
         (:sfsim.input/elevator @state) => 0.0
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => 0.0625
         (swap! state assoc :sfsim.input/elevator 0.0)
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => -0.0625
         (swap! state assoc :sfsim.input/elevator -1.0)
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => -1.0
         (swap! state assoc :sfsim.input/elevator 1.0)
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => 1.0
         ; Rudder
         (:sfsim.input/rudder @state) => 0.0
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => 0.0625
         (swap! state assoc :sfsim.input/rudder 0.0)
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => -0.0625
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_CONTROL))
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_CONTROL))
         (:sfsim.input/rudder @state) => 0.0
         (swap! state assoc :sfsim.input/rudder -1.0)
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => -1.0
         (swap! state assoc :sfsim.input/rudder 1.0)
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => 1.0
         ; Throttle
         (:sfsim.input/throttle @state) => 0.0
         (-> GLFW/GLFW_KEY_R mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_R mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/throttle @state) => 0.0625
         (-> GLFW/GLFW_KEY_F mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_F mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         (:sfsim.input/throttle @state) => 0.0
         (-> GLFW/GLFW_KEY_F mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_F mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         (:sfsim.input/throttle @state) => 0.0
         (swap! state assoc :sfsim.input/throttle 1.0)
         (-> GLFW/GLFW_KEY_R mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_R mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/throttle @state) => 1.0
         (:sfsim.input/air-brake @state) => false
         (-> GLFW/GLFW_KEY_SLASH mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_SLASH mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/air-brake @state) => true
         (-> GLFW/GLFW_KEY_SLASH mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_SLASH mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/air-brake @state) => false
         ; Toggle aerofoil surfaces/RCS thrusters
         (:sfsim.input/rcs @state) => false
         (-> GLFW/GLFW_KEY_BACKSLASH mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_BACKSLASH mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs @state) => true
         (-> GLFW/GLFW_KEY_BACKSLASH mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_BACKSLASH mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs @state) => false
         ; RCS roll
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-roll @state) => 0.0
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-roll @state) => 1.0
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-roll @state) => 1.0
         (-> GLFW/GLFW_KEY_A mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs-roll @state) => 0.0
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-roll @state) => -1.0
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-roll @state) => -1.0
         (-> GLFW/GLFW_KEY_D mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs-roll @state) => 0.0
         ; RCS pitch
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-pitch @state) => 0.0
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-pitch @state) => 1.0
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-pitch @state) => 1.0
         (-> GLFW/GLFW_KEY_W mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs-pitch @state) => 0.0
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-pitch @state) => -1.0
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-pitch @state) => -1.0
         (-> GLFW/GLFW_KEY_S mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs-pitch @state) => 0.0
         ; RCS yaw
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-yaw @state) => 0.0
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-yaw @state) => 1.0
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-yaw @state) => 1.0
         (-> GLFW/GLFW_KEY_Q mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rcs-yaw @state) => 0.0
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (:sfsim.input/rcs-yaw @state) => -1.0
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         (:sfsim.input/rcs-yaw @state) => -1.0
         (-> GLFW/GLFW_KEY_E mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         ))


(facts "Process mouse events"
       (let [event-buffer (make-event-buffer)
             playback     (atom [])
             handler      (reify InputHandlerProtocol
                                 (process-mouse-button [_this button x y action mods]
                                   (swap! playback conj {:button button :action action :mods mods :x x :y y}))
                                 (process-mouse-move [_this x y]
                                   (swap! playback conj {:x x :y y})))]
         (process-events (add-mouse-button-event event-buffer GLFW/GLFW_MOUSE_BUTTON_LEFT 160 120 GLFW/GLFW_PRESS 0) handler)
         @playback => [{:button GLFW/GLFW_MOUSE_BUTTON_LEFT :action GLFW/GLFW_PRESS :mods 0 :x 160 :y 120}]
         (reset! playback [])
         (process-events (add-mouse-move-event event-buffer 100 60) handler)
         @playback => [{:x 100 :y 60}]))


(facts "Dead center zone for joystick axis"
       (dead-zone-continuous 0.0 1.0) => 1.0
       (dead-zone-continuous 0.0 -1.0) => -1.0
       (dead-zone-continuous 0.5 0.5) => 0.0
       (dead-zone-continuous 0.5 0.75) => 0.5
       (dead-zone-continuous 0.5 -0.75) => -0.5
       (dead-zone-continuous 1.0 1.0) => 0.0
       (dead-zone-three-state 0.0 1.0) => 1.0
       (dead-zone-three-state 0.0 -1.0) => -1.0
       (dead-zone-three-state 0.5 0.5) => 0.0
       (dead-zone-three-state 0.5 0.75) => 1.0
       (dead-zone-three-state 0.5 -0.75) => -1.0
       (dead-zone-three-state 1.0 1.0) => 0.0)


(facts "Dead margins for throttle stick"
       (dead-margins 0.0 0.0) => 0.0
       (dead-margins 0.0 1.0) => 1.0
       (dead-margins 0.5 0.5) => 1.0
       (dead-margins 0.5 0.25) => 0.5
       (dead-margins 0.5 1.0) => 1.0
       (dead-margins 0.5 -0.25) => -0.5
       (dead-margins 0.5 -1.0) => -1.0)


(facts "Get joystick and axis for a mapping"
       (let [mappings {:sfsim.input/joysticks {:sfsim.input/devices {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/aileron}}
                                                                     "Throttle" {:sfsim.input/axes {1 :sfsim.input/throttle}}}}}]
         (get-joystick-sensor-for-mapping {} ["Gamepad"] :sfsim.input/axes :sfsim.input/aileron) => nil
         (get-joystick-sensor-for-mapping mappings ["Gamepad"] :sfsim.input/axes :sfsim.input/aileron) => ["Gamepad" 0]
         (get-joystick-sensor-for-mapping mappings ["Gamepad" "Throttle"] :sfsim.input/axes :sfsim.input/throttle) => ["Throttle" 1]))


(facts "Get joystick and button for a mapping"
       (let [mappings {:sfsim.input/joysticks {:sfsim.input/devices {"Gamepad" {:sfsim.input/buttons {0 :sfsim.input/gear}}
                                                                     "Throttle" {:sfsim.input/buttons {1 :sfsim.input/brake}}}}}]
         (get-joystick-sensor-for-mapping {} ["Gamepad"] :sfsim.input/buttons :sfsim.input/gear) => nil
         (get-joystick-sensor-for-mapping mappings ["Gamepad"] :sfsim.input/buttons :sfsim.input/gear) => ["Gamepad" 0]
         (get-joystick-sensor-for-mapping mappings ["Gamepad" "Throttle"] :sfsim.input/buttons :sfsim.input/brake) => ["Throttle" 1]))


(facts "Process joystick axis events"
       (let [event-buffer             (make-event-buffer)
             playback                 (atom [])
             mock-handler             (reify InputHandlerProtocol
                                             (process-joystick-axis [_this device axis value _moved]
                                               (swap! playback conj {:device device :axis axis :value value})))
             state                    (make-initial-state)
             gui                      {:sfsim.gui/context :ctx}
             axis-state               (atom {})
             mappings                 {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/aileron
                                                                                              1 :sfsim.input/elevator
                                                                                              2 :sfsim.input/rudder}}}
                                                               :sfsim.input/dead-zone 0.0}}
             mappings-inv             {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/aileron-inverted
                                                                                              1 :sfsim.input/elevator-inverted
                                                                                              2 :sfsim.input/rudder-inverted}}}
                                                               :sfsim.input/dead-zone 0.0}}
             mappings-zn              {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/aileron }}}
                                                               :sfsim.input/dead-zone 0.5}}
             map-throttle             {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/throttle}}}
                                                               :sfsim.input/dead-zone 0.0}}
             map-throttle-incr        {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/throttle-increment}}}
                                                               :sfsim.input/dead-zone 0.0}}
             map-throttle-zn          {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/throttle}}}
                                                               :sfsim.input/dead-zone 0.5}}
             map-throttle-incr-zn     {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/axes {0 :sfsim.input/throttle-increment}}}
                                                               :sfsim.input/dead-zone 0.5}}
             handler                  (->InputHandler state gui (atom mappings))
             handler-inv              (->InputHandler state gui (atom mappings-inv))
             handler-zn               (->InputHandler state gui (atom mappings-zn))
             handler-throttle         (->InputHandler state gui (atom map-throttle))
             handler-throttle-incr    (->InputHandler state gui (atom map-throttle-incr))
             handler-throttle-zn      (->InputHandler state gui (atom map-throttle-zn))
             handler-throttle-incr-zn (->InputHandler state gui (atom map-throttle-incr-zn))]
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" []) mock-handler)
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75]) mock-handler)
         @playback => [{:device "Gamepad" :axis 0 :value -0.5} {:device "Gamepad" :axis 1 :value -0.75}]
         ; Aerofoil surfaces
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/aileron @state) => 0.5
         (:sfsim.input/elevator @state) => 0.75
         (:sfsim.input/rudder @state) => -0.5
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler-inv)
         (:sfsim.input/aileron @state) => -0.5
         (:sfsim.input/elevator @state) => -0.75
         (:sfsim.input/rudder @state) => 0.5
         ; RCS thrusters
         (swap! state assoc :sfsim.input/rcs true)
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/rcs-roll @state) => 1.0
         (:sfsim.input/rcs-pitch @state) => 1.0
         (:sfsim.input/rcs-yaw @state) => -1.0
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler-inv)
         (:sfsim.input/rcs-roll @state) => -1.0
         (:sfsim.input/rcs-pitch @state) => -1.0
         (:sfsim.input/rcs-yaw @state) => 1.0
         (swap! state assoc :sfsim.input/rcs false)
         ; Dead zone testing
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.75]) handler-zn)
         (:sfsim.input/aileron @state) => 0.5
         ; Throttle
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.75]) handler-throttle)
         (:sfsim.input/throttle @state) => 0.125
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.25]) handler-throttle-zn)
         (:sfsim.input/throttle @state) => 0.25
         (swap! state assoc :sfsim.input/throttle 0.0)
         ; Incremental throttle
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-1.0]) handler-throttle-incr)
         (:sfsim.input/throttle @state) => 0.0625
         (swap! state assoc :sfsim.input/throttle 0.0)
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0]) handler-throttle-incr)
         (:sfsim.input/throttle @state) => 0.0
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.75]) handler-throttle-incr-zn)
         (:sfsim.input/throttle @state) => (* 0.5 0.0625)
         (process-events (add-joystick-axis-state event-buffer axis-state "Unknown" [0.0 0.0]) handler)) ())


(facts "Process joystick button events"
       (let [event-buffer             (make-event-buffer)
             playback                 (atom [])
             button-state             (atom {})
             mock-handler             (reify InputHandlerProtocol
                                             (process-joystick-button [_this device button action]
                                               (swap! playback conj {:device device :button button :action action})))
             mappings                 {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/buttons {1 :sfsim.input/gear
                                                                                                 2 :sfsim.input/brake
                                                                                                 3 :sfsim.input/parking-brake
                                                                                                 4 :sfsim.input/air-brake}}}}}
             state                    (make-initial-state)
             gui                      {:sfsim.gui/context :ctx}
             handler                  (->InputHandler state gui (atom mappings))]
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" []) mock-handler)
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0]) mock-handler)
         @playback => []
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) mock-handler)
         @playback => [{:device "Gamepad" :button 1 :action GLFW/GLFW_PRESS}]
         (reset! playback [])
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) mock-handler)
         @playback => [{:device "Gamepad" :button 1 :action GLFW/GLFW_REPEAT}]
         (reset! playback [])
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0]) mock-handler)
         @playback => [{:device "Gamepad" :button 1 :action GLFW/GLFW_RELEASE}]
         (:sfsim.input/gear-down @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) handler)
         (:sfsim.input/gear-down @state) => false
         (:sfsim.input/brake @state) => false
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (:sfsim.input/brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (:sfsim.input/brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0]) handler)
         (:sfsim.input/brake @state) => false
         (:sfsim.input/parking-brake @state) => false
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 1]) handler)
         (:sfsim.input/parking-brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0]) handler)
         (:sfsim.input/parking-brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1 0]) handler)
         (:sfsim.input/parking-brake @state) => false
         (:sfsim.input/air-brake @state)=> false
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 1]) handler)
         (:sfsim.input/air-brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 0]) handler)
         (:sfsim.input/air-brake @state) => true
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 1]) handler)
         (:sfsim.input/air-brake @state) => false
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 0]) handler)
         (:sfsim.input/air-brake @state) => false))


(facts "Recording last active joystick axis or button"
       (let [event-buffer (make-event-buffer)
             gui          {:sfsim.gui/context :ctx}
             state        (atom {:sfsim.input/menu true})
             button-state (atom {})
             axis-state   (atom {})
             mappings     {}
             handler      (->InputHandler state gui (atom mappings))]
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0]) handler)
         (@state :sfsim.input/last-joystick-button) => nil
         (process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (@state :sfsim.input/last-joystick-button) => ["Gamepad" 2]
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => nil
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => ["Gamepad" 0]
         (process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => ["Gamepad" 0]))
