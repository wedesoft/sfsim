;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-audio
    (:require
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [midje.sweet :refer :all]
      [sfsim.audio :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Load Ogg Vorbis audio"
       (load-vorbis "test/clj/sfsim/fixtures/audio/nosuchfile.ogg") => (throws RuntimeException)
       (let [sound (load-vorbis "test/clj/sfsim/fixtures/audio/beep.ogg")]
         (:sfsim.audio/channels sound) => 2
         (:sfsim.audio/sample-rate sound) => 48000
         (:sfsim.audio/samples sound) => 9216
         (.limit (:sfsim.audio/pcm sound)) => (* 9216 2)))
