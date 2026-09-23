;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.shockwave
    (:require
      [sfsim.render :refer (uniform-float use-program uniform-int render-quads framebuffer-render uniform-sampler use-textures
                            make-program destroy-program make-vertex-array-object destroy-vertex-array-object uniform-matrix4)]
      [sfsim.texture :refer (make-empty-texture-2d destroy-texture disable-compare-mode)]
      [sfsim.bluenoise :as bluenoise]
      [sfsim.shaders :refer (vertex-passthrough ray-box)])
    (:import
      (org.lwjgl.opengl
        GL30)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def shockfront (slurp "resources/shaders/shockwave/shockfront.glsl"))


(def curvature (slurp "resources/shaders/shockwave/curvature.glsl"))


(def fragment-jump-flooding-init (slurp "resources/shaders/shockwave/fragment-jump-flooding-init.glsl"))


(def fragment-jump-flooding-step (slurp "resources/shaders/shockwave/fragment-jump-flooding-step.glsl"))


(def vertex-shockwave (slurp "resources/shaders/shockwave/vertex.glsl"))


(def fragment-shockwave (slurp "resources/shaders/shockwave/fragment.glsl"))


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


(defn make-shockwave-renderer
  [depth-source normal-source shockfront size bluenoise shockwave-radius max-curvature-radius]
  (let [indices           [0 1 3 2]
        vertices          [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        program-init      (make-program :sfsim.render/vertex [vertex-passthrough]
                                        :sfsim.render/fragment [fragment-jump-flooding-init curvature depth-source normal-source])
        program-step      (make-program :sfsim.render/vertex [vertex-passthrough]
                                        :sfsim.render/fragment [fragment-jump-flooding-step shockfront])
        vao               (make-vertex-array-object program-init indices vertices ["point" 3])
        program-shockwave (make-program :sfsim.render/vertex [vertex-shockwave]
                                        :sfsim.render/fragment [ray-box shockfront fragment-shockwave
                                                                bluenoise/sampling-offset])
        vao-shockwave     (make-vertex-array-object program-shockwave shockwave-indices shockwave-vertices ["point" 3])]
    {::size                 size
     ::bluenoise            bluenoise
     ::shockwave-radius     shockwave-radius
     ::max-curvature-radius max-curvature-radius
     ::program-init         program-init
     ::program-step         program-step
     ::vao                  vao
     ::program-shockwave    program-shockwave
     ::vao-shockwave        vao-shockwave}))


(defn destroy-shockwave-renderer
  [{::keys [program-init program-step vao program-shockwave vao-shockwave]}]
  (destroy-vertex-array-object vao)
  (destroy-program program-step)
  (destroy-program program-init)
  (destroy-program program-shockwave)
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


(defn jump-flooding-algorithm
  [{::keys [size] :as shockwave-renderer} wind-shadow mach]
  (let [initial-shockwave (jump-flooding-initialisation shockwave-renderer (setup-shockwave-sources wind-shadow mach))]
    (reduce (jump-flooding-step shockwave-renderer (setup-shockwave-shape mach)) initial-shockwave (halving size))))


(defn render-shockwave-overlay
  [{::keys [program-shockwave vao-shockwave bluenoise shockwave-radius size]} flood overlay-width overlay-height mach projection
   ndc-to-camera camera-to-ndc frame]
  (use-program program-shockwave)
  (uniform-sampler program-shockwave "points" 0)
  (uniform-sampler program-shockwave "flood" 1)
  (uniform-sampler program-shockwave "bluenoise" 2)
  (uniform-int program-shockwave "width" overlay-width)
  (uniform-int program-shockwave "height" overlay-height)
  (uniform-int program-shockwave "noise_size" (:sfsim.texture/width bluenoise))
  (uniform-float program-shockwave "shockwave_radius" shockwave-radius)
  (uniform-float program-shockwave "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
  (uniform-float program-shockwave "step" 0.01)
  (uniform-float program-shockwave "mach" mach)
  (uniform-matrix4 program-shockwave "projection" projection)
  (uniform-matrix4 program-shockwave "ndc_to_camera" ndc-to-camera)
  (uniform-matrix4 program-shockwave "camera_to_ndc" camera-to-ndc)
  (use-textures {0 (:sfsim.clouds/points (:sfsim.graphics/cloud-geometry frame))
                 1 flood
                 2 bluenoise})
  (render-quads vao-shockwave)
  (destroy-texture flood))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
