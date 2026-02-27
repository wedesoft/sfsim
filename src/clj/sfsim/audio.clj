;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.audio
    "OpenAL method calls for sound output"
    (:require [fastmath.vector :refer (vec3)]
              [sfsim.config :as config]
              [sfsim.physics :refer (get-height)]
              [sfsim.atmosphere :refer (pressure-at-height)])
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)
             (org.lwjgl BufferUtils)
             (org.lwjgl.system MemoryUtil)
             (org.lwjgl.openal AL AL10 AL11 ALC ALC10 ALCapabilities EXTThreadLocalContext)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn setup-sample-attenuation
  "Configure distance attenuation model"
  [source]
  (AL10/alSourcef source AL10/AL_REFERENCE_DISTANCE 10.0)
  (AL10/alSourcef source AL10/AL_MAX_DISTANCE 100.0)
  (AL10/alSourcef source AL10/AL_ROLLOFF_FACTOR 1.0))


(defn set-listener-position
  "Set listener position"
  [position]
  (AL10/alListener3f AL10/AL_POSITION (position 0) (position 1) (position 2)))


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
        (set-listener-position (vec3 0 0 0))
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
  ^long [^long channels]
  (if (= channels 1)
    AL10/AL_FORMAT_MONO16
    AL10/AL_FORMAT_STEREO16))


(defn make-audio-buffer
  "Create audio buffer from PCM data"
  [sound]
  (let [buffer (AL10/alGenBuffers)]
    (AL10/alBufferData buffer (audio-format (::channels sound)) ^java.nio.DirectShortBufferU (::pcm sound) ^long (::sample-rate sound))
    buffer))


(defn destroy-audio-buffer
  "Delete audio buffer"
  [^long buffer]
  (AL10/alDeleteBuffers buffer))


(defn set-source-gain
  "Set audio volume of source"
  [source gain]
  (AL10/alSourcef source AL10/AL_GAIN gain))


(defn make-source
  "Create audio source"
  [buffer looping]
  (let [source (AL10/alGenSources)]
    (AL10/alSourcei source AL10/AL_BUFFER buffer)
    (set-source-gain source 1.0)
    (AL10/alSourcei source AL10/AL_LOOPING (if looping AL10/AL_TRUE AL10/AL_FALSE))
    (setup-sample-attenuation source)
    source))


(defn set-source-position
  "Set audio source position"
  [source position]
  (AL10/alSource3f source AL10/AL_POSITION (position 0) (position 1) (position 2)))


(defn source-play
  "Play audio source"
  [source]
  (AL10/alSourcePlay source))


(defn source-pause
  "Pause audio source"
  [source]
  (AL10/alSourcePause source))


(defn source-stop
  "Stop playing audio source"
  [source]
  (AL10/alSourceStop source))


(defn get-source-offset
  "Get current time of audio track"
  [source]
  (AL10/alGetSourcef source AL11/AL_SEC_OFFSET))


(defn set-source-offset
  "Seek to specified time in audio track"
  [source offset]
  (AL10/alSourcef source AL11/AL_SEC_OFFSET offset))


(defn destroy-source
  "Delete audio source"
  [^long source]
  (AL10/alDeleteSources source))


(defn make-audio-state
  []
  (let [audio (initialize-audio "")
        gear-deploy-buffer (-> "data/audio/gear-deploy.ogg" load-vorbis make-audio-buffer)
        gear-retract-buffer (-> "data/audio/gear-retract.ogg" load-vorbis make-audio-buffer)
        throttle-buffer (-> "data/audio/main-engine.ogg" load-vorbis make-audio-buffer)
        gear-deploy-source (make-source gear-deploy-buffer false)
        gear-retract-source (make-source gear-retract-buffer false)
        throttle-source (make-source throttle-buffer true)]
    {::audio audio
     ::gear-deploy-buffer gear-deploy-buffer
     ::gear-retract-buffer gear-retract-buffer
     ::throttle-buffer throttle-buffer
     ::gear-deploy-source gear-deploy-source
     ::gear-retract-source gear-retract-source
     ::throttle-source throttle-source
     ::gear-down true
     ::throttle 0.0}))


(defn update-state
  [state physics inputs]
  (let [height             (get-height physics config/planet-config)
        sea-level-pressure (pressure-at-height 0.0)
        pressure           (pressure-at-height height)
        relative-pressure  (/ pressure sea-level-pressure)]
    (when-not (= (::gear-down state) (:sfsim.input/gear-down inputs))
              (if (:sfsim.input/gear-down inputs)
                (do
                  (source-stop (::gear-retract-source state))
                  (set-source-offset (::gear-deploy-source state) (* 4.0 ^double (:sfsim.physics/gear physics)))
                  (source-play (::gear-deploy-source state)))
                (do
                  (source-stop (::gear-deploy-source state))
                  (set-source-offset (::gear-retract-source state) (* 4.0 (- 1.0 ^double (:sfsim.physics/gear physics))))
                  (source-play (::gear-retract-source state)))))
    (set-source-gain (::gear-deploy-source state) relative-pressure)
    (set-source-gain (::gear-retract-source state) relative-pressure)
    (when-not (= (zero? (::throttle state)) (zero? (:sfsim.input/throttle inputs)))
              (if (zero? (:sfsim.input/throttle inputs))
                (source-stop (::throttle-source state))
                (source-play (::throttle-source state))))
    (when-let [throttle (:sfsim.input/throttle inputs)]
              (set-source-gain (::throttle-source state) (* relative-pressure throttle)))
    (assoc state
           ::gear-down (:sfsim.input/gear-down inputs)
           ::throttle (:sfsim.input/throttle inputs))))


(defn destroy-audio-state
  [state]
  (destroy-source (::gear-deploy-source state))
  (destroy-source (::gear-retract-source state))
  (destroy-audio-buffer (::gear-deploy-buffer state))
  (destroy-audio-buffer (::gear-retract-buffer state))
  (finalize-audio (::audio state)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
