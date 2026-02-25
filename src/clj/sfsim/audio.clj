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
  (ALC10/alcMakeContextCurrent 0)
  (if (::use-tlc audio)
    (AL/setCurrentThread nil)
    (AL/setCurrentProcess nil))
  (MemoryUtil/memFree (.getAddressBuffer ^ALCapabilities (::caps audio)))
  (ALC10/alcDestroyContext (::context audio))
  (ALC10/alcCloseDevice (::device audio)))


(defn load-vorbis
  "Load Ogg Vorbis file"
  [path]
  (let [error  (int-array 1)
        vorbis (STBVorbis/stb_vorbis_open_filename ^String path ^ints error nil)
        info   (STBVorbisInfo/malloc)]
    (when (zero? ^long vorbis)
      (throw (RuntimeException. (format "Error opening Ogg Vorbis file \"%s\"." path))))
    (STBVorbis/stb_vorbis_get_info vorbis info)
    (try
      (let [channels    (.channels info)
            sample-rate (.sample_rate info)
            samples     (STBVorbis/stb_vorbis_stream_length_in_samples vorbis)
            pcm         (BufferUtils/createShortBuffer (* channels samples))]
        (STBVorbis/stb_vorbis_get_samples_short_interleaved vorbis channels pcm)
        {::channels channels
         ::sample-rate sample-rate
         ::samples samples
         ::pcm pcm})
      (finally
        (STBVorbis/stb_vorbis_close vorbis)))))


(defn audio-format
  "Determine audio format for one or two channels"
  [channels]
  (if (= channels 1)
    AL10/AL_FORMAT_MONO16
    AL10/AL_FORMAT_STEREO16))


(defn make-audio-buffer
  "Create audio buffer from PCM data"
  [sound]
  (let [buffer (AL10/alGenBuffers)]
    (AL10/alBufferData buffer (audio-format (::channels sound)) (::pcm sound) (::sample-rate sound))
    buffer))


(defn destroy-audio-buffer
  "Delete audio buffer"
  [buffer]
  (AL10/alDeleteBuffers buffer))


(defn make-source
  "Create audio source"
  [buffer looping]
  (let [source (AL10/alGenSources)]
    (AL10/alSourcei source AL10/AL_BUFFER buffer)
    (AL10/alSourcef source AL10/AL_GAIN 1.0)
    (AL10/alSourcei source AL10/AL_LOOPING (if looping AL10/AL_TRUE AL10/AL_FALSE))
    (setup-sample-attenuation source)
    source))


(defn set-source-position
  "Set audio source position"
  [source position]
  (let [[x y z] position]
    (AL10/alSource3f source AL10/AL_POSITION x y z)))


(defn play-source
  "Play audio source"
  [source]
  (AL10/alSourcePlay source))


(defn stop-source
  "Stop playing audio source"
  [source]
  (AL10/alSourceStop source))


(defn destroy-source
  "Delete audio source"
  [source]
  (AL10/alDeleteSources source))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
