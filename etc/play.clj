; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/ALCDemo.java
(ns play
    (:require [clojure.java.io :as io]
              [clojure.math :refer (sin)]
              [sfsim.audio :as audio])
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)
             (org.lwjgl BufferUtils)
             (org.lwjgl.system MemoryUtil)
             (org.lwjgl.openal ALC10 ALC AL AL10 EXTThreadLocalContext)))

; (def file-path "sample-1.ogg")
;
; (when (not (.exists (io/file file-path)))
;   (io/copy
;     (io/input-stream "https://getsamplefiles.com/download/ogg/sample-1.ogg")
;     (io/output-stream file-path)))

; (def file-path "aerospike.ogg")
(def file-path "mono.ogg")
; (def file-path "warning.ogg")

(def error  (int-array 1))
(def vorbis (STBVorbis/stb_vorbis_open_filename file-path error nil)) ; TODO: check not zero
(def info (STBVorbisInfo/malloc))
(STBVorbis/stb_vorbis_get_info vorbis info)
(def sample-rate (.sample_rate info))
(def num-channels (.channels info))
(def num-samples (STBVorbis/stb_vorbis_stream_length_in_samples vorbis))

(def pcm (BufferUtils/createShortBuffer (* num-samples num-channels)))
(STBVorbis/stb_vorbis_get_samples_short_interleaved vorbis num-channels pcm)

(STBVorbis/stb_vorbis_close vorbis)


(def audio (audio/initialize-audio ""))

(AL10/alDistanceModel AL10/AL_INVERSE_DISTANCE_CLAMPED)

(AL10/alListener3f AL10/AL_POSITION 0.0 0.0 0.0)
; forward and up vector
(AL10/alListenerfv AL10/AL_ORIENTATION (float-array [0.0 0.0 -1.0 0.0 1.0 0.0]))


(def buffer (AL10/alGenBuffers))

;; You need one source per sound
(def source (AL10/alGenSources))

(AL10/alBufferData buffer (if (= num-channels 1) AL10/AL_FORMAT_MONO16 AL10/AL_FORMAT_STEREO16) pcm sample-rate)

;; Multiple sources can share the same buffer
(AL10/alSourcei source AL10/AL_BUFFER buffer)

; (AL10/alSourcei source AL10/AL_LOOPING AL10/AL_TRUE)
(AL10/alSourcef source AL10/AL_GAIN 1.0)
(AL10/alSourcei source AL10/AL_SOURCE_RELATIVE AL10/AL_FALSE)
(AL10/alSourcef source AL10/AL_REFERENCE_DISTANCE 10.0)
(AL10/alSourcef source AL10/AL_MAX_DISTANCE 100.0)
(AL10/alSourcef source AL10/AL_ROLLOFF_FACTOR 1.0)
(AL10/alSource3f source AL10/AL_POSITION 0.0 0.0 5.0)

(AL10/alSourcePlay source)

; (AL10/alSourceQueueBuffers source (int-array [buffer buffer buffer]))
; (while (> (AL10/alGetSourcei source AL10/AL_BUFFERS_PROCESSED) 0)
;        (println "unqueueing buffer")
;        (let [unqueue (int-array 1)]
;          (AL10/alSourceUnqueueBuffers source unqueue)))

(def t (atom 0.0))
(while (= (AL10/alGetSourcei source AL10/AL_SOURCE_STATE) AL10/AL_PLAYING)
       (swap! t + 0.1)
       (AL10/alSource3f source AL10/AL_POSITION (* 10.0 (sin @t)) 0.0 5.0)
       (Thread/sleep 100))

(AL10/alSourceStop source)

(AL10/alDeleteSources source)

(AL10/alDeleteBuffers buffer)

(ALC10/alcMakeContextCurrent 0)

(audio/finalize-audio audio)
