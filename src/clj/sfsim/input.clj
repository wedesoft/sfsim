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
       GLFW]))


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
  (atom {::gear-down true}))


(defmulti state-change (fn [state id] id))


(defmethod state-change ::gear
  [state _id]
  (swap! state update ::gear-down not))


(def default-mappings
  {GLFW/GLFW_KEY_G ::gear})


(defn process-key
  [state mappings k action mods]
  (->> k mappings (state-change state)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
