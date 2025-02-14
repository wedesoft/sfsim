(ns sfsim.config
  "Configuration values for software"
  (:require
    [immuconf.config :as immuconf]))


(set! *unchecked-math* true)
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
