;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.input
    (:import
      [clojure.lang
       PersistentQueue]
      [org.lwjgl.glfw
       GLFW]
      [org.lwjgl.nuklear
       Nuklear]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-event-buffer
  []
  PersistentQueue/EMPTY)


(defn add-char-event
  [event-buffer codepoint]
  (conj event-buffer {::event ::char ::codepoint codepoint}))


(defn add-key-event
  [event-buffer k action mods]
  (conj event-buffer {::event ::key ::k k ::action action ::mods mods}))


(defn process-event
  "Process a single event"
  [event process-char process-key]
  (cond
    (= (::event event) ::char)
    (process-char (::codepoint event))
    (= (::event event) ::key)
    (process-key (::k event) (::action event) (::mods event))))


(defn process-events
  "Take events from the event buffer and process them"
  [event-buffer process-char process-key]
  (let [event (peek event-buffer)]
    (if event
      (if (false? (process-event event process-char process-key))
        (pop event-buffer)
        (recur (pop event-buffer) process-char process-key))
      event-buffer)))


(defn make-initial-state
  []
  (atom {::menu       false
         ::fullscreen false
         ::gear-down  true}))


(def default-mappings
  {GLFW/GLFW_KEY_ESCAPE ::menu
   GLFW/GLFW_KEY_F      ::fullscreen
   GLFW/GLFW_KEY_G      ::gear })


(defn menu-key
  "Key handling when menu is shown"
  [k state action mods]
  (let [press (or (= action GLFW/GLFW_PRESS))]
    (when (and press (= k GLFW/GLFW_KEY_ESCAPE))
      (swap! state update ::menu not)
      false)))


; Simulation key handling when menu is hidden
(defmulti simulator-key (fn [id _state _action _mods] id))


(defmethod simulator-key ::menu
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::menu not)
    false))


(defmethod simulator-key ::fullscreen
  [_id state action _mods]
  (when (and (= action GLFW/GLFW_PRESS) (-> @state ::menu not))
    (swap! state update ::fullscreen not)))


(defmethod simulator-key ::gear
  [_id state action _mods]
  (when (and (= action GLFW/GLFW_PRESS) (-> @state ::menu not))
    (swap! state update ::gear-down not)))


(defn process-char
  [state gui codepoint]
  (when (-> @state ::menu not)
    (Nuklear/nk_input_unicode (:sfsim.gui/context gui) codepoint)))


(defn process-key
  [state gui mappings k action mods]
  (if (::menu @state)
    (-> k (menu-key state action mods))
    (-> k mappings (simulator-key state action mods))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
