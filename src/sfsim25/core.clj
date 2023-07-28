(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin tan sqrt log exp)]
            [fastmath.matrix :refer (inverse eye)]
            [fastmath.vector :refer (vec3 add mult mag dot)]
            [sfsim25.render :refer (make-window destroy-window clear destroy-program destroy-texture destroy-vertex-array-object
                                    generate-mipmap make-float-cubemap make-float-texture-2d make-float-texture-3d
                                    make-program make-rgb-texture make-ubyte-texture-2d make-vector-texture-2d
                                    make-vertex-array-object onscreen-render render-quads texture-render-color-depth
                                    uniform-float uniform-int uniform-matrix4 uniform-sampler uniform-vector3 use-program
                                    use-textures shadow-cascade)]
            [sfsim25.atmosphere :refer (attenuation-outer attenuation-track phase phase-function ray-scatter-outer
                                        ray-scatter-track transmittance-outer transmittance-track
                                        vertex-atmosphere fragment-atmosphere)]
            [sfsim25.planet :refer (geometry-planet ground-radiance make-cube-map-tile-vertices
                                    surface-radiance-function tess-control-planet tess-evaluation-planet
                                    vertex-planet render-tree fragment-planet)]
            [sfsim25.quadtree :refer (increase-level? quadtree-update update-level-of-detail)]
            [sfsim25.clouds :refer (cloud-atmosphere cloud-base cloud-cover cloud-density cloud-noise cloud-planet
                                    cloud-profile cloud-transfer linear-sampling opacity-cascade
                                    opacity-cascade-lookup opacity-fragment opacity-lookup opacity-vertex
                                    sample-cloud sphere-noise cloud-overlay overall-shadow)]
            [sfsim25.bluenoise :as bluenoise]
            [sfsim25.matrix :refer (projection-matrix quaternion->matrix shadow-matrix-cascade split-mixed
                                    transformation-matrix)]
            [sfsim25.quaternion :as q]
            [sfsim25.util :refer (slurp-floats sqr)]
            [sfsim25.shaders :as shaders])
  (:import [org.lwjgl.opengl GL11]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def width 1280)
(def height 720)

(def vertex-shadow-planet
"#version 410 core
in vec3 point;
in vec2 heightcoord;
in vec2 colorcoord;
out VS_OUT
{
  vec2 heightcoord;
  vec2 colorcoord;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.heightcoord = heightcoord;
  vs_out.colorcoord = colorcoord;
}")

(def tess-evaluation-shadow-planet
"#version 410 core
layout(quads, equal_spacing, ccw) in;
uniform sampler2D surface;
uniform vec3 tile_center;
uniform mat4 recenter_and_transform;
uniform int shadow_size;
in TCS_OUT
{
  vec2 heightcoord;
  vec2 colorcoord;
} tes_in[];

out TES_OUT
{
  vec2 colorcoord;
  vec3 point;
} tes_out;

vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);

// Use surface pointcloud to determine coordinates of tessellated points.
void main()
{
  vec2 colorcoord_a = mix(tes_in[0].colorcoord, tes_in[1].colorcoord, gl_TessCoord.x);
  vec2 colorcoord_b = mix(tes_in[3].colorcoord, tes_in[2].colorcoord, gl_TessCoord.x);
  tes_out.colorcoord = mix(colorcoord_a, colorcoord_b, gl_TessCoord.y);
  vec2 heightcoord_a = mix(tes_in[0].heightcoord, tes_in[1].heightcoord, gl_TessCoord.x);
  vec2 heightcoord_b = mix(tes_in[3].heightcoord, tes_in[2].heightcoord, gl_TessCoord.x);
  vec2 heightcoord = mix(heightcoord_a, heightcoord_b, gl_TessCoord.y);
  vec3 vector = texture(surface, heightcoord).xyz;
  tes_out.point = tile_center + vector;
  vec4 transformed_point = recenter_and_transform * vec4(vector, 1);
  gl_Position = shrink_shadow_index(transformed_point, shadow_size, shadow_size);
}" )

(def fragment-shadow-planet
"#version 410 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;
void main()
{
}")

(def fragment-planet-clouds
"#version 410 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;
out vec4 fragColor;
vec4 cloud_planet(vec3 point);
void main()
{
  fragColor = cloud_planet(fs_in.point);
}")

(def fragment-atmosphere-clouds
"#version 410 core
in VS_OUT
{
  vec3 direction;
} fs_in;
out vec4 fragColor;
vec4 cloud_atmosphere(vec3 fs_in_direction);
void main()
{
  fragColor = cloud_atmosphere(fs_in.direction);
}")

(def fov (to-radians 60.0))
(def radius 6378000.0)
(def tilesize 33)
(def color-tilesize 129)
(def max-height 35000.0)
(def threshold (atom 18.2))
(def anisotropic (atom 0.25))
(def shadow-bias (exp -6.0))
(def cloud-bottom 2000)
(def cloud-top 5000)
(def cloud-multiplier 10.0)
(def cover-multiplier 26.0)
(def cap (atom 0.005))
(def detail-scale 4000)
(def cloud-scale 100000)
(def series (take 4 (iterate #(* % 0.7) 1.0)))
(def sum-series (apply + series))
(def octaves (mapv #(/ % sum-series) series))
(def perlin-series (take 4 (iterate #(* % 0.7) 1.0)))
(def perlin-sum-series (apply + perlin-series))
(def perlin-octaves (mapv #(/ % perlin-sum-series) perlin-series))
(def mix 0.8)
(def opacity-step (atom 250.0))
(def step (atom 300.0))
(def worley-size 64)
(def shadow-size 512)
(def noise-size 64)
(def mount-everest 8000)
(def depth (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
              (sqrt (- (sqr (+ radius mount-everest)) (sqr radius)))))
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def surface-height-size 16)
(def surface-sun-elevation-size 63)
(def theta (to-radians 25))
(def r (+ radius cloud-bottom -750))
; (def position (atom (vec3 0 (* (cos theta) r) (* (sin theta) r))))
; (def position (atom (vec3 (* 1.0 r) 0 (* 0.7 r))))
(def position (atom (vec3 (+ 3.0 radius) 0 0)))
; (def orientation (atom (q/rotation (to-radians 25) (vec3 1 0 0))))
; (def orientation (atom (q/rotation (to-radians 90) (vec3 0 1 0))))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def num-steps 3)
(def num-opacity-layers 7)

(GLFW/glfwInit)

(def window (make-window "sfsim25" width height))
(GLFW/glfwShowWindow window)

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def worley-data (float-array (map #(+ (* 0.3 %1) (* 0.7 %2))
                                   (slurp-floats "data/clouds/perlin.raw")
                                   (slurp-floats "data/clouds/worley-cover.raw"))))
(def L (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data worley-data}))

(def noise-data (slurp-floats "data/bluenoise.raw"))
(def B (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data noise-data}))

(def cover (map (fn [i] {:width 512 :height 512 :data (slurp-floats (str "data/clouds/cover" i ".raw"))}) (range 6)))
(def C (make-float-cubemap :linear :clamp cover))

(def transmittance-data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data transmittance-data}))

(def scatter-data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data scatter-data}))

(def mie-data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data mie-data}))

(def surface-radiance-data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def E (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data surface-radiance-data}))

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere shaders/convert-1d-index shaders/ray-sphere
                           (opacity-cascade-lookup num-steps "average_opacity")
                           (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup"
                                                                [["sampler3D" "layers"] ["float" "depth"]])
                           opacity-lookup shaders/convert-2d-index transmittance-track phase-function cloud-overlay
                           shaders/is-above-horizon shaders/transmittance-forward shaders/height-to-index
                           shaders/interpolate-2d shaders/horizon-distance shaders/elevation-to-index shaders/limit-quot
                           ray-scatter-track shaders/ray-scatter-forward shaders/sun-elevation-to-index shaders/interpolate-4d
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d transmittance-outer ray-scatter-outer
                           ground-radiance shaders/surface-radiance-forward surface-radiance-function attenuation-outer]))

(def program-opacity
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) cloud-density shaders/remap
                           cloud-base cloud-cover cloud-noise
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile shaders/convert-1d-index
                           shaders/ray-shell shaders/ray-sphere bluenoise/sampling-offset linear-sampling
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index]))

(def opacity-indices [0 1 3 2])
(def opacity-vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0])
(def opacity-vao (make-vertex-array-object program-opacity opacity-indices opacity-vertices [:point 2]))

(def program-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet attenuation-track shaders/ray-sphere ground-radiance
                           shaders/transmittance-forward phase-function cloud-overlay shaders/remap
                           transmittance-track shaders/height-to-index shaders/horizon-distance shaders/sun-elevation-to-index
                           shaders/limit-quot shaders/sun-angle-to-index shaders/interpolate-2d shaders/interpolate-4d
                           ray-scatter-track shaders/elevation-to-index shaders/convert-2d-index shaders/ray-scatter-forward
                           shaders/make-2d-index-from-4d shaders/is-above-horizon shaders/clip-shell-intersections
                           shaders/surface-radiance-forward transmittance-outer surface-radiance-function
                           shaders/convert-1d-index (opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
                           (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup"
                                                                [["sampler3D" "layers"] ["float" "depth"]])
                           shaders/convert-3d-index overall-shadow shaders/shadow-lookup shaders/convert-shadow-index
                           (shaders/shadow-cascade-lookup num-steps "average_shadow")
                           (shaders/percentage-closer-filtering "average_shadow" "shadow_lookup"
                                                                [["sampler2DShadow" "shadow_map"]])]))
(def program-shadow-planet
  (make-program :vertex [vertex-shadow-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-shadow-planet shaders/shrink-shadow-index]
                :geometry [geometry-planet]
                :fragment [fragment-shadow-planet]))

(def program-cloud-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet-clouds cloud-planet shaders/ray-sphere shaders/ray-shell
                           shaders/clip-shell-intersections sample-cloud linear-sampling bluenoise/sampling-offset
                           phase-function cloud-density cloud-base cloud-transfer cloud-cover cloud-noise
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index (sphere-noise "perlin_octaves")
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley") shaders/remap cloud-profile
                           (shaders/lookup-3d "lookup_perlin" "perlin") (opacity-cascade-lookup num-steps "average_opacity")
                           (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup"
                                                                [["sampler3D" "layers"] ["float" "depth"]])
                           opacity-lookup shaders/convert-2d-index transmittance-outer shaders/convert-3d-index
                           shaders/transmittance-forward transmittance-track shaders/height-to-index shaders/interpolate-2d
                           shaders/is-above-horizon ray-scatter-track shaders/horizon-distance shaders/elevation-to-index
                           shaders/ray-scatter-forward shaders/limit-quot shaders/sun-elevation-to-index shaders/interpolate-4d
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d overall-shadow shaders/shadow-lookup
                           shaders/convert-shadow-index (shaders/shadow-cascade-lookup num-steps "average_shadow")
                           (shaders/percentage-closer-filtering "average_shadow" "shadow_lookup"
                                                                [["sampler2DShadow" "shadow_map"]])]))

(def program-cloud-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere-clouds cloud-atmosphere shaders/ray-sphere shaders/ray-shell
                           sample-cloud linear-sampling bluenoise/sampling-offset phase-function cloud-density cloud-base
                           cloud-transfer cloud-cover cloud-noise shaders/interpolate-float-cubemap
                           shaders/convert-cubemap-index (sphere-noise "perlin_octaves")
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley") shaders/remap
                           cloud-profile (shaders/lookup-3d "lookup_perlin" "perlin")
                           (opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
                           (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup"
                                                                [["sampler3D" "layers"] ["float" "depth"]])
                           shaders/convert-2d-index transmittance-outer
                           shaders/convert-3d-index shaders/transmittance-forward
                           transmittance-track shaders/height-to-index shaders/interpolate-2d
                           shaders/is-above-horizon ray-scatter-track shaders/horizon-distance
                           shaders/elevation-to-index shaders/ray-scatter-forward shaders/limit-quot
                           shaders/sun-elevation-to-index shaders/interpolate-4d
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d
                           overall-shadow shaders/shadow-lookup shaders/convert-shadow-index
                           (shaders/shadow-cascade-lookup num-steps "average_shadow")
                           (shaders/percentage-closer-filtering "average_shadow" "shadow_lookup"
                                                                [["sampler2DShadow" "shadow_map"]])]))

(def vertex-cube
"#version 410 core
uniform float shift_y;
uniform float shift_z;
uniform mat4 projection;
in vec3 point;
void main()
{
  gl_Position = projection * (vec4(point, 1) + vec4(0, shift_y, shift_z - 10, 0));
}")

(def fragment-cube
"#version 410 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")

(def cube-indices [0 1 2 3
                   1 5 6 2
                   5 4 7 6
                   4 0 3 7
                   3 2 6 7
                   4 5 1 0])

(def cube-vertices [-0.5 -0.5  0.5
                     0.5 -0.5  0.5
                     0.5  0.5  0.5
                    -0.5  0.5  0.5
                    -0.5 -0.5 -0.5
                     0.5 -0.5 -0.5
                     0.5  0.5 -0.5
                    -0.5  0.5 -0.5])

(def program-cube
  (make-program :vertex [vertex-cube]
                :fragment [fragment-cube]))

(def cube-vao (make-vertex-array-object program-cube cube-indices cube-vertices [:point 3]))

(use-program program-opacity)
(uniform-sampler program-opacity "worley" 0)
(uniform-sampler program-opacity "perlin" 1)
(uniform-sampler program-opacity "bluenoise" 2)
(uniform-sampler program-opacity "cover" 3)
(uniform-int program-opacity "cover_size" 512)
(uniform-int program-opacity "shadow_size" shadow-size)
(uniform-int program-opacity "noise_size" noise-size)
(uniform-float program-opacity "radius" radius)
(uniform-float program-opacity "cloud_bottom" cloud-bottom)
(uniform-float program-opacity "cloud_top" cloud-top)
(uniform-float program-opacity "detail_scale" detail-scale)
(uniform-float program-opacity "cloud_scale" cloud-scale)
(use-textures W L B C)

(use-program program-shadow-planet)
(uniform-sampler program-shadow-planet "surface" 0)
(uniform-int program-shadow-planet "high_detail" (dec tilesize))
(uniform-int program-shadow-planet "low_detail" (quot (dec tilesize) 2))
(uniform-int program-shadow-planet "shadow_size" shadow-size)

(use-program program-cloud-planet)
(uniform-sampler program-cloud-planet "surface"          0)
(uniform-sampler program-cloud-planet "transmittance"    1)
(uniform-sampler program-cloud-planet "ray_scatter"      2)
(uniform-sampler program-cloud-planet "mie_strength"     3)
(uniform-sampler program-cloud-planet "worley"           4)
(uniform-sampler program-cloud-planet "perlin"           5)
(uniform-sampler program-cloud-planet "bluenoise"        6)
(uniform-sampler program-cloud-planet "cover"            7)
(uniform-sampler program-cloud-planet "shadow_map0"      8)
(uniform-sampler program-cloud-planet "shadow_map1"      9)
(uniform-sampler program-cloud-planet "shadow_map2"     10)
(doseq [i (range num-steps)]
       (uniform-sampler program-cloud-planet (str "opacity" i) (+ i 11)))
(uniform-float program-cloud-planet "radius" radius)
(uniform-float program-cloud-planet "max_height" max-height)
(uniform-float program-cloud-planet "cloud_bottom" cloud-bottom)
(uniform-float program-cloud-planet "cloud_top" cloud-top)
(uniform-float program-cloud-planet "cloud_scale" cloud-scale)
(uniform-float program-cloud-planet "detail_scale" detail-scale)
(uniform-float program-cloud-planet "depth" depth)
(uniform-int program-cloud-planet "cover_size" 512)
(uniform-int program-cloud-planet "noise_size" noise-size)
(uniform-int program-cloud-planet "high_detail" (dec tilesize))
(uniform-int program-cloud-planet "low_detail" (quot (dec tilesize) 2))
(uniform-int program-cloud-planet "height_size" height-size)
(uniform-int program-cloud-planet "elevation_size" elevation-size)
(uniform-int program-cloud-planet "light_elevation_size" light-elevation-size)
(uniform-int program-cloud-planet "heading_size" heading-size)
(uniform-int program-cloud-planet "transmittance_height_size" transmittance-height-size)
(uniform-int program-cloud-planet "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-cloud-planet "surface_height_size" surface-height-size)
(uniform-int program-cloud-planet "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-float program-cloud-planet "albedo" 0.9)
(uniform-float program-cloud-planet "reflectivity" 0.1)
(uniform-float program-cloud-planet "specular" 1000)
(uniform-float program-cloud-planet "radius" radius)
(uniform-float program-cloud-planet "max_height" max-height)
(uniform-vector3 program-cloud-planet "water_color" (vec3 0.09 0.11 0.34))
(uniform-float program-cloud-planet "amplification" 6)
(uniform-float program-cloud-planet "opacity_cutoff" 0.05)
(uniform-int program-cloud-planet "num_opacity_layers" num-opacity-layers)
(uniform-int program-cloud-planet "shadow_size" shadow-size)

(use-program program-cloud-atmosphere)
(uniform-sampler program-cloud-atmosphere "transmittance"    0)
(uniform-sampler program-cloud-atmosphere "ray_scatter"      1)
(uniform-sampler program-cloud-atmosphere "mie_strength"     2)
(uniform-sampler program-cloud-atmosphere "worley"           3)
(uniform-sampler program-cloud-atmosphere "perlin"           4)
(uniform-sampler program-cloud-atmosphere "bluenoise"        5)
(uniform-sampler program-cloud-atmosphere "cover"            6)
(uniform-sampler program-cloud-atmosphere "shadow_map0"      7)
(uniform-sampler program-cloud-atmosphere "shadow_map1"      8)
(uniform-sampler program-cloud-atmosphere "shadow_map2"      9)
(doseq [i (range num-steps)]
       (uniform-sampler program-cloud-atmosphere (str "opacity" i) (+ i 10)))
(uniform-float program-cloud-atmosphere "radius" radius)
(uniform-float program-cloud-atmosphere "max_height" max-height)
(uniform-float program-cloud-atmosphere "cloud_bottom" cloud-bottom)
(uniform-float program-cloud-atmosphere "cloud_top" cloud-top)
(uniform-float program-cloud-atmosphere "cloud_scale" cloud-scale)
(uniform-float program-cloud-atmosphere "detail_scale" detail-scale)
(uniform-float program-cloud-atmosphere "depth" depth)
(uniform-int program-cloud-atmosphere "cover_size" 512)
(uniform-int program-cloud-atmosphere "noise_size" noise-size)
(uniform-int program-cloud-atmosphere "high_detail" (dec tilesize))
(uniform-int program-cloud-atmosphere "low_detail" (quot (dec tilesize) 2))
(uniform-int program-cloud-atmosphere "height_size" height-size)
(uniform-int program-cloud-atmosphere "elevation_size" elevation-size)
(uniform-int program-cloud-atmosphere "light_elevation_size" light-elevation-size)
(uniform-int program-cloud-atmosphere "heading_size" heading-size)
(uniform-int program-cloud-atmosphere "transmittance_height_size" transmittance-height-size)
(uniform-int program-cloud-atmosphere "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-cloud-atmosphere "surface_height_size" surface-height-size)
(uniform-int program-cloud-atmosphere "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-float program-cloud-atmosphere "albedo" 0.9)
(uniform-float program-cloud-atmosphere "reflectivity" 0.1)
(uniform-float program-cloud-atmosphere "specular" 1000)
(uniform-float program-cloud-atmosphere "radius" radius)
(uniform-float program-cloud-atmosphere "max_height" max-height)
(uniform-vector3 program-cloud-atmosphere "water_color" (vec3 0.09 0.11 0.34))
(uniform-float program-cloud-atmosphere "amplification" 6)
(uniform-float program-cloud-atmosphere "opacity_cutoff" 0.05)
(uniform-int program-cloud-atmosphere "num_opacity_layers" num-opacity-layers)
(uniform-int program-cloud-atmosphere "shadow_size" shadow-size)

(use-program program-planet)
(uniform-sampler program-planet "surface"          0)
(uniform-sampler program-planet "day"              1)
(uniform-sampler program-planet "night"            2)
(uniform-sampler program-planet "normals"          3)
(uniform-sampler program-planet "water"            4)
(uniform-sampler program-planet "transmittance"    5)
(uniform-sampler program-planet "ray_scatter"      6)
(uniform-sampler program-planet "mie_strength"     7)
(uniform-sampler program-planet "surface_radiance" 8)
(uniform-sampler program-planet "clouds"           9)
(uniform-sampler program-planet "shadow_map0"     10)
(uniform-sampler program-planet "shadow_map1"     11)
(uniform-sampler program-planet "shadow_map2"     12)
(doseq [i (range num-steps)]
       (uniform-sampler program-planet (str "opacity" i) (+ i 13)))
(uniform-int program-planet "cover_size" 512)
(uniform-int program-planet "noise_size" noise-size)
(uniform-int program-planet "high_detail" (dec tilesize))
(uniform-int program-planet "low_detail" (quot (dec tilesize) 2))
(uniform-int program-planet "height_size" height-size)
(uniform-int program-planet "elevation_size" elevation-size)
(uniform-int program-planet "light_elevation_size" light-elevation-size)
(uniform-int program-planet "heading_size" heading-size)
(uniform-int program-planet "transmittance_height_size" transmittance-height-size)
(uniform-int program-planet "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-planet "surface_height_size" surface-height-size)
(uniform-int program-planet "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-float program-planet "albedo" 0.9)
(uniform-float program-planet "dawn_start" -0.2)
(uniform-float program-planet "dawn_end" 0.0)
(uniform-float program-planet "reflectivity" 0.1)
(uniform-float program-planet "specular" 1000)
(uniform-float program-planet "radius" radius)
(uniform-float program-planet "max_height" max-height)
(uniform-vector3 program-planet "water_color" (vec3 0.09 0.11 0.34))
(uniform-float program-planet "amplification" 6)
(uniform-float program-planet "opacity_cutoff" 0.05)
(uniform-int program-planet "num_opacity_layers" num-opacity-layers)
(uniform-int program-planet "shadow_size" shadow-size)
(uniform-float program-planet "radius" radius)
(uniform-float program-planet "max_height" max-height)

(use-program program-atmosphere)
(uniform-sampler program-atmosphere "transmittance" 0)
(uniform-sampler program-atmosphere "ray_scatter" 1)
(uniform-sampler program-atmosphere "mie_strength" 2)
(uniform-sampler program-atmosphere "surface_radiance" 3)
(uniform-sampler program-atmosphere "clouds" 4)
(doseq [i (range num-steps)]
       (uniform-sampler program-atmosphere (str "opacity" i) (+ i 5)))
(uniform-int program-atmosphere "cover_size" 512)
(uniform-int program-atmosphere "noise_size" noise-size)
(uniform-int program-atmosphere "height_size" height-size)
(uniform-int program-atmosphere "elevation_size" elevation-size)
(uniform-int program-atmosphere "light_elevation_size" light-elevation-size)
(uniform-int program-atmosphere "heading_size" heading-size)
(uniform-int program-atmosphere "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-atmosphere "transmittance_height_size" transmittance-height-size)
(uniform-int program-atmosphere "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-int program-atmosphere "surface_height_size" surface-height-size)
(uniform-float program-atmosphere "albedo" 0.9)
(uniform-float program-atmosphere "reflectivity" 0.1)
(uniform-float program-atmosphere "opacity_cutoff" 0.05)
(uniform-int program-atmosphere "num_opacity_layers" num-opacity-layers)
(uniform-int program-atmosphere "shadow_size" shadow-size)
(uniform-float program-atmosphere "radius" radius)
(uniform-float program-atmosphere "max_height" max-height)
(uniform-float program-atmosphere "specular" 1000)
(uniform-float program-atmosphere "amplification" 6)

(def tree (atom []))
(def changes (atom (future {:tree {} :drop [] :load []})))

(defn background-tree-update [tree]
  (let [increase? (partial increase-level? tilesize radius width 60 10 2 @position)]
    (update-level-of-detail tree radius increase? true)))

(defn load-tile-into-opengl
  [tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program-planet indices vertices [:point 3 :heightcoord 2 :colorcoord 2])
        day-tex    (make-rgb-texture :linear :clamp (:day tile))
        night-tex  (make-rgb-texture :linear :clamp (:night tile))
        surf-tex   (make-vector-texture-2d :linear :clamp {:width tilesize :height tilesize :data (:surface tile)})
        normal-tex (make-vector-texture-2d :linear :clamp (:normals tile))
        water-tex  (make-ubyte-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:water tile)})]
    (assoc (dissoc tile :day :night :surface :normals :water)
           :vao vao :day-tex day-tex :night-tex night-tex :surf-tex surf-tex :normal-tex normal-tex :water-tex water-tex)))

(defn load-tiles-into-opengl
  [tree paths]
  (quadtree-update tree paths load-tile-into-opengl))

(defn unload-tile-from-opengl
  [tile]
  (destroy-texture (:day-tex tile))
  (destroy-texture (:night-tex tile))
  (destroy-texture (:surf-tex tile))
  (destroy-texture (:normal-tex tile))
  (destroy-texture (:water-tex tile))
  (destroy-vertex-array-object (:vao tile)))

(defn unload-tiles-from-opengl
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))
(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)


(def shift-y (atom 0.0))
(def shift-z (atom 0.0))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [t0 (atom (System/currentTimeMillis))
        n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (not (GLFW/glfwWindowShouldClose window))
           (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
           (when (realized? @changes)
             (let [data @@changes]
               (unload-tiles-from-opengl (:drop data))
               (reset! tree (load-tiles-into-opengl (:tree data) (:load data)))
               (reset! changes (future (background-tree-update @tree)))))
           (let [t1 (System/currentTimeMillis)
                 dt (- t1 @t0)
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 50 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -50 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))
                 ta (if (@keystates GLFW/GLFW_KEY_E) 0.0001 (if (@keystates GLFW/GLFW_KEY_D) -0.0001 0))
                 tm (if (@keystates GLFW/GLFW_KEY_R) 0.001 (if (@keystates GLFW/GLFW_KEY_F) -0.001 0))
                 tc (if (@keystates GLFW/GLFW_KEY_T) 0.00001 (if (@keystates GLFW/GLFW_KEY_G) -0.00001 0))
                 ts (if (@keystates GLFW/GLFW_KEY_Y) 0.05 (if (@keystates GLFW/GLFW_KEY_H) -0.05 0))
                 tg (if (@keystates GLFW/GLFW_KEY_U) 0.001 (if (@keystates GLFW/GLFW_KEY_J) -0.001 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! threshold + (* dt tr))
             (swap! opacity-step + (* dt to))
             (swap! anisotropic + (* dt ta))
             (swap! shift-y + (* 0.5 dt tm))
             (swap! shift-z + (* 0.5 dt tg))
             (swap! cap + (* dt tc))
             (swap! step + (* dt ts))
             (GL11/glFinish)
             (let [norm-pos   (mag @position)
                   dist       (- norm-pos radius cloud-top)
                   z-near     (max 1.0 (* 0.4 dist))
                   z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                 (sqrt (- (sqr norm-pos) (sqr radius))))
                   indices    [0 1 3 2]
                   vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
                   vao        (make-vertex-array-object program-atmosphere indices vertices [:point 3])
                   light-dir  (vec3 (cos @light) (sin @light) 0)
                   projection (projection-matrix (aget w 0) (aget h 0) z-near (+ z-far 1) fov)
                   lod-offset (/ (log (/ (tan (/ fov 2)) (/ (aget w 0) 2) (/ detail-scale worley-size))) (log 2))
                   transform  (transformation-matrix (quaternion->matrix @orientation) @position)
                   matrix-cas (shadow-matrix-cascade projection transform light-dir depth mix z-near z-far num-steps)
                   splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
                   scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
                   cos-light  (/ (dot light-dir @position) (mag @position))
                   sin-light  (sqrt (- 1 (sqr cos-light)))
                   opac-step  (* (+ cos-light (* 10 sin-light)) @opacity-step)
                   opacities  (opacity-cascade shadow-size num-opacity-layers matrix-cas (/ detail-scale worley-size) program-opacity
                                               (uniform-vector3 program-opacity "light_direction" light-dir)
                                               (uniform-float program-opacity "cloud_multiplier" cloud-multiplier)
                                               (uniform-float program-opacity "cover_multiplier" cover-multiplier)
                                               (uniform-float program-opacity "cap" @cap)
                                               (uniform-float program-opacity "cloud_threshold" @threshold)
                                               (uniform-float program-opacity "scatter_amount" scatter-am)
                                               (uniform-float program-opacity "opacity_step" opac-step)
                                               (uniform-float program-opacity "cloud_max_step" (* 0.5 opac-step))
                                               (render-quads opacity-vao))
                   shadows    (shadow-cascade shadow-size matrix-cas program-shadow-planet
                                              (fn [transform]
                                                  (render-tree program-shadow-planet @tree transform [:surf-tex])))
                   w2         (quot (aget w 0) 2)
                   h2         (quot (aget h 0) 2)
                   clouds     (texture-render-color-depth
                                w2 h2 true
                                (clear (vec3 0 0 0) 0)
                                ; Render clouds under the horizon
                                (use-program program-cloud-planet)
                                (uniform-float program-cloud-planet "cloud_step" @step)
                                (uniform-float program-cloud-planet "cloud_multiplier" cloud-multiplier)
                                (uniform-float program-cloud-planet "cover_multiplier" cover-multiplier)
                                (uniform-float program-cloud-planet "cap" @cap)
                                (uniform-float program-cloud-planet "cloud_threshold" @threshold)
                                (uniform-float program-cloud-planet "lod_offset" lod-offset)
                                (uniform-float program-cloud-planet "anisotropic" @anisotropic)
                                (uniform-matrix4 program-cloud-planet "projection" projection)
                                (uniform-vector3 program-cloud-planet "origin" @position)
                                (uniform-vector3 program-cloud-planet "light_direction" light-dir)
                                (uniform-float program-cloud-planet "opacity_step" opac-step)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-cloud-planet (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-cloud-planet (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-cloud-planet (str "depth" idx) (:depth item)))
                                (apply use-textures nil T S M W L B C (concat shadows opacities))
                                (render-tree program-cloud-planet @tree (inverse transform) [:surf-tex])
                                ; Render clouds above the horizon
                                (use-program program-cloud-atmosphere)
                                (uniform-float program-cloud-atmosphere "cloud_step" @step)
                                (uniform-float program-cloud-atmosphere "cloud_multiplier" cloud-multiplier)
                                (uniform-float program-cloud-atmosphere "cover_multiplier" cover-multiplier)
                                (uniform-float program-cloud-atmosphere "cap" @cap)
                                (uniform-float program-cloud-atmosphere "cloud_threshold" @threshold)
                                (uniform-float program-cloud-atmosphere "lod_offset" lod-offset)
                                (uniform-float program-cloud-atmosphere "anisotropic" @anisotropic)
                                (uniform-matrix4 program-cloud-atmosphere "projection" projection)
                                (uniform-vector3 program-cloud-atmosphere "origin" @position)
                                (uniform-matrix4 program-cloud-atmosphere "transform" transform)
                                (uniform-matrix4 program-cloud-atmosphere "inverse_transform" (inverse transform))
                                (uniform-vector3 program-cloud-atmosphere "light_direction" light-dir)
                                (uniform-float program-cloud-atmosphere "opacity_step" opac-step)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-cloud-atmosphere (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-cloud-atmosphere (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-cloud-atmosphere (str "depth" idx) (:depth item)))
                                (apply use-textures T S M W L B C (concat shadows opacities))
                                (render-quads vao))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0)
                                ; Render cube
                                (use-program program-cube)
                                (uniform-float program-cube "shift_y" @shift-y)
                                (uniform-float program-cube "shift_z" @shift-z)
                                (uniform-matrix4 program-cube "projection" projection)
                                (render-quads cube-vao)
                                ; Render planet with cloud overlay
                                (use-program program-planet)
                                (uniform-matrix4 program-planet "projection" projection)
                                (uniform-vector3 program-planet "origin" @position)
                                (uniform-vector3 program-planet "light_direction" light-dir)
                                (uniform-float program-planet "opacity_step" opac-step)
                                (uniform-int program-planet "window_width" (aget w 0))
                                (uniform-int program-planet "window_height" (aget h 0))
                                (uniform-float program-planet "shadow_bias" shadow-bias)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-planet (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-planet (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-planet (str "depth" idx) (:depth item)))
                                (apply use-textures nil nil nil nil nil T S M E clouds (concat shadows opacities))
                                (render-tree program-planet @tree (inverse transform)
                                             [:surf-tex :day-tex :night-tex :normal-tex :water-tex])
                                ; Render atmosphere with cloud overlay
                                (use-program program-atmosphere)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-atmosphere (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-atmosphere (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-atmosphere (str "depth" idx) (:depth item)))
                                (uniform-matrix4 program-atmosphere "projection" projection)
                                (uniform-matrix4 program-atmosphere "transform" transform)
                                (uniform-vector3 program-atmosphere "origin" @position)
                                (uniform-matrix4 program-atmosphere "inverse_transform" (inverse transform))
                                (uniform-float program-atmosphere "opacity_step" opac-step)
                                (uniform-int program-atmosphere "window_width" (aget w 0))
                                (uniform-int program-atmosphere "window_height" (aget h 0))
                                (uniform-vector3 program-atmosphere "light_direction" light-dir)
                                (apply use-textures T S M E clouds opacities)
                                (render-quads vao))
               (destroy-texture clouds)
               (doseq [layer opacities]
                      (destroy-texture layer))
               (doseq [shadow shadows]
                      (destroy-texture shadow))
               (destroy-vertex-array-object vao))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, aniso (e/d) %.3f, dy (r/f) %.1f, dz (u/j) %.1f, cap (t/g) %.3f, step (y/h) %.0f, dt %.3f"
                              @threshold @opacity-step @anisotropic @shift-y @shift-z @cap @step (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (destroy-texture E)
  (destroy-texture M)
  (destroy-texture S)
  (destroy-texture T)
  (destroy-texture C)
  (destroy-texture B)
  (destroy-texture W)
  (destroy-vertex-array-object cube-vao)
  (destroy-vertex-array-object opacity-vao)
  (destroy-program program-cube)
  (destroy-program program-cloud-atmosphere)
  (destroy-program program-cloud-planet)
  (destroy-program program-shadow-planet)
  (destroy-program program-planet)
  (destroy-program program-opacity)
  (destroy-program program-atmosphere)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
