;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.graphics
    "High-level graphics code"
    (:require
      [fastmath.matrix :refer (mulm)]
      [fastmath.vector :refer (vec3)]
      [sfsim.config :as config]
      [sfsim.clouds :as clouds]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.aerodynamics :as aerodynamics]
      [sfsim.planet :as planet]
      [sfsim.model :as model]
      [sfsim.render :as render]
      [sfsim.lighting :as lighting]
      [sfsim.texture :as texture]
      [sfsim.opacity :as opacity]
      [sfsim.matrix :as matrix])
   (:import
    (org.lwjgl.opengl
      GL11)))


(defn make-graphics2
  []
  (let [cloud-data              (clouds/make-cloud-data config/cloud-config)
        opacity-data            (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)
        atmosphere-luts         (atmosphere/make-atmosphere-luts config/max-height)
        planet-shadow-renderer  (planet/make-planet-shadow-renderer {:sfsim.opacity/data opacity-data
                                                                     :sfsim.planet/config config/planet-config})
        opacity-renderer        (opacity/make-opacity-renderer {:sfsim.render/config config/render-config
                                                                :sfsim.planet/config config/planet-config
                                                                :sfsim.clouds/data cloud-data
                                                                :sfsim.opacity/data opacity-data})
        cloud-renderer          (clouds/make-cloud-renderer {:sfsim.render/config config/render-config
                                                             :sfsim.planet/config config/planet-config
                                                             :sfsim.model/data config/model-config
                                                             :sfsim.opacity/data opacity-data
                                                             :sfsim.clouds/data cloud-data
                                                             :sfsim.atmosphere/luts atmosphere-luts})
        cloud-geometry-renderer (model/make-joined-geometry-renderer {:sfsim.planet/config config/planet-config} 0)
        planet-renderer         (planet/make-planet-geometry-renderer {:sfsim.planet/config config/planet-config} true 0)
        atmosphere-renderer     (atmosphere/make-atmosphere-geometry-renderer true)
        lighting-renderer       (lighting/make-lighting-renderer {:sfsim.render/config config/render-config
                                                                  :sfsim.planet/config config/planet-config
                                                                  :sfsim.opacity/data opacity-data
                                                                  :sfsim.clouds/data cloud-data
                                                                  :sfsim.atmosphere/luts atmosphere-luts})]
    {:sfsim.render/config config/render-config
     :sfsim.planet/config config/planet-config
     :sfsim.clouds/config config/cloud-config
     :sfsim.model/data config/model-config
     :sfsim.clouds/data cloud-data
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.opacity/data opacity-data
     ::planet-shadow-renderer planet-shadow-renderer
     ::opacity-renderer opacity-renderer
     ::cloud-renderer cloud-renderer
     ::cloud-geometry-renderer cloud-geometry-renderer
     ::planet-geometry-renderer planet-renderer
     ::atmosphere-geometry-renderer atmosphere-renderer
     ::lighting-renderer lighting-renderer}))


(defn destroy-graphics2
  [graphics]
  (lighting/destroy-lighting-renderer (::lighting-renderer graphics))
  (atmosphere/destroy-atmosphere-geometry-renderer (::atmosphere-geometry-renderer graphics))
  (planet/destroy-planet-geometry-renderer (::planet-geometry-renderer graphics))
  (model/destroy-joined-geometry-renderer (::cloud-geometry-renderer graphics))
  (clouds/destroy-cloud-renderer (::cloud-renderer graphics))
  (opacity/destroy-opacity-renderer (::opacity-renderer graphics))
  (planet/destroy-planet-shadow-renderer (::planet-shadow-renderer graphics))
  (clouds/destroy-cloud-data (:sfsim.clouds/data graphics))
  (atmosphere/destroy-atmosphere-luts (:sfsim.atmosphere/luts graphics)))


(defn make-frame
  [graphics width height camera-position camera-orientation light-direction]
  (let [render-config          (:sfsim.render/config graphics)
        fov                    (:sfsim.render/fov render-config)
        planet-config          (:sfsim.planet/config graphics)
        cloud-config           (:sfsim.clouds/config graphics)
        planet-render-vars     (planet/make-planet-render-vars2 planet-config cloud-config render-config width height
                                                                camera-position camera-orientation light-direction)
        cloud-render-vars      (clouds/make-cloud-render-vars render-config planet-render-vars width height camera-position
                                                              camera-orientation light-direction camera-position camera-orientation)
        atmosphere-render-vars (atmosphere/make-atmosphere-render-vars width height fov light-direction)
        model-vars             (model/make-model-vars 0.0 1.0 0.0)
        geometry-buffers       (model/make-geometry-buffers width height)]
    {::width                  width
     ::height                 height
     ::camera-position        camera-position
     ::camera-orientation     camera-orientation
     ::planet-render-vars     planet-render-vars
     ::cloud-render-vars      cloud-render-vars
     ::light-direction        light-direction
     ::atmosphere-render-vars atmosphere-render-vars
     ::model-vars             model-vars
     ::geometry-buffers       geometry-buffers}))


(defn destroy-frame
  [frame]
  (texture/destroy-texture (::clouds frame))
  (clouds/destroy-cloud-geometry (::cloud-geometry frame))
  (opacity/destroy-opacity-and-shadow (::shadow-vars frame))
  (model/destroy-geometry-buffers (::geometry-buffers frame)))


(defn render-shadows
  [frame graphics tree]
  (let [shadow-data            (:sfsim.opacity/data graphics)
        cloud-data             (:sfsim.clouds/data graphics)
        planet-render-vars     (::planet-render-vars frame)
        opacity-base           (:sfsim.clouds/opacity-base (:sfsim.clouds/config graphics))
        opacity-renderer       (::opacity-renderer graphics)
        planet-shadow-renderer (::planet-shadow-renderer graphics)]
    (assoc frame ::shadow-vars (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                   cloud-data planet-render-vars tree opacity-base))))

(defn render-cloud-geometry
  [frame graphics tree]
  (let [render-config           (:sfsim.render/config graphics)
        cloud-geometry-renderer (::cloud-geometry-renderer graphics)
        planet-render-vars      (::planet-render-vars frame)
        planet-geometry-vars    (render/make-subsampled-vars planet-render-vars render-config)]
    (assoc frame
           ::cloud-geometry
           (model/render-joined-geometry2 cloud-geometry-renderer planet-geometry-vars planet-geometry-vars nil tree))))


(defn render-clouds
  [frame graphics]
  (let [cloud-renderer      (::cloud-renderer graphics)
        cloud-render-vars   (::cloud-render-vars frame)
        model-vars          (::model-vars frame)
        shadow-vars         (::shadow-vars frame)
        cloud-geometry      (::cloud-geometry frame)]
    (assoc frame ::clouds (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars [] cloud-geometry))))


(defn render-geometry
  [frame graphics tree]
  (let [planet-geometry-renderer     (::planet-geometry-renderer graphics)
        atmosphere-geometry-renderer (::atmosphere-geometry-renderer graphics)
        planet-render-vars           (::planet-render-vars frame)
        atmosphere-render-vars       (::atmosphere-render-vars frame)
        geometry-buffers             (::geometry-buffers frame)]
    (model/render-geometry
      geometry-buffers
      (render/with-stencils
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x2 0x2
          (planet/render-planet-geometry2 planet-geometry-renderer planet-render-vars true tree))
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x1 0x7
          (atmosphere/render-full-atmosphere-geometry atmosphere-geometry-renderer atmosphere-render-vars))))
    frame))


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
    {::data                   data
     ::opacity-renderer       (opacity/make-opacity-renderer data)
     ::planet-shadow-renderer (planet/make-planet-shadow-renderer data)
     ::planet-renderer        (planet/make-planet-renderer data)
     ::atmosphere-renderer    (atmosphere/make-atmosphere-renderer data)
     ::geometry-renderer      (model/make-joined-geometry-renderer data 0)
     ::cloud-renderer         (clouds/make-cloud-renderer data)
     ::scene-renderer         scene-renderer
     ::scene-shadow-renderer  scene-shadow-renderer
     ::models                 models
     ::scenes                 (mapv (partial model/load-scene scene-renderer) models)}))


(defn prepare-frame
  "Render geometry buffer for deferred rendering and cloud overlay"
  [graphics model-vars tree width height position orientation light-direction
   object-position object-orientation plume-transforms opacity-base]
  (let [opacity-renderer       (::opacity-renderer graphics)
        planet-shadow-renderer (::planet-shadow-renderer graphics)
        geometry-renderer      (::geometry-renderer graphics)
        cloud-renderer         (::cloud-renderer graphics)
        scene-shadow-renderer  (::scene-shadow-renderer graphics)
        scenes                 (::scenes graphics)
        shadow-data            (-> graphics ::data :sfsim.opacity/data)
        cloud-data             (-> graphics ::data :sfsim.clouds/data)
        object-to-world        (matrix/transformation-matrix (matrix/quaternion->matrix object-orientation) object-position)
        gltf-to-aerodynamic    (matrix/rotation-matrix aerodynamics/gltf-to-aerodynamic)
        objects                (mapv #(assoc-in % [:sfsim.model/root :sfsim.model/transform]
                                                (mulm object-to-world gltf-to-aerodynamic)) scenes)
        planet-render-vars     (planet/make-planet-render-vars config/planet-config cloud-data config/render-config
                                                               width height position orientation light-direction
                                                               object-position object-orientation model-vars)
        scene-render-vars      (model/make-scene-render-vars config/render-config width height position
                                                             orientation light-direction object-position
                                                             object-orientation config/model-config model-vars)
        shadow-render-vars     (render/joined-render-vars planet-render-vars scene-render-vars)
        shadow-vars            (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                   cloud-data shadow-render-vars tree opacity-base)
        cloud-render-vars      (clouds/make-cloud-render-vars config/render-config planet-render-vars width height position
                                                              orientation light-direction object-position object-orientation)
        object-shadows         (mapv (partial model/scene-shadow-map scene-shadow-renderer light-direction) objects)
        geometry               (model/render-joined-geometry geometry-renderer scene-render-vars planet-render-vars (first objects)
                                                             tree)
        clouds                 (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars plume-transforms
                                                            geometry)]
    {::planet-render-vars planet-render-vars
     ::scene-render-vars  scene-render-vars
     ::geometry           geometry
     ::shadow-vars        shadow-vars
     ::clouds             clouds
     ::object-shadows     object-shadows
     ::objects            objects}))


(defn render-frame
  [graphics frame tree]
  (let [planet-renderer      (::planet-renderer graphics)
        atmosphere-renderer  (::atmosphere-renderer graphics)
        scene-renderer       (::scene-renderer graphics)
        planet-render-vars   (::planet-render-vars frame)
        scene-render-vars    (::scene-render-vars frame)
        geometry             (::geometry frame)
        shadow-vars          (::shadow-vars frame)
        clouds               (::clouds frame)
        object-shadows       (::object-shadows frame)
        objects              (::objects frame)
        model-covers-planet? (< ^double (:sfsim.render/z-near scene-render-vars) ^double (:sfsim.render/z-near planet-render-vars))]
    (render/with-depth-test true
      (render/with-stencils
        ;; Clear color, depth, and stencil buffer
        (render/clear (vec3 0 1 0) 0.0 0)
        (render/with-stencil-op-ref-and-mask GL11/GL_ALWAYS 0x4 0x4
          ;; Render model
          (model/render-scenes scene-renderer scene-render-vars shadow-vars object-shadows geometry clouds objects))
        (when model-covers-planet?
          ;; Only clear depth buffer
          (render/clear))
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x2 (if model-covers-planet? 0x6 0x2)
          ;; Render planet with cloud overlay
          (planet/render-planet planet-renderer planet-render-vars shadow-vars object-shadows geometry clouds tree))
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x1 0x7
          ;; Render atmosphere with cloud overlay
          (atmosphere/render-atmosphere atmosphere-renderer planet-render-vars geometry clouds))))))


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
