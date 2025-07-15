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
  (atom {::menu          false
         ::fullscreen    false
         ::pause         false
         ::brake         false
         ::parking-brake false
         ::gear-down     true
         ::elevator      0.0
         }))


(def default-mappings
  {GLFW/GLFW_KEY_ESCAPE ::menu
   GLFW/GLFW_KEY_F      ::fullscreen
   GLFW/GLFW_KEY_P      ::pause
   GLFW/GLFW_KEY_G      ::gear
   GLFW/GLFW_KEY_B      ::brake
   GLFW/GLFW_KEY_W      ::elevator-down
   GLFW/GLFW_KEY_S      ::elevator-up
   })


(defn menu-key
  "Key handling when menu is shown"
  [k state gui action mods]
  (let [press (#{GLFW/GLFW_PRESS GLFW/GLFW_REPEAT} action)]
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
      (= k GLFW/GLFW_KEY_RIGHT_SHIFT) (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_SHIFT press))
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
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::fullscreen not)))


(defmethod simulator-key ::pause
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::pause not)))


(defmethod simulator-key ::gear
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::gear-down not)))


(defmethod simulator-key ::elevator-down
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::elevator + 0.0625)))


(defmethod simulator-key ::elevator-up
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::elevator - 0.0625)))


(defmethod simulator-key ::brake
  [_id state action mods]
  (if (= mods GLFW/GLFW_MOD_SHIFT)
    (when (= action GLFW/GLFW_PRESS)
      (swap! state update ::parking-brake not))
    (do
      (swap! state assoc ::parking-brake false)
      (swap! state assoc ::brake (not= action GLFW/GLFW_RELEASE)))))


(defn process-char
  [state gui codepoint]
  (when (-> @state ::menu not)
    (Nuklear/nk_input_unicode (:sfsim.gui/context gui) codepoint)))


(defn process-key
  [state gui mappings k action mods]
  (if (::menu @state)
    (-> k (menu-key state gui action mods))
    (-> k mappings (simulator-key state action mods))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
