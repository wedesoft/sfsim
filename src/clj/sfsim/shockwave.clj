;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.shockwave
    (:require
      [fastmath.matrix :refer (mulm inverse)]
      [comb.template :as template]
      [sfsim.render :refer (uniform-float use-program uniform-int render-quads framebuffer-render uniform-sampler use-textures
                            make-program destroy-program make-vertex-array-object destroy-vertex-array-object uniform-matrix4
                            with-stencil-op-ref-and-mask uniform-vector3 with-culling)]
      [sfsim.texture :refer (make-empty-texture-2d destroy-texture disable-compare-mode)]
      [sfsim.bluenoise :as bluenoise]
      [sfsim.plume :refer (geometry-point geometry-distance setup-dynamic-overlay-uniforms)]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.shaders :refer (vertex-passthrough ray-box ray-sphere limit-interval) :as shaders])
    (:import
      (org.lwjgl.opengl
        GL11 GL30)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def shockfront (slurp "resources/shaders/shockwave/shockfront.glsl"))


(def shockwave-transfer
  [shockfront (slurp "resources/shaders/shockwave/shockwave-transfer.glsl")])


(def curvature (slurp "resources/shaders/shockwave/curvature.glsl"))


(def fragment-jump-flooding-init (slurp "resources/shaders/shockwave/fragment-jump-flooding-init.glsl"))


(def fragment-jump-flooding-step (slurp "resources/shaders/shockwave/fragment-jump-flooding-step.glsl"))


(def vertex-shockwave (slurp "resources/shaders/shockwave/vertex.glsl"))


(defn sample-shockwave-segment
  [outer]
  (template/eval (slurp "resources/shaders/shockwave/sample-shockwave-segment.glsl") {:outer outer}))


(defn shockwave-segment
  [outer]
  [(sample-shockwave-segment outer) shaders/ray-sphere shaders/limit-interval atmosphere/attenuation-track shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/segment.glsl") {:type "shockwave" :outer outer})])


(def shockwave-outer
  (shockwave-segment true))


(def shockwave-point
  (shockwave-segment false))


(defn fragment-shockwave
  [outer]
  [ray-box shockwave-transfer bluenoise/sampling-offset geometry-point geometry-distance limit-interval
   ray-sphere (sample-shockwave-segment outer) atmosphere/attenuation-track (shockwave-segment outer)
   (template/eval (slurp "resources/shaders/shockwave/fragment.glsl") {:outer outer})])


(def shockwave-indices
  [4 5 7 6    ; front (+z)
   1 0 2 3    ; back  (-z)
   0 4 6 2    ; left  (-x)
   5 1 3 7    ; right (+x)
   2 6 7 3    ; top   (+y)
   0 1 5 4])  ; bottom (-y)


(def shockwave-vertices
  [-1.0 -1.0  0.0
    1.0 -1.0  0.0
   -1.0  1.0  0.0
    1.0  1.0  0.0
   -1.0 -1.0  1.0
    1.0 -1.0  1.0
   -1.0  1.0  1.0
    1.0  1.0  1.0])


(defn setup-static-shockwave-uniforms
  [program bluenoise atmosphere-luts shockwave-radius size]
  (use-program program)
  (uniform-sampler program "camera_point" 0)
  (uniform-sampler program "dist" 1)
  (uniform-sampler program "flood" 2)
  (uniform-sampler program "bluenoise" 3)
  (atmosphere/setup-atmosphere-uniforms program atmosphere-luts 4 false)
  (uniform-int program "noise_size" (:sfsim.texture/width bluenoise))
  (uniform-float program "shockwave_radius" shockwave-radius)
  (uniform-float program "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
  (uniform-float program "shockwave_step" 0.5)
  (uniform-float program "shockwave_strength" 0.1))


(def depth-source
"#version 450 core
uniform sampler2D depth;
float depth_source(vec2 uv)
{
  return texture(depth, uv).r;
}")


(def normal-source
"#version 450 core
uniform sampler2D normals;
vec4 normal_source(vec2 uv)
{
  return texture(normals, uv);
}")


(defn make-shockwave-program
  [vertex-shader fragment-shader]
  (make-program :sfsim.render/vertex [vertex-shader]
                :sfsim.render/fragment [fragment-shader]))


(defn make-shockwave-renderer
  ([data bluenoise atmosphere-luts shockwave-radius max-curvature-radius]
   (make-shockwave-renderer data bluenoise atmosphere-luts shockwave-radius max-curvature-radius
                            depth-source normal-source shockfront))
  ([data bluenoise atmosphere-luts shockwave-radius max-curvature-radius depth-source normal-source shockfront]
   (let [config            (::config data)
         size              (::shockwave-size config)
         indices           [0 1 3 2]
         vertices          [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         program-init      (make-program :sfsim.render/vertex [vertex-passthrough]
                                         :sfsim.render/fragment [fragment-jump-flooding-init curvature depth-source normal-source])
         program-step      (make-program :sfsim.render/vertex [vertex-passthrough]
                                         :sfsim.render/fragment [fragment-jump-flooding-step shockfront])
         vao               (make-vertex-array-object program-init indices vertices ["point" 3])
         program-point     (make-shockwave-program vertex-shockwave (fragment-shockwave false))
         program-outer     (make-shockwave-program vertex-shockwave (fragment-shockwave true))
         vao-shockwave     (make-vertex-array-object program-outer shockwave-indices shockwave-vertices ["point" 3])]
     (doseq [program [program-point program-outer]]
            (setup-static-shockwave-uniforms program bluenoise atmosphere-luts shockwave-radius size))
     {::size                 size
      ::bluenoise            bluenoise
      ::shockwave-radius     shockwave-radius
      ::max-curvature-radius max-curvature-radius
      ::program-init         program-init
      ::program-step         program-step
      ::vao                  vao
      ::program-point        program-point
      ::program-outer        program-outer
      ::vao-shockwave        vao-shockwave})))


(defn destroy-shockwave-renderer
  [{::keys [program-init program-step vao program-point program-outer vao-shockwave]}]
  (destroy-vertex-array-object vao)
  (destroy-program program-step)
  (destroy-program program-init)
  (destroy-program program-outer)
  (destroy-program program-point)
  (destroy-vertex-array-object vao-shockwave))


(defn jump-flooding-initialisation
  [{::keys [program-init vao size shockwave-radius max-curvature-radius]} fun]
  (let [flood (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
    (framebuffer-render size size :sfsim.render/cullback nil [flood]
                        (use-program program-init)
                        (uniform-int program-init "size" size)
                        (uniform-float program-init "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
                        (uniform-float program-init "shockwave_radius" shockwave-radius)
                        (uniform-float program-init "max_curvature_radius" max-curvature-radius)
                        (fun program-init)
                        (render-quads vao))
    flood))


(defn jump-flooding-step
  [{::keys [program-step vao size shockwave-radius]} fun]
  (fn [flood step]
      (let [result (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
        (framebuffer-render size size :sfsim.render/noculling nil [result]
                            (use-program program-step)
                            (uniform-sampler program-step "flood" 0)
                            (uniform-int program-step "size" size)
                            (uniform-int program-step "step" step)
                            (uniform-float program-step "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
                            (fun program-step)
                            (use-textures {0 flood})
                            (render-quads vao))
        (destroy-texture flood)
        result)))


(defn setup-shockwave-shape
  [mach]
  (fn [program]
      (uniform-float program "mach" mach)))


(defn setup-shockwave-sources
  [wind-shadow mach]
  (fn [program]
      (uniform-sampler program "depth" 0)
      (uniform-sampler program "normals" 1)
      ((setup-shockwave-shape mach) program)
      ;; Have to disable compare mode for the depth texture, otherwise it cannot be used as a sampler2D texture!
      (disable-compare-mode (:sfsim.model/shadows wind-shadow))
      (use-textures {0 (:sfsim.model/shadows wind-shadow)
                     1 (:sfsim.model/normals wind-shadow)})))


(defn halving
  "Generate halving sequence of integers"
  [size]
  (rest (take-while pos? (iterate #(bit-shift-right ^long % 1) size))))


(defn jump-flooding-algorithm
  [{::keys [size] :as shockwave-renderer} wind-shadow mach]
  (let [initial-shockwave (jump-flooding-initialisation shockwave-renderer (setup-shockwave-sources wind-shadow mach))]
    (reduce (jump-flooding-step shockwave-renderer (setup-shockwave-shape mach)) initial-shockwave (halving size))))


(defn destroy-shockwave-geometry
  [flood]
  (destroy-texture flood))


(defn setup-dynamic-shockwave-uniforms
  [program cloud-render-vars wind-shadow mach projection points dist flood bluenoise]
  (let [overlay-width        (:sfsim.render/overlay-width cloud-render-vars)
        overlay-height       (:sfsim.render/overlay-height cloud-render-vars)
        origin               (:sfsim.render/origin cloud-render-vars)
        camera-to-world      (:sfsim.render/camera-to-world cloud-render-vars)
        matrices             (:sfsim.model/matrices wind-shadow)
        world-to-object      (:sfsim.matrix/world-to-object matrices)
        object-to-ndc        (:sfsim.matrix/object-to-shadow-ndc matrices)
        object-to-shockwave  (:sfsim.matrix/object-to-shadow matrices)
        camera-to-object     (mulm world-to-object camera-to-world)
        camera-to-ndc        (mulm object-to-ndc camera-to-object)
        camera-to-shockwave  (mulm object-to-shockwave camera-to-object)
        ndc-to-camera        (inverse camera-to-ndc)]
    (uniform-int program "overlay_width" overlay-width)
    (uniform-int program "overlay_height" overlay-height)
    (uniform-vector3 program "origin" origin)
    (uniform-matrix4 program "camera_to_world" camera-to-world)
    (uniform-float program "mach" mach)
    (uniform-matrix4 program "projection" projection)
    (uniform-matrix4 program "ndc_to_camera" ndc-to-camera)
    (uniform-matrix4 program "camera_to_shockwave" camera-to-shockwave)
    (use-textures {0 points 1 dist 2 flood 3 bluenoise})))


(defn render-shockwave-overlay
  [{::keys [program-outer program-point vao-shockwave bluenoise]} points dist wind-shadow flood
   cloud-render-vars mach projection]
  (with-culling :sfsim.render/cullfront
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
      (use-program program-outer)
      (setup-dynamic-overlay-uniforms program-outer cloud-render-vars)
      (setup-dynamic-shockwave-uniforms program-outer cloud-render-vars wind-shadow mach projection
                                        points dist flood bluenoise)
      (render-quads vao-shockwave))
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
      (use-program program-point)
      (setup-dynamic-overlay-uniforms program-point cloud-render-vars)
      (setup-dynamic-shockwave-uniforms program-point cloud-render-vars wind-shadow mach projection
                                        points dist flood bluenoise)
      (render-quads vao-shockwave))
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
      (use-program program-point)
      (render-quads vao-shockwave))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
