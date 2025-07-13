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
      [sfsim.input :refer :all]))


(facts "Record key events"
       (let [event-buffer (make-event-buffer)
             playback     (atom [])
             process-char (fn [c] (swap! playback conj c))]
         (process-event event-buffer process-char) => []
         @playback => []
         (process-event (add-char-event event-buffer 0x20) process-char)
         @playback => [0x20]
         (reset! playback [])
         (-> event-buffer (add-char-event 0x61) (add-char-event 0x62) (process-event process-char) (process-event process-char))
         @playback => [0x61 0x62]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
