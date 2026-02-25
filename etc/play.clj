;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/ALCDemo.java
;; https://javadoc.lwjgl.org/org/lwjgl/openal/package-summary.html
(ns play
    (:require [fastmath.vector :refer (vec3)]
              [sfsim.audio :as audio])
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

(def buffer (audio/make-audio-buffer sound))

(def source (audio/make-source buffer false))

(audio/set-source-position source (vec3 0.0 0.0 5.0))

(audio/play-source source)

(while (= (AL10/alGetSourcei source AL10/AL_SOURCE_STATE) AL10/AL_PLAYING)
       ; get seconds offset
       (println (AL10/alGetSourcef source AL11/AL_SEC_OFFSET))
       (Thread/sleep 100))

(audio/destroy-source source)

(audio/destroy-audio-buffer buffer)

(ALC10/alcMakeContextCurrent 0)

(audio/finalize-audio audio)
