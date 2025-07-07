;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.config
  "Configuration values for software"
  (:require
    [immuconf.config :as immuconf]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(def config (immuconf/load "resources/config.edn"))

(def max-height 35000.0)

(def render-config (immuconf/get config :sfsim.render-config))

(def planet-config (immuconf/get config :sfsim.planet-config))

(def cloud-config (immuconf/get config :sfsim.cloud-config))

(def shadow-config (immuconf/get config :sfsim.shadow-config))

(def object-radius (immuconf/get config :sfsim.object-radius))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
