;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.graphics
    "High-level graphics code"
    (:require
      [fastmath.vector :refer (vec3)]
      [sfsim.config :as config]
      [sfsim.clouds :as clouds]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.planet :as planet]
      [sfsim.model :as model]
      [sfsim.render :as render]
      [sfsim.texture :as texture]
      [sfsim.opacity :as opacity]
      [sfsim.matrix :as matrix]))


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
  [model-files object-radius]
  (let [data                  (make-graphics-data)
        models                (mapv model/read-gltf model-files)
        scene-renderer        (model/make-scene-renderer data)
        scene-shadow-renderer (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config) object-radius)]
    {::data data
     ::opacity-renderer       (opacity/make-opacity-renderer data)
     ::planet-shadow-renderer (planet/make-planet-shadow-renderer data)
     ::planet-renderer        (planet/make-planet-renderer data)
     ::atmosphere-renderer    (atmosphere/make-atmosphere-renderer data)
     ::geometry-renderer      (model/make-joined-geometry-renderer data)
     ::cloud-renderer         (clouds/make-cloud-renderer data)
     ::scene-renderer         scene-renderer
     ::scene-shadow-renderer  scene-shadow-renderer
     ::scenes                 (mapv (partial model/load-scene scene-renderer) models)}))


(defn prepare-frame
  "Render geometry buffer for deferred rendering and cloud overlay"
  [graphics model-vars tree width height position orientation light-direction
   object-position object-orientation opacity-base]
  (let [opacity-renderer       (::opacity-renderer graphics)
        planet-shadow-renderer (::planet-shadow-renderer graphics)
        geometry-renderer      (::geometry-renderer graphics)
        cloud-renderer         (::cloud-renderer graphics)
        scene-shadow-renderer  (::scene-shadow-renderer graphics)
        scenes                 (::scenes graphics)
        shadow-data            (-> graphics ::data :sfsim.opacity/data)
        cloud-data             (-> graphics ::data :sfsim.clouds/data)
        object-to-world        (matrix/transformation-matrix (matrix/quaternion->matrix object-orientation) object-position)
        objects                (mapv #(assoc-in % [:sfsim.model/root :sfsim.model/transform] object-to-world) scenes)
        render-vars            (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                               width height position orientation light-direction
                                                               object-position object-orientation model-vars)
        shadow-vars            (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                   cloud-data render-vars tree opacity-base)
        cloud-render-vars      (clouds/make-cloud-render-vars config/render-config render-vars width height position
                                                              orientation light-direction object-position object-orientation)
        object-shadows         (mapv (partial model/scene-shadow-map scene-shadow-renderer light-direction) objects)
        geometry               (model/render-joined-geometry geometry-renderer render-vars render-vars (first objects) tree)
        clouds                 (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars [] geometry)]
    {::render-vars    render-vars
     ::geometry       geometry
     ::shadow-vars    shadow-vars
     ::clouds         clouds
     ::object-shadows object-shadows
     ::objects        objects}))


(defn render-frame
  [graphics frame tree]
  (let [planet-renderer     (::planet-renderer graphics)
        atmosphere-renderer (::atmosphere-renderer graphics)
        scene-renderer      (::scene-renderer graphics)
        render-vars         (::render-vars frame)
        geometry            (::geometry frame)
        shadow-vars         (::shadow-vars frame)
        clouds              (::clouds frame)
        object-shadows      (::object-shadows frame)
        objects             (::objects frame)]
    (render/with-depth-test true
      (render/clear (vec3 0 1 0) 0.0)
      (model/render-scenes scene-renderer render-vars shadow-vars object-shadows geometry clouds objects)
      (planet/render-planet planet-renderer render-vars shadow-vars object-shadows geometry clouds tree)
      (atmosphere/render-atmosphere atmosphere-renderer render-vars geometry clouds))))


(defn finalise-frame
  "Cleanup frame data"
  [frame]
  (let [geometry       (::geometry frame)
        clouds         (::clouds frame)
        shadow-vars    (::shadow-vars frame)
        object-shadows (::object-shadows frame)]
    (doseq [object-shadow object-shadows]
           (model/destroy-scene-shadow-map object-shadow))
    (clouds/destroy-cloud-geometry geometry)
    (texture/destroy-texture clouds)
    (opacity/destroy-opacity-and-shadow shadow-vars)))


(defn destroy-graphics
  "Destroy graphics renderers"
  [graphics]
  (doseq [scene (::scenes graphics)]
         (model/destroy-scene scene))
  (model/destroy-scene-shadow-renderer (::scene-shadow-renderer graphics))
  (model/destroy-scene-renderer (::scene-renderer graphics))
  (clouds/destroy-cloud-renderer (::cloud-renderer graphics))
  (model/destroy-joined-geometry-renderer (::geometry-renderer graphics))
  (atmosphere/destroy-atmosphere-renderer (::atmosphere-renderer graphics))
  (planet/destroy-planet-renderer (::planet-renderer graphics))
  (planet/destroy-planet-shadow-renderer (::planet-shadow-renderer graphics))
  (opacity/destroy-opacity-renderer (::opacity-renderer graphics))
  (atmosphere/destroy-atmosphere-luts (-> graphics ::data :sfsim.atmosphere/luts))
  (clouds/destroy-cloud-data (-> graphics ::data :sfsim.clouds/data)))
