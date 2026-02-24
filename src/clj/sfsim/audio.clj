;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.audio
    "OpenAL method calls for sound output"
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)
             (org.lwjgl BufferUtils)
             (org.lwjgl.system MemoryUtil)
             (org.lwjgl.openal ALC10 ALC AL AL10 EXTThreadLocalContext)))


(defn initialize-audio
  "Open audio device"
  [device-name]
  (let [device (ALC10/alcOpenDevice device-name)]
    (when (zero? device)
      (throw (RuntimeException. (format "Error opening sound device \"%s\"." device-name))))
    (let [caps (ALC/createCapabilities device)
          context (ALC10/alcCreateContext device nil)]
      {::device device
       ::caps caps
       ::context context})))


(defn finalize-audio
  "Close audio device"
  [audio]
  (ALC10/alcDestroyContext (::context audio))
  (MemoryUtil/memFree (.getAddressBuffer (::caps audio)))
  (ALC10/alcCloseDevice (::device audio)))
