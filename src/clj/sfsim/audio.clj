;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.audio
    "OpenAL method calls for sound output"
    (:require [fastmath.vector :refer (vec3 mag)]
              [sfsim.config :as config]
              [sfsim.physics :refer (get-height get-linear-speed)]
              [sfsim.atmosphere :refer (pressure-at-height density-at-height)]
              [sfsim.aerodynamics :refer (dynamic-pressure drag-multiplier)]
              [sfsim.units :refer (pound-force foot)])
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


(defn source-state
  "Get state of audio source"
  [source]
  (AL10/alGetSourcei source AL10/AL_SOURCE_STATE))


(defn source-playing?
  "Determine whether an audio source is playing"
  [source]
  (= (source-state source) AL10/AL_PLAYING))


(defn source-paused?
  "Determine whether an audio source is paused"
  [source]
  (= (source-state source) AL10/AL_PAUSED))


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
        rcs-buffer (-> "data/audio/thruster.ogg" load-vorbis make-audio-buffer)
        air-flow-buffer (-> "data/audio/air-flow.ogg" load-vorbis make-audio-buffer)
        drag-buffer (-> "data/audio/drag.ogg" load-vorbis make-audio-buffer)
        gear-deploy-source (make-source gear-deploy-buffer false)
        gear-retract-source (make-source gear-retract-buffer false)
        throttle-source (make-source throttle-buffer true)
        rcs-thruster-source (make-source rcs-buffer true)
        air-flow-source (make-source air-flow-buffer true)
        drag-source (make-source drag-buffer true)]
    {::audio audio
     ::buffers [gear-deploy-buffer gear-retract-buffer throttle-buffer rcs-buffer]
     ::sources {::gear-deploy gear-deploy-source
                ::gear-retract gear-retract-source
                ::throttle throttle-source
                ::rcs-thruster rcs-thruster-source
                ::air-flow air-flow-source
                ::drag drag-source}
     ::gear-down true
     ::throttle 0.0
     ::rcs-count 0
     ::paused []}))


(defn update-state
  [state physics inputs]
  (let [sources                   (::sources state)
        controls                  (:sfsim.input/controls inputs)
        gear                      (:sfsim.physics/gear physics)
        rcs-count                 (reduce + (map (comp abs controls)
                                                 [:sfsim.input/rcs-yaw :sfsim.input/rcs-pitch :sfsim.input/rcs-roll]))
        height                    (get-height physics config/planet-config)
        sea-level-pressure        (pressure-at-height 0.0)
        pressure                  (pressure-at-height height)
        relative-pressure         (/ pressure sea-level-pressure)
        linear-velocity           (get-linear-speed :sfsim.physics/surface physics)
        density                   (density-at-height height)
        dynamic-pressure          (dynamic-pressure density (mag linear-velocity))
        relative-dynamic-pressure (min 1.0 (/ dynamic-pressure (* 1000.0 (/ ^double pound-force (* ^double foot ^double foot)))))
        air-brake                 (:sfsim.physics/air-brake physics)
        drag                      (/ (- (drag-multiplier gear air-brake) 1.0) 0.6)]
    (if (:sfsim.input/pause inputs)
      (let [playing-sources (filter source-playing? (vals sources))]
        (doseq [source playing-sources] (source-pause source))
        (update state ::paused into playing-sources))
      (let [paused-sources (filter source-paused? (vals sources))]
        (doseq [source paused-sources] (source-play source))
        (when-not (= (::gear-down state) (:sfsim.input/gear-down controls))
                  (if (:sfsim.input/gear-down controls)
                    (do
                      (source-stop (::gear-retract sources))
                      (set-source-offset (::gear-deploy sources) (* 4.0 ^double gear))
                      (source-play (::gear-deploy sources)))
                    (do
                      (source-stop (::gear-deploy sources))
                      (set-source-offset (::gear-retract sources) (* 4.0 (- 1.0 ^double gear)))
                      (source-play (::gear-retract sources)))))
        (set-source-gain (::gear-deploy sources) relative-pressure)
        (set-source-gain (::gear-retract sources) relative-pressure)
        (when-not (= (zero? ^double (::throttle state)) (zero? ^double (:sfsim.input/throttle controls)))
                  (if (zero? ^double (:sfsim.input/throttle controls))
                    (source-stop (::throttle sources))
                    (source-play (::throttle sources))))
        (when-let [throttle (:sfsim.input/throttle controls)]
                  (set-source-gain (::throttle sources) (* relative-pressure ^double throttle)))
        (when-not (= (zero? ^long rcs-count) (zero? ^long (::rcs-count state)))
                  (if (zero? ^long rcs-count)
                    (source-stop (::rcs-thruster sources))
                    (source-play (::rcs-thruster sources))))
        (set-source-gain (::rcs-thruster sources) (* relative-pressure (/ ^long rcs-count 3.0)))
        (set-source-gain (::air-flow sources) relative-dynamic-pressure)
        (when-not (source-playing? (::air-flow sources)) (source-play (::air-flow sources)))
        (set-source-gain (::drag sources) (* drag relative-dynamic-pressure))
        (when-not (= (> drag 0.0) (source-playing? (::drag sources)))
            (if (zero? drag)
              (source-stop (::drag sources))
              (source-play (::drag sources))))
        (assoc state
               ::gear-down (:sfsim.input/gear-down controls)
               ::throttle (:sfsim.input/throttle controls)
               ::rcs-count rcs-count
               ::paused [])))))


(defn destroy-audio-state
  [state]
  (doseq [source (vals (::sources state))] (destroy-source source))
  (doseq [buffer (::buffers state)] (destroy-audio-buffer buffer))
  (finalize-audio (::audio state)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
