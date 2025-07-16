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


(facts "Process character events"
       (let [event-buffer (make-event-buffer)
             playback     (atom [])
             process-char (fn [c] (swap! playback conj c))
             process-last (fn [c] (swap! playback conj c) false)]
         (process-events event-buffer process-char (constantly nil)) => []
         @playback => []
         (process-events (add-char-event event-buffer 0x20) process-char (constantly nil))
         @playback => [0x20]
         (reset! playback [])
         (-> event-buffer (add-char-event 0x61) (add-char-event 0x62) (process-events process-char (constantly nil))) => []
         @playback => [0x61 0x62]
         (reset! playback [])
         (-> event-buffer (add-char-event 0x61) (add-char-event 0x62) (process-events process-last (constantly nil)) count) => 1
         @playback => [0x61]))


(facts "Process key events"
       (let [event-buffer (make-event-buffer)
             playback (atom [])
             process-key (fn [k action mods] (swap! playback conj {:key k :action action :mods mods}))]
         (process-events event-buffer (constantly nil) process-key) => []
         (-> event-buffer (add-key-event GLFW/GLFW_KEY_A GLFW/GLFW_PRESS 0) (process-events (constantly nil) process-key)) => []
         @playback => [{:key GLFW/GLFW_KEY_A :action GLFW/GLFW_PRESS :mods 0}]))


(def gui-key (atom nil))


(defn menu-key-mock
  [k state gui action mods]
  (reset! gui-key k)
  (when (and (= action GLFW/GLFW_PRESS) (= k GLFW/GLFW_KEY_ESCAPE))
    (swap! state update :sfsim.input/menu not)
    false))


(facts "Test the integrated behaviour of some keys"
       (with-redefs [input/menu-key menu-key-mock]
         (let [event-buffer (make-event-buffer)
               state        (make-initial-state)
               gui          {:sfsim.gui/context :ctx}]
           ; Test gear up
           (:sfsim.input/gear-down @state) => true
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/gear-down @state) => false
           ; Test fullscreen
           (:sfsim.input/fullscreen @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/fullscreen @state) => true
           ; Test menu toggle
           (:sfsim.input/menu @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/menu @state) => true
           ; Hiding menu should postpone processing of remaining events
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings))
               count) => 3
           (:sfsim.input/menu @state) => false
           ; Showing menu should postpone processing of remaining events
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings))
               count) => 3
           (:sfsim.input/menu @state) => true
           ; Test no gear operation when menu is shown
           (swap! state assoc :sfsim.input/menu true)
           (:sfsim.input/gear-down @state) => false
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/gear-down @state) => false
           ; Test no fullscreen toggle when menu is shown
           (:sfsim.input/fullscreen @state) => true
           (-> event-buffer
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
               (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_RELEASE 0)
               (process-events (constantly nil) (partial process-key state gui default-mappings)))
           (:sfsim.input/fullscreen @state) => true
           ; Use alternate method for handling keys when menu is shown
           @gui-key => GLFW/GLFW_KEY_F)))


(facts "Test some simulator key bindings directly"
       (let [state (make-initial-state)]
         ; Pause
         (-> GLFW/GLFW_KEY_P default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_P default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/pause @state) => true
         (-> GLFW/GLFW_KEY_P default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_P default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/pause @state) => false
         ; Brakes
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_REPEAT 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         ; Parking brakes
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false true]
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [false false]
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_SHIFT))
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         ((juxt :sfsim.input/brake :sfsim.input/parking-brake) @state) => [true false]
         (-> GLFW/GLFW_KEY_B default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         ; Aileron
         (:sfsim.input/aileron @state) => 0.0
         (-> GLFW/GLFW_KEY_A default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_A default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 0.0625
         (swap! state assoc :sfsim.input/aileron 0.0)
         (-> GLFW/GLFW_KEY_D default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_D default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => -0.0625
         (-> GLFW/GLFW_KEY_KP_5 default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_KP_5 default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 0.0
         (swap! state assoc :sfsim.input/aileron -1.0)
         (-> GLFW/GLFW_KEY_D default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_D default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => -1.0
         (swap! state assoc :sfsim.input/aileron 1.0)
         (-> GLFW/GLFW_KEY_A default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_A default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/aileron @state) => 1.0
         ; Elevator
         (:sfsim.input/elevator @state) => 0.0
         (-> GLFW/GLFW_KEY_W default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_W default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => 0.0625
         (swap! state assoc :sfsim.input/elevator 0.0)
         (-> GLFW/GLFW_KEY_S default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_S default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => -0.0625
         (swap! state assoc :sfsim.input/elevator -1.0)
         (-> GLFW/GLFW_KEY_S default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_S default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => -1.0
         (swap! state assoc :sfsim.input/elevator 1.0)
         (-> GLFW/GLFW_KEY_W default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_W default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/elevator @state) => 1.0
         ; Rudder
         (:sfsim.input/rudder @state) => 0.0
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => 0.0625
         (swap! state assoc :sfsim.input/rudder 0.0)
         (-> GLFW/GLFW_KEY_E default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_E default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => -0.0625
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_PRESS GLFW/GLFW_MOD_CONTROL))
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_RELEASE GLFW/GLFW_MOD_CONTROL))
         (:sfsim.input/rudder @state) => 0.0
         (swap! state assoc :sfsim.input/rudder -1.0)
         (-> GLFW/GLFW_KEY_E default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_E default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => -1.0
         (swap! state assoc :sfsim.input/rudder 1.0)
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_PRESS 0))
         (-> GLFW/GLFW_KEY_Q default-mappings (simulator-key state GLFW/GLFW_RELEASE 0))
         (:sfsim.input/rudder @state) => 1.0
         ))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
