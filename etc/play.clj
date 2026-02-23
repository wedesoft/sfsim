; https://github.com/LWJGL/lwjgl3/blob/master/modules/samples/src/test/java/org/lwjgl/demo/openal/ALCDemo.java
(ns play
    (:require [clojure.java.io :as io])
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)
             (org.lwjgl BufferUtils)
             (org.lwjgl.system MemoryUtil)
             (org.lwjgl.openal ALC10 ALC AL AL10 EXTThreadLocalContext)))

(def file-path "sample-1.ogg")

(when (not (.exists (io/file file-path)))
  (io/copy
    (io/input-stream "https://getsamplefiles.com/download/ogg/sample-1.ogg")
    (io/output-stream file-path)))

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


(def device (ALC10/alcOpenDevice ""))

(def device-caps (ALC/createCapabilities device))

(def default-device (ALC10/alcGetString device ALC10/ALC_DEVICE_SPECIFIER))

(def context (ALC10/alcCreateContext device nil))

(def use-tlc (and (.ALC_EXT_thread_local_context device-caps)
                  (EXTThreadLocalContext/alcSetThreadContext context)))

(when-not use-tlc
          (ALC10/alcMakeContextCurrent context))

(def caps (AL/createCapabilities device-caps))

(def buffer (AL10/alGenBuffers))

;; You need one source per sound
(def source (AL10/alGenSources))

(AL10/alBufferData buffer (if (= num-channels 1) AL10/AL_FORMAT_MONO16 AL10/AL_FORMAT_STEREO16) pcm sample-rate)

;; Multiple sources can share the same buffer
(AL10/alSourcei source AL10/AL_BUFFER buffer)

; (AL10/alSourcei source AL10/AL_LOOPING AL10/AL_TRUE)

(AL10/alSourcePlay source)

(while (= (AL10/alGetSourcei source AL10/AL_SOURCE_STATE) AL10/AL_PLAYING)
       (Thread/sleep 100))

(AL10/alSourceStop source)

(AL10/alDeleteSources source)

(AL10/alDeleteBuffers buffer)

(ALC10/alcMakeContextCurrent 0)

(if use-tlc
  (AL/setCurrentThread nil)
  (AL/setCurrentProcess nil))

(MemoryUtil/memFree (.getAddressBuffer caps))

(ALC10/alcDestroyContext context)
(ALC10/alcCloseDevice device)
