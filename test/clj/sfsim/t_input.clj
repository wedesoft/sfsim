;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
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
             handler      (reify InputHandlerProtocol
                                 (process-char [_this state codepoint] (conj state codepoint)))]
         (process-events [] event-buffer handler) => []
         (process-events [] (add-char-event event-buffer 0x20) handler) => [0x20]
         (process-events [] (-> event-buffer (add-char-event 0x61) (add-char-event 0x62)) handler) => [0x61 0x62]))


(facts "Process key events"
       (let [event-buffer (make-event-buffer)
             handler  (reify InputHandlerProtocol
                             (process-key [_this state k action mods] (conj state {:key k :action action :mods mods})))]
         (process-events [] event-buffer handler) => []
         (process-events [] (-> event-buffer (add-key-event GLFW/GLFW_KEY_A GLFW/GLFW_PRESS 0)) handler)
         => [{:key GLFW/GLFW_KEY_A :action GLFW/GLFW_PRESS :mods 0}]))


(facts "Test GUI tab focus"
       (let [state (atom (make-initial-state))
             gui   {:sfsim.gui/context :ctx}]
         (@state :sfsim.input/focus) => 0
         (swap! state menu-key GLFW/GLFW_KEY_TAB gui GLFW/GLFW_PRESS 0)
         (@state :sfsim.input/focus-new) => 1
         (swap! state menu-key GLFW/GLFW_KEY_TAB gui GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (@state :sfsim.input/focus-new) => -1
         (@state :sfsim.input/menu) => false
         (swap! state menu-key GLFW/GLFW_KEY_ESCAPE gui GLFW/GLFW_PRESS 0)
         (@state :sfsim.input/menu) => true
         (swap! state menu-key GLFW/GLFW_KEY_ESCAPE gui GLFW/GLFW_PRESS 0)
         (@state :sfsim.input/menu) => false))


(def gui-key (atom nil))


(defn menu-key-mock
  [state k _gui action _mods]
  (reset! gui-key k)
  (if (and (= action GLFW/GLFW_PRESS) (= k GLFW/GLFW_KEY_ESCAPE))
    (update state :sfsim.input/menu not)
    state))


(facts "Test the integrated behaviour of some keys"
       (with-redefs [input/menu-key menu-key-mock]
         (let [event-buffer (make-event-buffer)
               state        (atom (make-initial-state))
               gui          {:sfsim.gui/context :ctx}
               handler      (->InputHandler gui)]
           ; Test gear up
           (:sfsim.input/gear-down @state) => true
           (swap! state process-events (-> event-buffer (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)) handler)
           (:sfsim.input/gear-down @state) => false
           (swap! state process-events (-> event-buffer (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)) handler)
           (:sfsim.input/gear-down @state) => false
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0))
                  handler)
           (:sfsim.input/gear-down @state) => false
           ; Test fullscreen
           (:sfsim.input/fullscreen @state) => false
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_ENTER GLFW/GLFW_PRESS GLFW/GLFW_MOD_ALT)
                      (add-key-event GLFW/GLFW_KEY_ENTER GLFW/GLFW_RELEASE GLFW/GLFW_MOD_ALT))
                      handler)
           (:sfsim.input/fullscreen @state) => true
           ; Test menu toggle
           (:sfsim.input/menu @state) => false
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0))
                      handler)
           (:sfsim.input/menu @state) => true
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0))
                      handler)
           (:sfsim.input/menu @state) => false
           ; Test no gear operation when menu is shown
           (swap! state assoc :sfsim.input/menu true)
           (:sfsim.input/gear-down @state) => false
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0))
                  handler)
           (:sfsim.input/gear-down @state) => false
           ; Test no fullscreen toggle when menu is shown
           (:sfsim.input/fullscreen @state) => true
           (swap! state process-events
                  (-> event-buffer
                      (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
                      (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_RELEASE 0))
                  handler)
           (:sfsim.input/fullscreen @state) => true
           ; Use alternate method for handling keys when menu is shown
           @gui-key => GLFW/GLFW_KEY_F)))


(facts "Test camera keys"
       (let [state    (atom (make-initial-state))
             mappings (:sfsim.input/keyboard default-mappings)]
         (simulator-key @state nil GLFW/GLFW_PRESS 0) => some?
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_2) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-x @state) => 0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_2) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_8) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-x @state) => -0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_8) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-x @state) => 0.0
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_6) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-y @state) => 0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_6) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_4) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-y @state) => -0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_4) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-y @state) => 0.0
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_1) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-z @state) => 0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_1) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_3) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-rotate-z @state) => -0.5
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_3) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-rotate-z @state) => 0.0
         (:sfsim.input/camera-distance-change @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_COMMA) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-distance-change @state) => 1.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_COMMA) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-distance-change @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_PERIOD) GLFW/GLFW_PRESS 0)
         (:sfsim.input/camera-distance-change @state) => -1.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_PERIOD) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/camera-distance-change @state) => 0.0))


(facts "Test some simulator key bindings directly"
       (let [state    (atom (make-initial-state))
             mappings (:sfsim.input/keyboard default-mappings)]
         ; Pause
         (:sfsim.input/pause @state) => true
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_P) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_P) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/pause @state) => false
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_P) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_P) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/pause @state) => true
         ; Brakes
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_PRESS 0)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_REPEAT 0)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_RELEASE 0)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         ; Parking brakes
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false true]
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_PRESS 0)
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_B) GLFW/GLFW_RELEASE 0)
         ; Aileron
         (:sfsim.input/aileron @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => 0.0625
         (swap! state assoc :sfsim.input/aileron 0.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => -0.0625
         (swap! state assoc :sfsim.input/aileron 0.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_REPEAT 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => (* 2 -0.0625)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_5) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_KP_5) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => 0.0
         (swap! state assoc :sfsim.input/aileron -1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => -1.0
         (swap! state assoc :sfsim.input/aileron 1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/aileron @state) => 1.0
         ; Elevator
         (:sfsim.input/elevator @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/elevator @state) => 0.0625
         (swap! state assoc :sfsim.input/elevator 0.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/elevator @state) => -0.0625
         (swap! state assoc :sfsim.input/elevator -1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/elevator @state) => -1.0
         (swap! state assoc :sfsim.input/elevator 1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/elevator @state) => 1.0
         ; Rudder
         (:sfsim.input/rudder @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rudder @state) => 0.0625
         (swap! state assoc :sfsim.input/rudder 0.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rudder @state) => -0.0625
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_PRESS GLFW/GLFW_MOD_CONTROL)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_CONTROL)
         (:sfsim.input/rudder @state) => 0.0
         (swap! state assoc :sfsim.input/rudder -1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rudder @state) => -1.0
         (swap! state assoc :sfsim.input/rudder 1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rudder @state) => 1.0
         ; Throttle
         (:sfsim.input/throttle @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_R) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_R) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/throttle @state) => 0.0625
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_F) GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_F) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT)
         (:sfsim.input/throttle @state) => 0.0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_F) GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_F) GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT)
         (:sfsim.input/throttle @state) => 0.0
         (swap! state assoc :sfsim.input/throttle 1.0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_R) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_R) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/throttle @state) => 1.0
         (:sfsim.input/air-brake @state) => false
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_SLASH) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_SLASH) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/air-brake @state) => true
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_SLASH) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_SLASH) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/air-brake @state) => false
         ; Toggle aerofoil surfaces/RCS thrusters
         (:sfsim.input/rcs @state) => false
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_BACKSLASH) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_BACKSLASH) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs @state) => true
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_BACKSLASH) GLFW/GLFW_PRESS 0)
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_BACKSLASH) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs @state) => false
         ; RCS roll
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-roll @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-roll @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-roll @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_A) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs-roll @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-roll @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-roll @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_D) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs-roll @state) => 0
         ; RCS pitch
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-pitch @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-pitch @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-pitch @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_W) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs-pitch @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-pitch @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-pitch @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_S) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs-pitch @state) => 0
         ; RCS yaw
         (swap! state assoc :sfsim.input/rcs true)
         (:sfsim.input/rcs-yaw @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-yaw @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-yaw @state) => 1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_Q) GLFW/GLFW_RELEASE 0)
         (:sfsim.input/rcs-yaw @state) => 0
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_PRESS 0)
         (:sfsim.input/rcs-yaw @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_REPEAT 0)
         (:sfsim.input/rcs-yaw @state) => -1
         (swap! state simulator-key (mappings GLFW/GLFW_KEY_E) GLFW/GLFW_RELEASE 0)))


(facts "Process mouse events"
       (let [event-buffer (make-event-buffer)
             handler      (reify InputHandlerProtocol
                                 (process-mouse-button [_this state button x y action mods]
                                   (conj state {:button button :action action :mods mods :x x :y y}))
                                 (process-mouse-move [_this state x y]
                                   (conj state {:x x :y y})))]
         (process-events [] (add-mouse-button-event event-buffer GLFW/GLFW_MOUSE_BUTTON_LEFT 160 120 GLFW/GLFW_PRESS 0) handler)
         => [{:button GLFW/GLFW_MOUSE_BUTTON_LEFT :action GLFW/GLFW_PRESS :mods 0 :x 160 :y 120}]
         (process-events [] (add-mouse-move-event event-buffer 100 60) handler)
         => [{:x 100 :y 60}]))


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
             mock-handler             (reify InputHandlerProtocol
                                             (process-joystick-axis [_this state device axis value _moved]
                                               (conj state {:device device :axis axis :value value})))
             state                    (atom (make-initial-state))
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
             handler                  (->InputHandler gui)]
         (process-events [] (add-joystick-axis-state event-buffer axis-state "Gamepad" []) mock-handler)
         => []
         (process-events [] (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75]) mock-handler)
         => [{:device "Gamepad" :axis 0 :value -0.5} {:device "Gamepad" :axis 1 :value -0.75}]
         ; Aerofoil surfaces
         (swap! state assoc :sfsim.input/mappings mappings)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/aileron @state) => 0.5
         (:sfsim.input/elevator @state) => 0.75
         (:sfsim.input/rudder @state) => -0.5
         (swap! state assoc :sfsim.input/mappings mappings-inv)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/aileron @state) => -0.5
         (:sfsim.input/elevator @state) => -0.75
         (:sfsim.input/rudder @state) => 0.5
         ; RCS thrusters
         (swap! state assoc :sfsim.input/rcs true)
         (swap! state assoc :sfsim.input/mappings mappings)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/rcs-roll @state) => 1.0
         (:sfsim.input/rcs-pitch @state) => 1.0
         (:sfsim.input/rcs-yaw @state) => -1.0
         (swap! state assoc :sfsim.input/mappings mappings-inv)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.5 -0.75 0.5 0.0]) handler)
         (:sfsim.input/rcs-roll @state) => -1.0
         (:sfsim.input/rcs-pitch @state) => -1.0
         (:sfsim.input/rcs-yaw @state) => 1.0
         (swap! state assoc :sfsim.input/rcs false)
         ; Dead zone testing
         (swap! state assoc :sfsim.input/mappings mappings-zn)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.75]) handler)
         (:sfsim.input/aileron @state) => 0.5
         ; Throttle
         (swap! state assoc :sfsim.input/mappings map-throttle)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.75]) handler)
         (:sfsim.input/throttle @state) => 0.125
         (swap! state assoc :sfsim.input/mappings map-throttle-zn)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.25]) handler)
         (:sfsim.input/throttle @state) => 0.25
         (swap! state assoc :sfsim.input/throttle 0.0)
         ; Incremental throttle
         (swap! state assoc :sfsim.input/mappings map-throttle-incr)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-1.0]) handler)
         (:sfsim.input/throttle @state) => 0.0625
         (swap! state assoc :sfsim.input/throttle 0.0)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0]) handler)
         (:sfsim.input/throttle @state) => 0.0
         (swap! state assoc :sfsim.input/mappings map-throttle-incr-zn)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [-0.75]) handler)
         (:sfsim.input/throttle @state) => (* 0.5 0.0625)
         (swap! state assoc :sfsim.input/mappings mappings)
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Unknown" [0.0 0.0]) handler)))


(facts "Process joystick button events"
       (let [event-buffer             (make-event-buffer)
             button-state             (atom {})
             mock-handler             (reify InputHandlerProtocol
                                             (process-joystick-button [_this state device button action]
                                               (conj state {:device device :button button :action action})))
             mappings                 {:sfsim.input/joysticks {:sfsim.input/devices
                                                               {"Gamepad" {:sfsim.input/buttons {1 :sfsim.input/gear
                                                                                                 2 :sfsim.input/brake
                                                                                                 3 :sfsim.input/parking-brake
                                                                                                 4 :sfsim.input/air-brake}}}}}
             state                    (atom (assoc (make-initial-state) :sfsim.input/mappings mappings))
             gui                      {:sfsim.gui/context :ctx}
             handler                  (->InputHandler gui)]
         (process-events [] (add-joystick-button-state event-buffer button-state "Gamepad" []) mock-handler)
         => []
         (process-events [] (add-joystick-button-state event-buffer button-state "Gamepad" [0 0]) mock-handler)
         => []
         (process-events [] (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) mock-handler)
         => [{:device "Gamepad" :button 1 :action GLFW/GLFW_PRESS}]
         (process-events [] (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) mock-handler)
         => [{:device "Gamepad" :button 1 :action GLFW/GLFW_REPEAT}]
         (process-events [] (add-joystick-button-state event-buffer button-state "Gamepad" [0 0]) mock-handler)
         => [{:device "Gamepad" :button 1 :action GLFW/GLFW_RELEASE}]
         (:sfsim.input/gear-down @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 1]) handler)
         (:sfsim.input/gear-down @state) => false
         (:sfsim.input/brake @state) => false
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (:sfsim.input/brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (:sfsim.input/brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0]) handler)
         (:sfsim.input/brake @state) => false
         (:sfsim.input/parking-brake @state) => false
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 1]) handler)
         (:sfsim.input/parking-brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0]) handler)
         (:sfsim.input/parking-brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1 0]) handler)
         (:sfsim.input/parking-brake @state) => false
         (:sfsim.input/air-brake @state) => false
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 1]) handler)
         (:sfsim.input/air-brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 0]) handler)
         (:sfsim.input/air-brake @state) => true
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 1]) handler)
         (:sfsim.input/air-brake @state) => false
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0 0 0]) handler)
         (:sfsim.input/air-brake @state) => false))


(facts "Recording last active joystick axis or button"
       (let [event-buffer (make-event-buffer)
             gui          {:sfsim.gui/context :ctx}
             state        (atom {:sfsim.input/menu true :sfsim.input/mappings {}})
             button-state (atom {})
             axis-state   (atom {})
             handler      (->InputHandler gui)]
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 0]) handler)
         (@state :sfsim.input/last-joystick-button) => nil
         (swap! state process-events (add-joystick-button-state event-buffer button-state "Gamepad" [0 0 1]) handler)
         (@state :sfsim.input/last-joystick-button) => ["Gamepad" 2]
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [0.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => nil
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => ["Gamepad" 0]
         (swap! state process-events (add-joystick-axis-state event-buffer axis-state "Gamepad" [1.0 0.0]) handler)
         (@state :sfsim.input/last-joystick-axis) => ["Gamepad" 0]))
