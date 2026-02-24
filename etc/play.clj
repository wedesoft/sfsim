;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/ALCDemo.java
;; https://javadoc.lwjgl.org/org/lwjgl/openal/package-summary.html
(ns play
    (:require [sfsim.audio :as audio])
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)
             (org.lwjgl BufferUtils)
             (org.lwjgl.system MemoryUtil)
             (org.lwjgl.openal ALC10 ALC AL AL10 AL11 EXTThreadLocalContext)))

; (def file-path "sample-1.ogg")
;
; (def file-path "aerospike.ogg")
; (def file-path "test/clj/sfsim/fixtures/audio/beep.ogg")
(def file-path "warning.ogg")

(def sound (audio/load-vorbis file-path))

(def audio (audio/initialize-audio ""))

; (AL10/alListenerf AL10/AL_GAIN 1.0)

(def buffer (AL10/alGenBuffers))

;; You need one source per sound
(def source (AL10/alGenSources))

(AL10/alBufferData buffer
                   (if (= (:sfsim.audio/channels sound) 1) AL10/AL_FORMAT_MONO16 AL10/AL_FORMAT_STEREO16)
                   (:sfsim.audio/pcm sound)
                   (:sfsim.audio/sample-rate sound))

;; Multiple sources can share the same buffer
; (AL10/alSourcei source AL10/AL_BUFFER buffer)
(AL10/alSourceQueueBuffers source (int-array [buffer buffer buffer]))

; (AL10/alSourcei source AL10/AL_LOOPING AL10/AL_TRUE)
(AL10/alSourcef source AL10/AL_GAIN 1.0)
(audio/setup-sample-attenuation source)
(AL10/alSource3f source AL10/AL_POSITION 0.0 0.0 5.0)

(AL10/alSourcePlay source)

; enqueue buffer again
(AL10/alSourceQueueBuffers source (int-array [buffer]))

(while (= (AL10/alGetSourcei source AL10/AL_SOURCE_STATE) AL10/AL_PLAYING)
       ; print number of enqueued buffers
       (println (AL10/alGetSourcei source AL10/AL_BUFFERS_QUEUED) " enqueued buffers")
       ; get seconds offset
       (println (AL10/alGetSourcef source AL11/AL_SEC_OFFSET))
       (when (> (AL10/alGetSourcei source AL10/AL_BUFFERS_PROCESSED) 0)
        (println "unqueueing buffer")
        (let [unqueue (int-array 1)]
          (AL10/alSourceUnqueueBuffers source unqueue)))
       (Thread/sleep 100))

(AL10/alSourceStop source)

(AL10/alDeleteSources source)

(AL10/alDeleteBuffers buffer)

(ALC10/alcMakeContextCurrent 0)

(audio/finalize-audio audio)
