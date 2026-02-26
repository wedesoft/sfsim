;; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/ALCDemo.java
;; https://javadoc.lwjgl.org/org/lwjgl/openal/package-summary.html
(ns play
    (:require [clojure.math :refer (sin)]
              [fastmath.vector :refer (vec3)]
              [sfsim.audio :as audio])
    (:import (org.lwjgl.openal AL10 AL11)))

(def file-path "mono.ogg")
;
; (def file-path "aerospike.ogg")
; (def file-path "test/clj/sfsim/fixtures/audio/beep.ogg")
; (def file-path "warning.ogg")

(def audio (audio/initialize-audio ""))

(def buffer (-> file-path audio/load-vorbis audio/make-audio-buffer))

(def source (audio/make-source buffer false))

(audio/set-source-position source (vec3 0.0 0.0 5.0))

(audio/source-play source)

(def t (atom 0.0))

(while (= (AL10/alGetSourcei source AL10/AL_SOURCE_STATE) AL10/AL_PLAYING)
       ; get seconds offset
       (swap! t + 0.05)
       (let [x (* 20.0 (sin (* 2.0 @t)))]
         (println  x)
         (audio/set-source-position source (vec3 x 0.0 -2.0)))
       (Thread/sleep 50))

(audio/destroy-source source)

(audio/destroy-audio-buffer buffer)

(audio/finalize-audio audio)
