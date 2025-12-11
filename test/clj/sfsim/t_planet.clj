;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-planet
  (:require
    [clojure.math :refer (PI exp pow to-radians)]
    [comb.template :as template]
    [fastmath.matrix :refer (eye diagonal inverse)]
    [fastmath.vector :refer (vec3 vec4 dot mag)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.clouds :as clouds]
    [sfsim.conftest :refer (is-image roughly-vector)]
    [sfsim.cubemap :as cubemap]
    [sfsim.image :refer :all]
    [sfsim.interpolate :refer :all]
    [sfsim.matrix :refer :all :as matrix]
    [sfsim.planet :refer :all :as planet]
    [sfsim.quaternion :as q]
    [sfsim.render :refer :all]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer :all]
    [sfsim.util :refer :all])
  (:import
    (clojure.lang
      Keyword)
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.opengl
      GL30)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def radius 6378000.0)
(def max-height 100000.0)
(def ray-steps 10)
(def size 12)


(def earth
  #:sfsim.sphere{:centre (vec3 0 0 0)
                 :radius radius
                 :sfsim.atmosphere/height max-height
                 :sfsim.atmosphere/brightness (vec3 0.3 0.3 0.3)})


(facts "Create vertex array object for drawing cube map tiles"
       (let [a (vec3 -0.75 -0.5  -1.0)
             b (vec3 -0.5  -0.5  -1.0)
             c (vec3 -0.75 -0.25 -1.0)
             d (vec3 -0.5  -0.25 -1.0)]
         (with-redefs [cubemap/cube-map-corners (fn [^Keyword face ^long level ^long y ^long x]
                                                  (fact [face level y x] => [:sfsim.cubemap/face5 3 2 1])
                                                  [a b c d])]
           (let [arr (make-cube-map-tile-vertices :sfsim.cubemap/face5 3 2 1 10 100)]
             (subvec arr  0  3) => (vec a)
             (subvec arr  7 10) => (vec b)
             (subvec arr 14 17) => (vec c)
             (subvec arr 21 24) => (vec d)
             (subvec arr  3  5) => [0.05 0.05]
             (subvec arr 10 12) => [0.95 0.05]
             (subvec arr 17 19) => [0.05 0.95]
             (subvec arr 24 26) => [0.95 0.95]
             (subvec arr  5  7) => [0.005 0.005]
             (subvec arr 12 14) => [0.995 0.005]
             (subvec arr 19 21) => [0.005 0.995]
             (subvec arr 26 28) => [0.995 0.995]))))


(def fragment-white
  "#version 450 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")


(fact "Use vertex data to draw a quad"
      (offscreen-render 256 256
                        (let [indices   [0 1 3 2]
                              vertices  [-0.5 -0.5 0.5 0.0 0.0 0.0 0.0
                                         +0.5 -0.5 0.5 0.0 0.0 0.0 0.0
                                         -0.5  0.5 0.5 0.0 0.0 0.0 0.0
                                         +0.5  0.5 0.5 0.0 0.0 0.0 0.0]
                              program   (make-program :sfsim.render/vertex [vertex-planet]
                                                      :sfsim.render/fragment [fragment-white])
                              variables ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao       (make-vertex-array-object program indices vertices variables)]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (raster-lines (render-quads vao))
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/planet/quad.png" 2.0))


(tabular "Tessellation control shader to control outer tessellation of quad using a uniform integer"
         (fact
           (offscreen-render 256 256
                             (let [indices     [0 1 3 2]
                                   vertices    [-0.5 -0.5 0.5 0.25 0.25 0.0 0.0,
                                                +0.5 -0.5 0.5 0.75 0.25 0.0 0.0,
                                                -0.5  0.5 0.5 0.25 0.75 0.0 0.0,
                                                +0.5  0.5 0.5 0.75 0.75 0.0 0.0]
                                   program     (make-program :sfsim.render/vertex [vertex-planet]
                                                             :sfsim.render/tess-control [tess-control-planet]
                                                             :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                             :sfsim.render/geometry [(geometry-planet 0)]
                                                             :sfsim.render/fragment [fragment-white])
                                   variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   data        [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5  0.5 0.5, 0.5  0.5 0.5]
                                   surface     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                       #:sfsim.image{:width 2 :height 2 :data (float-array data)})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" ?neighbours)
                               (uniform-matrix4 program "world_to_camera" (eye 4))
                               (uniform-matrix4 program "projection" (eye 4))
                               (uniform-float program "z_near" 0.0)
                               (use-textures {0 surface})
                               (raster-lines (render-patches vao))
                               (destroy-texture surface)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result 5.0))
         ?neighbours ?result
         15          "test/clj/sfsim/fixtures/planet/tessellation.png"
         +1          "test/clj/sfsim/fixtures/planet/tessellation-0.png"
         +2          "test/clj/sfsim/fixtures/planet/tessellation-1.png"
         +4          "test/clj/sfsim/fixtures/planet/tessellation-2.png"
         +8          "test/clj/sfsim/fixtures/planet/tessellation-3.png")


(def texture-coordinates-probe
  (template/fn [selector]
    "#version 450 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} frag_in;
out vec3 fragColor;
void main()
{
  fragColor.rg = <%= selector %>;
  fragColor.b = 0;
}"))


(tabular "Test color texture coordinates"
         (fact
           (offscreen-render 256 256
                             (let [indices     [0 1 3 2]
                                   vertices    [-0.5 -0.5 0.5 0.25 0.25 0.25 0.25,
                                                +0.5 -0.5 0.5 0.75 0.25 0.75 0.25,
                                                -0.5  0.5 0.5 0.25 0.75 0.25 0.75,
                                                +0.5  0.5 0.5 0.75 0.75 0.75 0.75]
                                   program     (make-program :sfsim.render/vertex [vertex-planet]
                                                             :sfsim.render/tess-control [tess-control-planet]
                                                             :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                             :sfsim.render/geometry [(geometry-planet 0)]
                                                             :sfsim.render/fragment [(texture-coordinates-probe ?selector)])
                                   variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   data        (map #(* % ?scale) [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5  0.5 0.5, 0.5  0.5 0.5])
                                   surface     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                       #:sfsim.image{:width 2 :height 2 :data (float-array data)})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" 15)
                               (uniform-matrix4 program "tile_to_camera" (eye 4))
                               (uniform-vector3 program "tile_center" (vec3 0 0 0))
                               (uniform-matrix4 program "projection" (eye 4))
                               (uniform-float program "z_near" 0.5)
                               (use-textures {0 surface})
                               (render-patches vao)
                               (destroy-texture surface)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result 0.04))
         ?selector                            ?scale ?result
         "frag_in.colorcoord"                 1.0    "test/clj/sfsim/fixtures/planet/color-coords.png"
         "frag_in.point.xy + vec2(0.5, 0.5)"  1.0    "test/clj/sfsim/fixtures/planet/point.png"
         "frag_in.point.xy + vec2(0.5, 0.5)"  1.1    "test/clj/sfsim/fixtures/planet/scaled-point.png")


(fact "Apply transformation to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.6 -0.5 0.5 0.25 0.25 0.0 0.0
                                           +0.4 -0.5 0.5 0.75 0.25 0.0 0.0
                                           -0.6  0.5 0.5 0.25 0.75 0.0 0.0
                                           +0.4  0.5 0.5 0.75 0.75 0.0 0.0]
                              program     (make-program :sfsim.render/vertex [vertex-planet]
                                                        :sfsim.render/tess-control [tess-control-planet]
                                                        :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                        :sfsim.render/geometry [(geometry-planet 0)]
                                                        :sfsim.render/fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              data        [-0.6 -0.5 0.5, 0.4 -0.5 0.5, -0.6  0.5 0.5, 0.4  0.5 0.5]
                              vao         (make-vertex-array-object program indices vertices variables)
                              surface     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                  #:sfsim.image{:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "world_to_camera" (transformation-matrix (eye 3) (vec3 0.1 0 0)))
                          (uniform-matrix4 program "projection" (eye 4))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/clj/sfsim/fixtures/planet/tessellation.png" 5.0))


(fact "Apply projection matrix to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.5 -0.5 0.0 0.25 0.25 0.0 0.0
                                           +0.5 -0.5 0.0 0.75 0.25 0.0 0.0
                                           -0.5  0.5 0.0 0.25 0.75 0.0 0.0
                                           +0.5  0.5 0.0 0.75 0.75 0.0 0.0]
                              program     (make-program :sfsim.render/vertex [vertex-planet]
                                                        :sfsim.render/tess-control [tess-control-planet]
                                                        :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                        :sfsim.render/geometry [(geometry-planet 0)]
                                                        :sfsim.render/fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              data        [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5  0.5 0.0, 0.5  0.5 0.0]
                              surface     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                  #:sfsim.image{:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-float program "z_near" 0.0)
                          (uniform-matrix4 program "world_to_camera" (transformation-matrix (eye 3) (vec3 0 0 -2)))
                          (uniform-matrix4 program "projection" (projection-matrix 256 256 1.0 3.0 (/ PI 3)))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/clj/sfsim/fixtures/planet/projection.png" 0.9))


(fact "Scale vertex coordinates using given height field"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.5 -0.5 0.5 0.25 0.25 0.0 0.0
                                           +0.5 -0.5 0.5 0.75 0.25 0.0 0.0
                                           -0.5  0.5 0.5 0.25 0.75 0.0 0.0
                                           +0.5  0.5 0.5 0.75 0.75 0.0 0.0]
                              program     (make-program :sfsim.render/vertex [vertex-planet]
                                                        :sfsim.render/tess-control [tess-control-planet]
                                                        :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                        :sfsim.render/geometry [(geometry-planet 0)]
                                                        :sfsim.render/fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              data        [-0.25 -0.25 0.25, 0.5 -0.5 0.5, -0.75 0.75 0.75, 1.0 1.0 1.0]
                              surface     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                  #:sfsim.image{:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "world_to_camera" (eye 4))
                          (uniform-matrix4 program "projection" (eye 4))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/clj/sfsim/fixtures/planet/heightfield.png" 1.6))


(defn ray-scatter
  [x view-direction light-direction above-horizon]
  (let [value (* (pow (max (dot view-direction light-direction) 0.0) 10) (exp (/ (- radius (mag x)) 5500.0)))]
    (vec3 value value value)))


(def surface-radiance-earth (partial atmosphere/surface-radiance earth ray-scatter ray-steps))
(def surface-radiance-space-earth (atmosphere/surface-radiance-space earth [size size]))
(def S (pack-matrices (make-lookup-table surface-radiance-earth surface-radiance-space-earth)))


(defn surface-radiance-shader-test
  [setup probe & shaders]
  (fn [uniforms args]
    (with-invisible-window
      (let [indices          [0 1 3 2]
            vertices         [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            surface-radiance (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                     #:sfsim.image{:width size :height size :data S})
            program          (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                           :sfsim.render/fragment (conj shaders (apply probe args)))
            vao              (make-vertex-array-object program indices vertices ["point" 3])
            tex              (texture-render-color
                               1 1 true
                               (use-program program)
                               (uniform-sampler program "surface_radiance" 0)
                               (apply setup program uniforms)
                               (use-textures {0 surface-radiance})
                               (render-quads vao))
            img           (rgb-texture->vectors3 tex)]
        (destroy-texture tex)
        (destroy-texture surface-radiance)
        (destroy-vertex-array-object vao)
        (destroy-program program)
        (get-vector3 img 0 0)))))


(def surface-radiance-probe
  (template/fn [px py pz lx ly lz]
    "#version 450 core
out vec3 fragColor;
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
void main()
{
  vec3 point = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = surface_radiance_function(point, light_direction);
}"))


(def surface-radiance-test
  (surface-radiance-shader-test
    (fn [program radius max-height size]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height)
      (uniform-int program "surface_sun_elevation_size" size)
      (uniform-int program "surface_height_size" size))
    surface-radiance-probe surface-radiance-function shaders/surface-radiance-forward shaders/interpolate-2d
    shaders/height-to-index shaders/horizon-distance shaders/sun-elevation-to-index shaders/convert-2d-index))


(tabular "Shader function to determine ambient light scattered by the atmosphere"
         (fact ((surface-radiance-test [radius max-height size] [?x ?y ?z ?lx ?ly ?lz]) 0) => (roughly ?value 1e-3))
         ?x ?y ?z              ?lx ?ly ?lz ?value
         0  0  radius          0   0   1   0.770411
         0  0  radius          1   0   0   0.095782
         0  0  (+ radius 1000) 0   0   1   0.639491)


(def vertex-planet-probe
  "#version 450 core
in vec3 point;
in vec2 colorcoord;
uniform float radius;
out GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.colorcoord = colorcoord;
  vs_out.point = vec3(0, 0, radius);
}")


(def fake-transmittance
  "#version 450 core
vec3 transmittance_track(vec3 p, vec3 q)
{
  float dist = distance(p, q);
  if (dist < 150) return vec3(1, 1, 1);
  if (dist < 150000) return vec3(0.5, 0.5, 0.5);
  return vec3(0, 0, 0);
}")


(def fake-ray-scatter
  "#version 450 core
uniform vec3 scatter;
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  return scatter;
}")


(def fake-attenuation
"#version 450 core
uniform float amplification;
vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
vec4 attenuate(vec3 light_direction, vec3 start, vec3 point, vec4 incoming)
{
  vec3 transmittance = transmittance_track(start, point);
  vec3 in_scatter = ray_scatter_track(light_direction, start, point) * amplification;
  return vec4(incoming.rgb * transmittance + in_scatter * incoming.a, incoming.a);
}")


(def opacity-lookup-mock
  "#version 450 core
float opacity_cascade_lookup(vec4 point)
{
  return 1.0;
}")


(def sampling-offset-mock
  "#version 450 core
float sampling_offset()
{
  return 0.5;
}")


(def cloud-overlay-mock
  "#version 450 core
uniform float clouds;
vec4 cloud_overlay()
{
  return vec4(clouds, clouds, clouds, clouds);
}")


(def planet-and-cloud-shadows-mock
  "#version 450 core
uniform float shadow;
float planet_and_cloud_shadows(vec4 point)
{
  return shadow;
}")


(def land-noise-mock
  "#version 450 core
uniform float land_noise_value;
float land_noise(vec3 point)
{
  return land_noise_value;
}")


(defn make-mocked-planet-program
  []
  (make-program :sfsim.render/vertex [vertex-planet-probe]
                :sfsim.render/fragment [(last (fragment-planet 3 0)) opacity-lookup-mock sampling-offset-mock cloud-overlay-mock
                                        planet-and-cloud-shadows-mock fake-transmittance fake-ray-scatter fake-attenuation
                                        shaders/ray-shell shaders/is-above-horizon atmosphere/transmittance-point shaders/phong
                                        shaders/limit-interval surface-radiance-function land-noise-mock shaders/remap
                                        (last (clouds/environmental-shading 3)) (last (clouds/overall-shading 3 []))
                                        (last atmosphere/attenuation-track) (last atmosphere/attenuation-point)]))


(defn setup-static-uniforms
  [program]
  ;; Moved this code out of the test below, otherwise method is too large
  (use-program program)
  (uniform-sampler program "day_night" 0)
  (uniform-sampler program "normals" 1)
  (uniform-sampler program "transmittance" 2)
  (uniform-sampler program "ray_scatter" 3)
  (uniform-sampler program "mie_strength" 4)
  (uniform-sampler program "surface_radiance" 5)
  (uniform-sampler program "water" 6)
  (uniform-sampler program "worley" 7)
  (uniform-float program "specular" 100.0)
  (uniform-float program "max_height" 100000.0)
  (uniform-float program "water_threshold" 0.5)
  (uniform-vector3 program "water_color" (vec3 0.09 0.11 0.34)))


(defn setup-uniforms
  [program size ?albedo ?refl ?lnoise ?clouds ?shd ?radius ?dist ?lx ?ly ?lz ?a]
  ;; Moved this code out of the test below, otherwise method is too large
  (use-program program)
  (uniform-int program "height_size" size)
  (uniform-int program "elevation_size" size)
  (uniform-int program "light_elevation_size" size)
  (uniform-int program "heading_size" size)
  (uniform-int program "transmittance_height_size" size)
  (uniform-int program "transmittance_elevation_size" size)
  (uniform-int program "surface_height_size" size)
  (uniform-int program "surface_sun_elevation_size" size)
  (uniform-float program "albedo" ?albedo)
  (uniform-float program "reflectivity" ?refl)
  (uniform-float program "land_noise_value" ?lnoise)
  (uniform-float program "land_noise_scale" 1.0)
  (uniform-float program "land_noise_strength" 0.5)
  (uniform-float program "clouds" ?clouds)
  (uniform-float program "shadow" ?shd)
  (uniform-float program "radius" radius)
  (uniform-float program "z_near" 0.0)
  (uniform-vector3 program "origin" (vec3 0 0 (+ ?radius ?dist)))
  (uniform-matrix4 program "world_to_camera" (transformation-matrix (eye 3) (vec3 0 0 (- 0 ?radius ?dist))))
  (uniform-vector3 program "light_direction" (vec3 ?lx ?ly ?lz))
  (uniform-float program "dawn_start" -0.05)
  (uniform-float program "dawn_end" 0.05)
  (uniform-float program "amplification" ?a))


(def planet-indices [0 1 3 2])


(def planet-vertices
  [-0.5 -0.5 0.5 0.25 0.25 0.5 0.5
   +0.5 -0.5 0.5 0.75 0.25 0.5 0.5
   -0.5  0.5 0.5 0.25 0.75 0.5 0.5
   +0.5  0.5 0.5 0.75 0.75 0.5 0.5])


(defn planet-textures
  [colors nx ny nz tr tg tb s ar ag ab water size]
  (let [day-night     (make-rgb-texture-array :sfsim.texture/linear :sfsim.texture/clamp
                                              [(slurp-image (str "test/clj/sfsim/fixtures/planet/" colors ".png"))
                                               (slurp-image (str "test/clj/sfsim/fixtures/planet/night.png"))])
        normals       (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width 2 :height 2
                                                            :data (float-array (flatten (repeat 4 [nx ny nz])))})
        transmittance (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width size :height size
                                                            :data (float-array (flatten (repeat (* size size) [tr tg tb])))})
        ray-scatter   (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width (* size size) :height (* size size)
                                                            :data (float-array (repeat (* size size size size 3) s))})
        mie-strength  (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width (* size size) :height (* size size)
                                                            :data (float-array (repeat (* size size size size 3) 0))})
        radiance      (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width size :height size
                                                            :data (float-array (flatten (repeat (* size size) [ar ag ab])))})
        water         (make-ubyte-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                             #:sfsim.image{:width 2 :height 2 :data (byte-array (repeat 8 water))})
        worley-data   (float-array (repeat (* 2 2 2) 1.0))
        worley        (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                             #:sfsim.image{:width 2 :height 2 :depth 2 :data worley-data})]
    [day-night normals transmittance ray-scatter mie-strength radiance water worley]))


(tabular "Fragment shader to render planetary surface"
         (fact
           (offscreen-render 256 256
                             (let [program   (make-mocked-planet-program)
                                   variables ["point" 3 "colorcoord" 2 "surfacecoord" 2]
                                   vao       (make-vertex-array-object program planet-indices planet-vertices variables)
                                   radius    6378000
                                   size      7
                                   textures  (planet-textures ?colors ?nx ?ny ?nz ?tr ?tg ?tb ?s ?ar ?ag ?ab ?water size)]
                               (clear (vec3 0 0 0))
                               (setup-static-uniforms program)
                               (setup-uniforms program size ?alb ?refl ?lnoise ?clouds ?shd radius ?dist ?lx ?ly ?lz ?a)
                               (use-textures (zipmap (range) textures))
                               (render-quads vao)
                               (doseq [tex textures] (destroy-texture tex))
                               (destroy-vertex-array-object vao)
                               (destroy-program program)))
           => (is-image (str "test/clj/sfsim/fixtures/planet/" ?result ".png") 0.33))
         ?colors   ?alb ?a  ?tr ?tg ?tb ?ar ?ag ?ab ?water ?dist  ?s  ?refl ?lnoise ?clouds ?shd ?lx ?ly ?lz ?nx ?ny ?nz ?result
         "white"   PI   1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "fragment"
         "pattern" PI   1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "colors"
         "white"   PI   1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0.8 0   0.6 "normal"
         "white"   0.9  1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "albedo"
         "white"   0.9  2.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "amplify"
         "white"   PI   1.0  1   0   0   0   0   0     0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "transmit"
         "pattern" PI   1.0  1   1   1   0.2 0.3 0.5   0      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "ambient"
         "white"   PI   1.0  1   1   1   0   0   0   220      100 0   0.0  0.0  0.0     1.0  0   0   1   0   0   0   "water"
         "white"   PI   1.0  1   1   1   0   0   0   255      100 0   0.5  0.0  0.0     1.0  0   0   1   0   0   1   "reflection1"
         "white"   PI   1.0  1   1   1   0   0   0   255      100 0   0.5  0.0  0.0     1.0  0   0.6 0.8 0   0   1   "reflection2"
         "pattern" PI   1.0  1   1   1   0   0   0   255      100 0   0.5  0.0  0.0     1.0  0   0  -1   0   0   1   "reflection3"
         "white"   PI   1.0  1   1   1   0   0   0     0    10000 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "absorption"
         "white"   PI   1.0  1   1   1   0   0   0     0   200000 0   0.0  0.0  0.0     1.0  0   0   1   0   0   1   "absorption"
         "white"   PI   1.0  1   1   1   0   0   0     0      100 0.5 0.0  0.0  0.0     1.0  0   0   1   0   0   1   "scatter"
         "pattern" PI   1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.5     1.0  0   0   1   0   0   1   "clouds"
         "pattern" PI   1.0  1   1   1   0   0   0     0      100 0   0.0  0.0  0.0     0.5  0   0   1   0   0   1   "shadow"
         "white"   PI   1.0  1   1   1   0   0   0     0      100 0   0.0  1.0  0.0     1.0  0   0   1   0   0   1   "noise")


(def fragment-white-tree
  "#version 450 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")


(tabular "Render a planetary tile using the specified texture keys and neighbour tessellation"
         (fact
           (offscreen-render 256 256
                             (let [program    (make-program :sfsim.render/vertex [vertex-planet]
                                                            :sfsim.render/tess-control [tess-control-planet]
                                                            :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                                            :sfsim.render/geometry [(geometry-planet 0)]
                                                            :sfsim.render/fragment [fragment-white-tree])
                                   indices    [0 2 3 1]
                                   face       :sfsim.cubemap/face0
                                   vertices   (make-cube-map-tile-vertices face 0 0 0 9 9)
                                   data       (flatten
                                                (for [y (range 1.0 -1.25 -0.25) x (range -1.0 1.25 0.25)]
                                                  [(* x 0.5) (* y 0.5) 0.5]))
                                   surf-tex   (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                                      #:sfsim.image{:width 9 :height 9 :data (float-array data)})
                                   vao        (make-vertex-array-object program indices vertices
                                                                        ["point" 3 "surfacecoord" 2 "colorcoord" 2])
                                   transform  (transformation-matrix (eye 3) (vec3 0 0 2.5))
                                   projection (projection-matrix 256 256 0.5 5.0 (/ PI 3))
                                   neighbours {:sfsim.quadtree/up    ?up
                                               :sfsim.quadtree/left  ?left
                                               :sfsim.quadtree/down  ?down
                                               :sfsim.quadtree/right ?right}
                                   tile       (merge {:sfsim.planet/vao vao :sfsim.planet/surf-tex surf-tex
                                                      :sfsim.quadtree/center (vec3 0 0 0.5)} neighbours)]
                               (use-program program)
                               (clear (vec3 0 0 0))
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 8)
                               (uniform-int program "low_detail" 4)
                               (uniform-matrix4 program "projection" projection)
                               (raster-lines (render-tile program tile (inverse transform) [] [:sfsim.planet/surf-tex]))
                               (destroy-texture surf-tex)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image (str "test/clj/sfsim/fixtures/planet/" ?result) 0.01))
         ?up   ?left ?down ?right ?result
         true  true  true  true   "tile.png"
         false true  true  true   "tile-up.png"
         true  false true  true   "tile-left.png"
         true  true  false true   "tile-down.png"
         true  true  true  false  "tile-right.png")


(defn render-tile-calls
  [program node transform scene-shadows texture-keys]
  (let [calls (atom [])]
    (with-redefs [render-tile (fn [program tile transform scene-shadows texture-keys]
                                (swap! calls conj [program tile transform scene-shadows texture-keys])
                                nil)]
      (render-tree program node transform scene-shadows texture-keys)
      @calls)))


(let [vao :sfsim.planet/vao]
  (tabular "Call each tile in tree to be rendered"
           (fact (render-tile-calls ?program ?node ?transform [] [:sfsim.planet/surf-tex]) => ?result)
           ?program ?transform ?node                                ?result
           1234 :transform {}                                       []
           1234 :transform {vao 42}                                 [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face0 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face1 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face2 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face3 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face4 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face5 {vao 42}}         [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]
           1234 :transform {:sfsim.cubemap/face3 {:sfsim.quadtree/quad2 {vao 42}}}
           [[1234 {vao 42} :transform [] [:sfsim.planet/surf-tex]]]))


(facts "Maximum shadow depth for cloud shadows"
       (render-depth 4.0 1.0 0.0) => 3.0
       (render-depth 3.0 2.0 0.0) => 4.0
       (render-depth 4.0 0.0 1.0) => 3.0
       (render-depth 4.0 1.0 1.0) => 6.0)


(defn render-depth-mock
  ^double [^double radius ^double max-height ^double cloud-top]
  (fact [radius cloud-top] => [1000.0 100.0])
  300.0)


(facts "Create hashmap with render variables for rendering current frame of planet"
       (let [planet {:sfsim.planet/radius 1000.0}
             cloud  {:sfsim.clouds/cloud-top 100.0}
             render #:sfsim.render{:fov 0.5 :min-z-near 1.0 :cloud-subsampling 2}
             pos1   (vec3 (+ 1000 150) 0 0)
             pos2   (vec3 (+ 1000 75) 0 0)
             opos   (vec3 0 0 0)
             o      (q/rotation 0.0 (vec3 0 0 1))
             m-vars {:sfsim.model/object-radius 30.0 :sfsim.model/time 0.0 :sfsim.model/pressure 1.0 :sfsim.model/throttle 0.0}
             light  (vec3 1 0 0)]
         (with-redefs [planet/render-depth render-depth-mock
                       matrix/quaternion->matrix (fn [orientation] (fact [orientation] orientation => o) :rotation-matrix)
                       matrix/transformation-matrix (fn [rot _pos] (fact rot => :rotation-matrix) (eye 4))
                       matrix/projection-matrix (fn [w h _near _far fov]
                                                    (fact [w h fov] => #(contains? #{[320 240 0.5] [640 480 0.5]} %))
                                                    (diagonal 1 2 3 4))]
           (:sfsim.render/origin (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => pos1
           (:sfsim.render/z-near (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => (roughly 47.549 1e-3)
           (:sfsim.render/z-near (make-planet-render-vars planet cloud render 640 480 pos2 o light opos o m-vars)) => 1.0
           (:sfsim.render/z-far (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => 300.0
           (:sfsim.render/camera-to-world (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => (eye 4)
           (:sfsim.render/projection (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => (diagonal 1 2 3 4)
           (:sfsim.render/light-direction (make-planet-render-vars planet cloud render 640 480 pos1 o light opos o m-vars)) => light)))


(facts "Render planet geometry"
       (with-invisible-window
         (let [data             {:sfsim.planet/config {:sfsim.planet/tilesize 3}}
               render-vars      #:sfsim.render{:overlay-projection (projection-matrix 160 120 0.1 10.0 (to-radians 60))
                                               :camera-to-world (transformation-matrix (eye 3) (vec3 0 0 5))}
               renderer         (make-planet-geometry-renderer data)
               indices          [0 2 3 1]
               vertices         (make-cube-map-tile-vertices :sfsim.cubemap/face0 0 0 0 3 3)
               vao              (make-vertex-array-object (:sfsim.planet/program renderer) indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
               data             [-1  1 0, 0  1 0, 1  1 0,
                                 -1  0 0, 0  0 0, 1  0 0,
                                 -1 -1 0, 0 -1 0, 1 -1 0]
               surface          (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                        #:sfsim.image{:width 3 :height 3 :data (float-array data)})
               tree             {:sfsim.planet/vao vao :sfsim.planet/surf-tex surface :sfsim.quadtree/center (vec3 0 0 2)}
               geometry         (clouds/render-cloud-geometry 160 120 (render-planet-geometry renderer render-vars tree))]
           (get-vector4 (rgba-texture->vectors4 (:sfsim.clouds/points geometry)) 60 80)
           => (roughly-vector (vec4 0.004 0.004 -1.0 0.0) 1e-3)
           (get-float (float-texture-2d->floats (:sfsim.clouds/distance geometry)) 60 80)
           => (roughly 3.0 1e-3)
           (clouds/destroy-cloud-geometry geometry)
           (destroy-texture surface)
           (destroy-vertex-array-object vao)
           (destroy-planet-geometry-renderer renderer))))


(GLFW/glfwTerminate)
