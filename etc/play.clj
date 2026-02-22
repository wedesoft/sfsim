(ns play
    (:import (org.lwjgl.stb STBVorbis STBVorbisInfo)))

(defn read-ogg-file [file-path]
  (let [error  (int-array 1)
        vorbis (STBVorbis/stb_vorbis_open_filename file-path error nil)]
    (if (zero? vorbis)
      (println "Failed to open Ogg Vorbis file.")
      (let [info (STBVorbisInfo/malloc)]
        (STBVorbis/stb_vorbis_get_info vorbis info)
        (println "Sample rate:" (.sample_rate info))
        (println "Channels:" (.channels info))
        (STBVorbis/stb_vorbis_close vorbis)))))

(read-ogg-file "etc/audio.ogg")
