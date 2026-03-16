;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.config
  "Configuration values for software"
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :refer (pprint)]
    [immuconf.config :as immuconf]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def tmpdir "/tmp")
(def separator "/")
(def appdata (str (System/getenv "HOME") separator ".config"))
(def sfsim-data (str appdata separator "sfsim"))


(def config (immuconf/load "resources/config.edn"))


(def max-height 35000.0)
(def render-config (immuconf/get config :sfsim.render-config))
(def planet-config (immuconf/get config :sfsim.planet-config))
(def cloud-config (immuconf/get config :sfsim.cloud-config))
(def shadow-config (immuconf/get config :sfsim.shadow-config))
(def model-config (immuconf/get config :sfsim.model-config))


(defn read-user-config
  ([filename default]
   (read-user-config sfsim-data separator filename default))
  ([sfsim-data separator filename default]
   (let [path (str sfsim-data separator filename)]
     (if (.exists (io/file path))
       (immuconf/load path)
       default))))


(defn write-user-config
  ([filename value]
   (write-user-config sfsim-data separator filename value))
  ([sfsim-data separator filename value]
   (let [path (str sfsim-data separator filename)]
     (io/make-parents path)
     (with-open [f (io/writer path)]
       (binding [*out* f]
                (pprint value))))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
