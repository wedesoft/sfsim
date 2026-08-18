;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.graphics
    "High-level graphics code"
    (:require
      [fastmath.matrix :refer (mulm mulv inverse)]
      [fastmath.vector :refer (vec4 vec3)]
      [sfsim.config :as config]
      [sfsim.clouds :as clouds]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.aerodynamics :as aerodynamics]
      [sfsim.planet :as planet]
      [sfsim.model :as model]
      [sfsim.render :as render]
      [sfsim.physics :as physics]
      [sfsim.lighting :as lighting]
      [sfsim.texture :as texture]
      [sfsim.opacity :as opacity]
      [sfsim.matrix :as matrix])
   (:import
    (org.lwjgl.opengl
      GL11)))


(defn get-thruster-transforms
  [model rcs-names]
  (into {}
        (remove nil? (map (fn [rcs-name] (some->> (model/get-node-transform model rcs-name)
                                                  (vector rcs-name)))
                          rcs-names))))


(defn make-graphics2
  [models]
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
        scene-renderer          (model/make-scene-geometry-renderer true)
        object-radius           (or (::object-radius (first models)) (:sfsim.model/object-radius config/model-config))
        scene-shadow-renderer   (model/make-scene-shadow-renderer (:sfsim.opacity/scene-shadow-size config/shadow-config)
                                                                  object-radius)
        lighting-renderer       (lighting/make-lighting-renderer {:sfsim.render/config config/render-config
                                                                  :sfsim.planet/config config/planet-config
                                                                  :sfsim.opacity/data opacity-data
                                                                  :sfsim.clouds/data cloud-data
                                                                  :sfsim.atmosphere/luts atmosphere-luts})
        scenes                  (mapv (comp model/read-gltf ::model-file) models)
        bsp-tree                (some-> (first scenes) (model/get-bsp-tree "BSP"))
        thruster-transforms     (some-> (first scenes) (get-thruster-transforms (physics/all-rcs)))
        opengl-scenes           (mapv (partial model/load-scene-into-opengl (model/geometry-program-selection scene-renderer)) scenes)]
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
     ::scene-geometry-renderer scene-renderer
     ::scene-shadow-renderer scene-shadow-renderer
     ::lighting-renderer lighting-renderer
     ::bsp-tree bsp-tree
     ::thruster-transforms thruster-transforms
     ::scenes opengl-scenes}))


(defn destroy-graphics2
  [graphics]
  (doseq [scene (::scenes graphics)] (model/destroy-scene scene))
  (lighting/destroy-lighting-renderer (::lighting-renderer graphics))
  (model/destroy-scene-shadow-renderer (::scene-shadow-renderer graphics))
  (model/destroy-scene-geometry-renderer (::scene-geometry-renderer graphics))
  (atmosphere/destroy-atmosphere-geometry-renderer (::atmosphere-geometry-renderer graphics))
  (planet/destroy-planet-geometry-renderer (::planet-geometry-renderer graphics))
  (model/destroy-joined-geometry-renderer (::cloud-geometry-renderer graphics))
  (clouds/destroy-cloud-renderer (::cloud-renderer graphics))
  (opacity/destroy-opacity-renderer (::opacity-renderer graphics))
  (planet/destroy-planet-shadow-renderer (::planet-shadow-renderer graphics))
  (clouds/destroy-cloud-data (:sfsim.clouds/data graphics))
  (atmosphere/destroy-atmosphere-luts (:sfsim.atmosphere/luts graphics)))


(defn make-frame
  [graphics width height camera-position camera-orientation light-direction object-poses model-vars]
  (let [render-config          (:sfsim.render/config graphics)
        model-config           (:sfsim.model/data graphics)
        planet-config          (:sfsim.planet/config graphics)
        cloud-config           (:sfsim.clouds/config graphics)
        fov                    (:sfsim.render/fov render-config)
        spacecraft             (seq object-poses)
        object-position        (if spacecraft (::object-position (first object-poses)) camera-position)
        object-orientation     (if spacecraft (::object-orientation (first object-poses)) camera-orientation)
        planet-render-vars     (planet/make-planet-render-vars2 planet-config cloud-config render-config width height
                                                                camera-position camera-orientation light-direction)
        scene-render-vars      (when spacecraft
                                 (model/make-scene-render-vars render-config width height camera-position camera-orientation
                                                               light-direction object-position object-orientation
                                                               model-config model-vars))
        cloud-render-vars      (clouds/make-cloud-render-vars render-config planet-render-vars width height camera-position
                                                              camera-orientation light-direction object-position object-orientation)
        atmosphere-render-vars (atmosphere/make-atmosphere-render-vars width height fov light-direction)
        geometry-buffers       (model/make-geometry-buffers width height)]
    {::width                  width
     ::height                 height
     ::camera-position        camera-position
     ::camera-orientation     camera-orientation
     ::planet-render-vars     planet-render-vars
     ::scene-render-vars      scene-render-vars
     ::cloud-render-vars      cloud-render-vars
     ::light-direction        light-direction
     ::atmosphere-render-vars atmosphere-render-vars
     ::model-vars             model-vars
     ::object-poses           object-poses
     ::object-shadows         []
     ::geometry-buffers       geometry-buffers}))


(defn destroy-frame
  [frame]
  (doseq [object-shadow (::object-shadows frame)]
         (model/destroy-scene-shadow-map object-shadow))
  (texture/destroy-texture (::clouds frame))
  (clouds/destroy-cloud-geometry (::cloud-geometry frame))
  (opacity/destroy-opacity-and-shadow (::shadow-vars frame))
  (model/destroy-geometry-buffers (::geometry-buffers frame)))


(defn get-moved-scenes
  [frame graphics]
  (let [object-poses      (::object-poses frame)
        object-transforms (mapv (fn [{::keys [object-position object-orientation]}]
                                    (matrix/transformation-matrix (matrix/quaternion->matrix object-orientation) object-position))
                                object-poses)
        moved-scenes      (mapv #(assoc-in % [:sfsim.model/root :sfsim.model/transform] %2) (::scenes graphics) object-transforms)]
    moved-scenes))


(defn render-shadows
  [frame graphics tree]
  (let [shadow-data            (:sfsim.opacity/data graphics)
        cloud-data             (:sfsim.clouds/data graphics)
        planet-render-vars     (::planet-render-vars frame)
        scene-render-vars      (::scene-render-vars frame)
        shadow-render-vars     (if scene-render-vars
                                 (render/joined-render-vars2 planet-render-vars scene-render-vars)
                                 planet-render-vars)
        opacity-base           (:sfsim.clouds/opacity-base (:sfsim.clouds/config graphics))
        opacity-renderer       (::opacity-renderer graphics)
        planet-shadow-renderer (::planet-shadow-renderer graphics)]
    (assoc frame ::shadow-vars (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data
                                                                   cloud-data shadow-render-vars tree opacity-base))))

(defn render-cloud-geometry
  [frame graphics tree]
  (let [render-config           (:sfsim.render/config graphics)
        cloud-geometry-renderer (::cloud-geometry-renderer graphics)
        planet-render-vars      (::planet-render-vars frame)
        scene-render-vars       (::scene-render-vars frame)
        planet-geometry-vars    (render/make-subsampled-vars planet-render-vars render-config)
        scene-geometry-vars     (if scene-render-vars
                                  (render/make-subsampled-vars scene-render-vars render-config)
                                  planet-geometry-vars)
        moved-scenes            (get-moved-scenes frame graphics)]
    (assoc frame
           ::cloud-geometry
           (model/render-joined-geometry2 cloud-geometry-renderer scene-geometry-vars planet-geometry-vars moved-scenes tree))))


(defn plume-transforms
  [frame graphics rcs-names]
  (let [bsp-tree            (::bsp-tree graphics)
        thruster-transforms (::thruster-transforms graphics)
        camera-position     (::camera-position frame)
        camera-orientation  (::camera-orientation frame)
        object-poses        (::object-poses frame)
        object-position     (::object-position (first object-poses))
        object-orientation  (::object-orientation (first object-poses))
        object-to-world     (matrix/transformation-matrix (matrix/quaternion->matrix object-orientation) object-position)
        camera-to-world     (matrix/transformation-matrix (matrix/quaternion->matrix camera-orientation) camera-position)
        camera-to-object    (mulm (inverse object-to-world) camera-to-world)
        object-origin       (matrix/vec4->vec3 (mulv camera-to-object (vec4 0 0 0 1)))
        render-order        (model/bsp-render-order bsp-tree object-origin)]
    (map (fn [thruster] [thruster (thruster-transforms thruster)]) (filter (set rcs-names) render-order))))


(defn render-clouds
  [frame graphics rcs-names]
  (let [cloud-renderer      (::cloud-renderer graphics)
        cloud-render-vars   (::cloud-render-vars frame)
        model-vars          (::model-vars frame)
        shadow-vars         (::shadow-vars frame)
        cloud-geometry      (::cloud-geometry frame)
        plume-transforms    (if (::bsp-tree graphics) (plume-transforms frame graphics rcs-names) [])]
    (assoc frame ::clouds (clouds/render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars plume-transforms
                                                       cloud-geometry))))


(defn render-scene-shadows
  [frame graphics]
  (let [scene-shadow-renderer (::scene-shadow-renderer graphics)
        light-direction       (::light-direction frame)
        moved-scenes          (get-moved-scenes frame graphics)
        object-shadows        (mapv #(model/scene-shadow-map scene-shadow-renderer light-direction %) moved-scenes)]
    (assoc frame ::object-shadows object-shadows)))


(defn render-geometry
  [frame graphics tree]
  (let [planet-geometry-renderer     (::planet-geometry-renderer graphics)
        atmosphere-geometry-renderer (::atmosphere-geometry-renderer graphics)
        scene-geometry-renderer      (::scene-geometry-renderer graphics)
        moved-scenes                 (get-moved-scenes frame graphics)
        camera-position              (::camera-position frame)
        camera-orientation           (::camera-orientation frame)
        camera-to-world              (matrix/transformation-matrix (matrix/quaternion->matrix camera-orientation) camera-position)
        planet-render-vars           (::planet-render-vars frame)
        scene-render-vars            (::scene-render-vars frame)
        scene-projection             (if scene-render-vars
                                       (:sfsim.render/projection scene-render-vars)
                                       (:sfsim.render/projection planet-render-vars))
        atmosphere-render-vars       (::atmosphere-render-vars frame)
        geometry-buffers             (::geometry-buffers frame)
        model-covers-planet?         (when scene-render-vars
                                       (< ^double (:sfsim.render/z-near scene-render-vars)
                                          ^double (:sfsim.render/z-near planet-render-vars)))]
    (model/render-geometry
      geometry-buffers
      ;; Clear color, depth, and stencil buffer
      (render/clear (vec3 0 1 0) 0.0 0)
      (render/with-stencils
        (render/with-stencil-op-ref-and-mask GL11/GL_ALWAYS 0x4 0x4
          (doseq [moved-scene moved-scenes]
                 (model/render-scene-geometry2 scene-geometry-renderer scene-projection {:sfsim.render/camera-to-world camera-to-world}
                                               moved-scene)))
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x2 (if model-covers-planet? 0x6 0x2)
          (planet/render-planet-geometry2 planet-geometry-renderer planet-render-vars true tree))
        (render/with-stencil-op-ref-and-mask GL11/GL_GEQUAL 0x1 0x7
          (atmosphere/render-full-atmosphere-geometry atmosphere-geometry-renderer atmosphere-render-vars))))
    frame))


(defn render-lighting
  [frame graphics]
  (let [lighting-renderer  (::lighting-renderer graphics)
        shadow-config      (:sfsim.opacity/data graphics)
        camera-position    (::camera-position frame)
        camera-orientation (::camera-orientation frame)
        light-direction    (::light-direction frame)
        planet-render-vars (::planet-render-vars frame)
        cloud-geometry     (::cloud-geometry frame)
        cloud-render-vars  (::cloud-render-vars frame)
        object-shadows     (::object-shadows frame)
        geometry-buffers   (::geometry-buffers frame)
        width              (::width frame)
        height             (::height frame)
        clouds             (::clouds frame)
        shadow-vars        (::shadow-vars frame)]
    (lighting/render-lighting lighting-renderer width height geometry-buffers shadow-config camera-position camera-orientation
                              light-direction planet-render-vars cloud-render-vars shadow-vars cloud-geometry clouds
                              object-shadows)))


(defn make-graphics-data
  []
  (let [cloud-data (clouds/make-cloud-data config/cloud-config)]
    {:sfsim.render/config config/render-config
     :sfsim.planet/config config/planet-config
     :sfsim.model/data config/model-config
     :sfsim.clouds/data cloud-data
     :sfsim.atmosphere/luts (atmosphere/make-atmosphere-luts config/max-height)
     :sfsim.opacity/data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data)}))
