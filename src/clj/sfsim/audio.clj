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
             (org.lwjgl.openal ALC10 ALC AL ALCapabilities AL10 EXTThreadLocalContext)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn setup-sample-attenuation
  [source]
  (AL10/alSourcef source AL10/AL_REFERENCE_DISTANCE 10.0)
  (AL10/alSourcef source AL10/AL_MAX_DISTANCE 100.0)
  (AL10/alSourcef source AL10/AL_ROLLOFF_FACTOR 1.0))


(defn initialize-audio
  "Open audio device"
  [^String device-name]
  (let [device (ALC10/alcOpenDevice device-name)]
    (when (zero? ^long device)
      (throw (RuntimeException. (format "Error opening sound device \"%s\"." device-name))))
    (let [device-caps (ALC/createCapabilities device)
          context     (ALC10/alcCreateContext ^long device (int-array 1))
          use-tlc     (and (.ALC_EXT_thread_local_context device-caps)
                           (EXTThreadLocalContext/alcSetThreadContext context))]
      (when-not use-tlc
                (ALC10/alcMakeContextCurrent context))
      (let [caps (AL/createCapabilities device-caps)]
        (AL10/alDistanceModel AL10/AL_INVERSE_DISTANCE_CLAMPED)
        (AL10/alListener3f AL10/AL_POSITION 0.0 0.0 0.0)
        ; forward and up vector
        (AL10/alListenerfv AL10/AL_ORIENTATION (float-array [0.0 0.0 -1.0 0.0 1.0 0.0]))
        {::device device
         ::device-caps device-caps
         ::caps caps
         ::context context
         ::use-tlc use-tlc}))))


(defn finalize-audio
  "Close audio device"
  [audio]
  (MemoryUtil/memFree (.getAddressBuffer ^ALCapabilities (::caps audio)))
  (if (::use-tlc audio)
    (AL/setCurrentThread nil)
    (AL/setCurrentProcess nil))
  (ALC10/alcDestroyContext (::context audio))
  (ALC10/alcCloseDevice (::device audio)))


(defn load-vorbis
  "Load Ogg Vorbis file"
  [path]
  (let [error (int-array 1)
        vorbis (STBVorbis/stb_vorbis_open_filename ^String path ^ints error nil)]
    (when (zero? ^long vorbis)
      (throw (RuntimeException. (format "Error opening Ogg Vorbis file \"%s\"." path))))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
