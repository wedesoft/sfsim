;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.graphics
    "High-level graphics code"
    (:require
      [sfsim.config :as config]
      [sfsim.clouds :as clouds]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.planet :as planet]
      [sfsim.model :as model]
      [sfsim.opacity :as opacity]))


(defn make-graphics-data
  []
  (let [cloud-data (clouds/make-cloud-data config/cloud-config)]
    {:sfsim.render/config config/render-config
     :sfsim.planet/config config/planet-config
     :sfsim.model/data config/model-config
     :sfsim.clouds/data cloud-data
     :sfsim.atmosphere/luts (atmosphere/make-atmosphere-luts config/max-height)
     :sfsim.opacity/data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)}))


(defn make-graphics
  []
  (let [data (make-graphics-data)]
    {::data data
     ::opacity-renderer       (opacity/make-opacity-renderer data)
     ::planet-shadow-renderer (planet/make-planet-shadow-renderer data)
     ::planet-renderer        (planet/make-planet-renderer data)
     ::atmosphere-renderer    (atmosphere/make-atmosphere-renderer data)
     ::geometry-renderer      (model/make-joined-geometry-renderer data)
     ::cloud-renderer         (clouds/make-cloud-renderer data)}))


(defn destroy-graphics
  [graphics]
  (clouds/destroy-cloud-renderer (::cloud-renderer graphics))
  (model/destroy-joined-geometry-renderer (::geometry-renderer graphics))
  (atmosphere/destroy-atmosphere-renderer (::atmosphere-renderer graphics))
  (planet/destroy-planet-renderer (::planet-renderer graphics))
  (planet/destroy-planet-shadow-renderer (::planet-shadow-renderer graphics))
  (opacity/destroy-opacity-renderer (::opacity-renderer graphics))
  (atmosphere/destroy-atmosphere-luts (-> graphics ::data :sfsim.atmosphere/luts))
  (clouds/destroy-cloud-data (-> graphics ::data :sfsim.clouds/data)))
