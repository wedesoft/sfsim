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
      [sfsim.input :refer :all])
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


(facts "Default key mappings"
       (let [event-buffer (make-event-buffer)
             state        (make-initial-state)]
         ; Test gear up
         (:sfsim.input/gear-down @state) => true
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/gear-down @state) => false
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/gear-down @state) => false
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
             (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_RELEASE 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/gear-down @state) => false
         ; Test fullscreen
         (:sfsim.input/fullscreen @state) => false
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/fullscreen @state) => true
         ; Test menu toggle
         (:sfsim.input/menu @state) => false
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/menu @state) => true
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
             (add-key-event GLFW/GLFW_KEY_ESCAPE GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings))
             count) => 1
         (:sfsim.input/menu @state) => false
         (swap! state update :sfsim.input/menu not)
         ; Test no gear operation when menu is shown
         (:sfsim.input/gear-down @state) => false
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_G GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/gear-down @state) => false
         ; Test no fullscreen toggle when menu is shown
         (:sfsim.input/fullscreen @state) => true
         (-> event-buffer
             (add-key-event GLFW/GLFW_KEY_F GLFW/GLFW_PRESS 0)
             (process-events (constantly nil) (partial process-key state default-mappings)))
         (:sfsim.input/fullscreen @state) => true))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
