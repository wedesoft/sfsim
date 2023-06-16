(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin tan PI sqrt log)]
            [fastmath.matrix :refer (inverse)]
            [fastmath.vector :refer (vec3 add mult mag)]
            [sfsim25.render :refer :all]
            [sfsim25.atmosphere :refer :all]
            [sfsim25.planet :refer :all]
            [sfsim25.quadtree :refer :all]
            [sfsim25.clouds :refer :all]
            [sfsim25.bluenoise :as bluenoise]
            [sfsim25.matrix :refer :all]
            [sfsim25.quaternion :as q]
            [sfsim25.util :refer :all]
            [sfsim25.shaders :as shaders])
  (:import [org.lwjgl.opengl GL]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)

(def width 960)
(def height 540)

(def fragment-atmosphere-enhanced
"#version 410 core
uniform float stepsize;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float dense_height;
uniform float max_height;
uniform float lod_offset;
uniform float anisotropic;
uniform float specular;
uniform float amplification;
uniform vec3 light_direction;
in VS_OUT
{
  vec3 origin;
  vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
float cloud_density(vec3 point, float lod);
float phase(float g, float mu);
float opacity_cascade_lookup(vec4 point);
float sampling_offset();
vec3 transmittance_track(vec3 p, vec3 q);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
vec3 ray_scatter_outer(vec3 light_direction, vec3 point, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
int number_of_samples(float a, float b, float max_step);
float sample_point(float a, float idx, float step_size);
float step_size(float a, float b, int num_samples);
float lod_at_distance(float dist, float lod_offset);

bool planet_shadow(vec3 point, vec3 light_direction)  // To be replaced with shadow map
{
  if (dot(point, light_direction) < 0) {
    vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
    return planet_intersection.y > 0;
  } else
    return false;
}

float cloud_shadow(vec3 point, vec3 light_direction)
{
  if (planet_shadow(point, light_direction))
    return 0.0;
  else
    return opacity_cascade_lookup(vec4(point, 1));
}

vec4 cloud_transfer(vec3 origin, vec3 point, vec3 direction, vec3 light_direction, vec2 atmosphere, float step, vec4 cloud_scatter, float lod)
{
  float density = cloud_density(point, lod);
  if (density > 0) {
    float t = exp(-density * step);
    vec3 intensity = cloud_shadow(point, light_direction) * transmittance_outer(point, light_direction);
    vec3 scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic) * intensity;
    vec3 in_scatter = ray_scatter_track(light_direction, origin + atmosphere.x * direction, point) * amplification;
    vec3 transmittance = transmittance_track(origin + atmosphere.x * direction, point);
    cloud_scatter.rgb = cloud_scatter.rgb + cloud_scatter.a * (1 - t) * scatter_amount * transmittance + cloud_scatter.a * (1 - t) * in_scatter;
    cloud_scatter.a *= t;
  };
  return cloud_scatter;
}

vec4 sample_cloud(vec3 origin, vec3 direction, vec3 light_direction, vec2 atmosphere, vec4 cloud_scatter)
{
  int count = number_of_samples(atmosphere.x, atmosphere.x + atmosphere.y, stepsize);
  float step = step_size(atmosphere.x, atmosphere.x + atmosphere.y, count);
  float offset = sampling_offset();
  for (int i=0; i<count; i++) {
    float l = sample_point(atmosphere.x, i + offset, step);
    vec3 point = origin + l * direction;
    float r = length(point);
    if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
      float lod = lod_at_distance(l, lod_offset);
      cloud_scatter = cloud_transfer(origin, point, direction, light_direction, atmosphere, step, cloud_scatter, lod);
    }
    if (cloud_scatter.a <= 0.01)
      break;
  };
  return cloud_scatter;
}

void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, fs_in.origin, direction);
  vec3 background;
  vec3 ray_scatter;
  vec3 transmittance;
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  if (atmosphere.y > 0) {
    transmittance = transmittance_outer(fs_in.origin + atmosphere.x * direction, direction);
    float glare = pow(max(0, dot(direction, light_direction)), specular);
    background = vec3(glare, glare, glare);
    ray_scatter = ray_scatter_outer(light_direction, fs_in.origin + atmosphere.x * direction, direction) * amplification;
    cloud_scatter = sample_cloud(fs_in.origin, direction, light_direction, atmosphere, cloud_scatter);
  } else {
    float glare = pow(max(0, dot(direction, light_direction)), specular);
    background = vec3(glare, glare, glare);
    transmittance = vec3(1.0, 1.0, 1.0);
    ray_scatter = vec3(0.0, 0.0, 0.0);
  };
  fragColor = (background * transmittance + ray_scatter) * cloud_scatter.a + cloud_scatter.rgb;
}")

(def fragment-planet-enhanced
"#version 410 core

uniform sampler2D colors;
uniform sampler2D normals;
uniform sampler2D water;
uniform float specular;
uniform float radius;
uniform float max_height;
uniform float amplification;
uniform vec3 water_color;
uniform vec3 position;
uniform vec3 light_direction;
uniform vec3 origin;
uniform float anisotropic;
uniform float stepsize;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float lod_offset;
uniform float depth;

in GEO_OUT
{
  vec2 colorcoord;
  vec2 heightcoord;
  vec3 point;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
vec3 transmittance_track(vec3 p, vec3 q);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
float opacity_cascade_lookup(vec4 point);
float cloud_density(vec3 point, float lod);
float phase(float g, float mu);
int number_of_samples(float a, float b, float max_step);
float sample_point(float a, float idx, float step_size);
float step_size(float a, float b, int num_samples);
float sampling_offset();
float lod_at_distance(float dist, float lod_offset);

bool planet_shadow(vec3 point, vec3 light_direction)  // To be replaced with shadow map
{
  if (dot(point, light_direction) < 0) {
    vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
    return planet_intersection.y > 0;
  } else
    return false;
}

float cloud_shadow(vec3 point, vec3 light_direction)
{
  if (planet_shadow(point, light_direction))
    return 0.0;
  else
    return opacity_cascade_lookup(vec4(point, 1));
}

vec4 cloud_transfer(vec3 origin, vec3 point, vec3 direction, vec3 light_direction, vec2 atmosphere, float step, vec4 cloud_scatter, float lod)
{
  float density = cloud_density(point, lod);
  if (density > 0) {
    float t = exp(-density * step);
    vec3 intensity = cloud_shadow(point, light_direction) * transmittance_outer(point, light_direction);
    vec3 scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic) * intensity;
    vec3 in_scatter = ray_scatter_track(light_direction, origin + atmosphere.x * direction, point) * amplification;
    vec3 transmittance = transmittance_track(origin + atmosphere.x * direction, point);
    cloud_scatter.rgb = cloud_scatter.rgb + cloud_scatter.a * (1 - t) * scatter_amount * transmittance + cloud_scatter.a * (1 - t) * in_scatter;
    cloud_scatter.a *= t;
  };
  return cloud_scatter;
}

vec4 sample_cloud(vec3 origin, vec3 direction, vec3 light_direction, vec2 atmosphere, vec4 cloud_scatter)
{
  int count = number_of_samples(atmosphere.x, atmosphere.x + atmosphere.y, stepsize);
  float step = step_size(atmosphere.x, atmosphere.x + atmosphere.y, count);
  float offset = sampling_offset();
  for (int i=0; i<count; i++) {
    float l = sample_point(atmosphere.x, i + offset, step);
    vec3 point = origin + l * direction;
    float r = length(point);
    if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
      float lod = lod_at_distance(l, lod_offset);
      cloud_scatter = cloud_transfer(origin, point, direction, light_direction, atmosphere, step, cloud_scatter, lod);
    }
    if (cloud_scatter.a <= 0.01)
      break;
  };
  return cloud_scatter;
}

void main()
{
  vec3 normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 direction = normalize(fs_in.point - position);
  vec3 land_color = texture(colors, fs_in.colorcoord).rgb;
  float wet = texture(water, fs_in.colorcoord).r;
  float cos_incidence = dot(light_direction, normal);
  float highlight;
  if (cos_incidence > 0) {
    highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
  } else {
    cos_incidence = 0.0;
    highlight = 0.0;
  };
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, position, direction);
  atmosphere.y = min(distance(position, fs_in.point), depth) - atmosphere.x;
  vec3 ground = ground_radiance(fs_in.point, light_direction, wet, cos_incidence, highlight, land_color, water_color) * 0.7;
  vec3 transmittance = transmittance_track(position + atmosphere.x * direction, fs_in.point);
  vec3 intensity = cloud_shadow(fs_in.point, light_direction) * transmittance_outer(fs_in.point, light_direction);
  vec3 background = ground * intensity * amplification;
  vec3 ray_scatter = ray_scatter_track(light_direction, position + atmosphere.x * direction, fs_in.point) * amplification;
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  cloud_scatter = sample_cloud(position, direction, light_direction, atmosphere, cloud_scatter);
  fragColor = (background * transmittance + ray_scatter) * cloud_scatter.a + cloud_scatter.rgb;
}")

(def fov (to-radians 60.0))
(def radius 6378000.0)
(def tilesize 33)
(def color-tilesize 129)
(def max-height 35000.0)
(def dense-height 6000.0)
(def threshold (atom 29.0))
(def anisotropic (atom 0.2))
(def cloud-bottom 1500)
(def cloud-top 6000)
(def cloud-multiplier (atom 18.0))
(def cover-multiplier (atom 38.0))
(def cap (atom 0.010))
(def detail-scale 20000)
(def cloud-scale 800000)
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def octaves (mapv #(/ % sum-series) series))
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def perlin-octaves (mapv #(/ % sum-series) series))
(def z-near 10.0)
(def z-far (* radius 2))
(def mix 0.5)
(def opacity-step (atom 1000.0))
(def step (atom 800.0))
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
(def position (atom (vec3 0 (* (cos theta) r) (* (sin theta) r))))
(def orientation (atom (q/rotation (to-radians 25) (vec3 1 0 0))))
(def light (atom (* 0.25 PI)))
(def num-steps 2)
(def num-opacity-layers 7)

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow width height "scratch" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def data (float-array (map #(+ (* 0.3 %1) (* 0.7 %2))
                            (slurp-floats "data/clouds/perlin.raw")
                            (slurp-floats "data/clouds/worley-cover.raw"))))
(def L (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def data (slurp-floats "data/bluenoise.raw"))
(def B (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data data}))

(def cover (map (fn [i] {:width 512 :height 512 :data (slurp-floats (str "data/clouds/cover" i ".raw"))}) (range 6)))
(def C (make-float-cubemap :linear :clamp cover))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def E (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data data}))

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere-enhanced cloud-density shaders/remap
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           cloud-base cloud-cover cloud-noise
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile phase-function
                           shaders/convert-1d-index shaders/ray-sphere (opacity-cascade-lookup num-steps)
                           opacity-lookup shaders/convert-2d-index shaders/convert-3d-index bluenoise/sampling-offset
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index transmittance-track
                           shaders/is-above-horizon shaders/transmittance-forward shaders/height-to-index
                           shaders/interpolate-2d shaders/horizon-distance shaders/elevation-to-index shaders/limit-quot
                           ray-scatter-track shaders/ray-scatter-forward shaders/sun-elevation-to-index shaders/interpolate-4d
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d transmittance-outer ray-scatter-outer
                           ground-radiance shaders/surface-radiance-forward surface-radiance-function
                           linear-sampling]))

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

(defn opacity-cascade [matrix-cascade light-direction scatter-amount]
  (mapv
    (fn [{:keys [shadow-ndc-matrix depth scale]}]
        (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
              opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)
              detail          (/ (log (/ (/ scale shadow-size) (/ detail-scale worley-size))) (log 2))]
          (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                              (use-program program-opacity)
                              (uniform-sampler program-opacity "worley" 0)
                              (uniform-sampler program-opacity "perlin" 1)
                              (uniform-sampler program-opacity "bluenoise" 2)
                              (uniform-sampler program-opacity "cover" 3)
                              (uniform-int program-opacity "cover_size" 512)
                              (uniform-float program-opacity "level_of_detail" detail)
                              (uniform-int program-opacity "shadow_size" shadow-size)
                              (uniform-int program-opacity "noise_size" noise-size)
                              (uniform-float program-opacity "radius" radius)
                              (uniform-float program-opacity "cloud_bottom" cloud-bottom)
                              (uniform-float program-opacity "cloud_top" cloud-top)
                              (uniform-float program-opacity "detail_scale" detail-scale)
                              (uniform-float program-opacity "cloud_scale" cloud-scale)
                              (uniform-matrix4 program-opacity "ndc_to_shadow" (inverse shadow-ndc-matrix))
                              (uniform-vector3 program-opacity "light_direction" light-direction)
                              (uniform-float program-opacity "cloud_multiplier" @cloud-multiplier)
                              (uniform-float program-opacity "cover_multiplier" @cover-multiplier)
                              (uniform-float program-opacity "cap" @cap)
                              (uniform-float program-opacity "cloud_threshold" @threshold)
                              (uniform-float program-opacity "dense_height" dense-height)
                              (uniform-float program-opacity "scatter_amount" scatter-amount)
                              (uniform-float program-opacity "depth" depth)
                              (uniform-float program-opacity "opacity_step" @opacity-step)
                              (uniform-float program-opacity "cloud_max_step" (* 0.5 @opacity-step))
                              (use-textures W L B C)
                              (render-quads opacity-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))

(def program-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet-enhanced attenuation-track shaders/ray-sphere ground-radiance
                           shaders/transmittance-forward
                           transmittance-track shaders/height-to-index shaders/horizon-distance shaders/sun-elevation-to-index
                           shaders/limit-quot shaders/sun-angle-to-index shaders/interpolate-2d shaders/interpolate-4d
                           ray-scatter-track shaders/elevation-to-index shaders/convert-2d-index shaders/ray-scatter-forward
                           shaders/make-2d-index-from-4d shaders/is-above-horizon shaders/ray-shell
                           shaders/clip-shell-intersections phase-function shaders/surface-radiance-forward
                           transmittance-outer surface-radiance-function shaders/convert-1d-index shaders/remap
                           (opacity-cascade-lookup num-steps) opacity-lookup cloud-density shaders/convert-3d-index
                           cloud-base linear-sampling cloud-cover cloud-noise bluenoise/sampling-offset
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index cloud-profile
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves") (shaders/lookup-3d "lookup_perlin" "perlin")]))

(use-program program-planet)
(uniform-sampler program-planet "transmittance"    0)
(uniform-sampler program-planet "ray_scatter"      1)
(uniform-sampler program-planet "mie_strength"     2)
(uniform-sampler program-planet "surface_radiance" 3)
(uniform-sampler program-planet "worley"           4)
(uniform-sampler program-planet "perlin"           5)
(uniform-sampler program-planet "bluenoise"        6)
(uniform-sampler program-planet "cover"            7)
(uniform-sampler program-planet "heightfield"      8)
(uniform-sampler program-planet "colors"           9)
(uniform-sampler program-planet "normals"         10)
(uniform-sampler program-planet "water"           11)
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
(uniform-float program-planet "reflectivity" 0.1)
(uniform-float program-planet "specular" 1000)
(uniform-float program-planet "radius" radius)
(uniform-float program-planet "max_height" max-height)
(uniform-vector3 program-planet "water_color" (vec3 0.09 0.11 0.34))
(uniform-float program-planet "amplification" 6)

(def tree (atom []))
(def changes (atom (future {:tree {} :drop [] :load []})))

(defn background-tree-update [tree]
  (let [increase? (partial increase-level? tilesize radius width 60 10 5 @position)]
    (update-level-of-detail tree increase? true)))

(defn load-tile-into-opengl
  [tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program-planet indices vertices [:point 3 :heightcoord 2 :colorcoord 2])
        color-tex  (make-rgb-texture :linear :clamp (:colors tile))
        height-tex (make-float-texture-2d :linear :clamp {:width tilesize :height tilesize :data (:scales tile)})
        normal-tex (make-vector-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:normals tile)})
        water-tex  (make-ubyte-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:water tile)})]
    (assoc (dissoc tile :colors :scales :normals :water)
           :vao vao :color-tex color-tex :height-tex height-tex :normal-tex normal-tex :water-tex water-tex)))

(defn load-tiles-into-opengl
  [tree paths]
  (quadtree-update tree paths load-tile-into-opengl))

(defn unload-tile-from-opengl
  [tile]
  (destroy-texture (:color-tex tile))
  (destroy-texture (:height-tex tile))
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
           (if (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (if (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(defn render-tile
  [tile opacity-maps]
  (let [neighbours (bit-or (if (:sfsim25.quadtree/up    tile) 1 0)
                           (if (:sfsim25.quadtree/left  tile) 2 0)
                           (if (:sfsim25.quadtree/down  tile) 4 0)
                           (if (:sfsim25.quadtree/right tile) 8 0))]
    (uniform-int program-planet "neighbours" neighbours)
    (apply use-textures T S M E W L B C (:height-tex tile) (:color-tex tile) (:normal-tex tile) (:water-tex tile) opacity-maps)
    (render-patches (:vao tile))))

(defn render-tree
  [node opacity-maps]
  (if node
    (if (is-leaf? node)
      (if-not (empty? node) (render-tile node opacity-maps))
      (doseq [selector [:0 :1 :2 :3 :4 :5]]
        (render-tree (selector node) opacity-maps)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [t0 (atom (System/currentTimeMillis))
        n  (atom 0)]
    (while (not (GLFW/glfwWindowShouldClose window))
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
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 100 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -100 10))
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
             (swap! cloud-multiplier + (* dt tm))
             (swap! cover-multiplier + (* dt tg))
             (swap! cap + (* dt tc))
             (swap! step + (* dt ts))
             (let [norm-pos   (mag @position)
                   dist       (- norm-pos radius cloud-top)
                   z-near     (max 10.0 (* 0.4 dist))
                   z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                 (sqrt (- (sqr norm-pos) (sqr radius))))
                   indices    [0 1 3 2]
                   vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
                   vao        (make-vertex-array-object program-atmosphere indices vertices [:point 3])
                   light-dir  (vec3 0 (cos @light) (sin @light))
                   projection (projection-matrix width height z-near (+ z-far 1) fov)
                   lod-offset (/ (log (/ (tan (/ fov 2)) (/ width 2) (/ detail-scale worley-size))) (log 2))
                   transform  (transformation-matrix (quaternion->matrix @orientation) @position)
                   matrix-cas (shadow-matrix-cascade projection transform light-dir depth mix z-near z-far num-steps)
                   splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
                   scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
                   tex-cas    (opacity-cascade matrix-cas light-dir scatter-am)]
               (onscreen-render window
                                (clear (vec3 0 1 0))
                                ; Render planet
                                (use-program program-planet)
                                (uniform-matrix4 program-planet "projection" projection)
                                (uniform-matrix4 program-planet "inverse_transform" (inverse transform))
                                (uniform-matrix4 program-planet "transform" transform); TODO: required?
                                (uniform-vector3 program-planet "position" @position)
                                (uniform-vector3 program-planet "light_direction" light-dir)
                                (uniform-float program-planet "stepsize" @step)
                                (uniform-int program-planet "num_opacity_layers" num-opacity-layers)
                                (uniform-int program-planet "shadow_size" shadow-size)
                                (uniform-float program-planet "radius" radius)
                                (uniform-float program-planet "max_height" max-height)
                                (uniform-float program-planet "cloud_bottom" cloud-bottom)
                                (uniform-float program-planet "cloud_top" cloud-top)
                                (uniform-float program-planet "cloud_multiplier" @cloud-multiplier)
                                (uniform-float program-planet "cover_multiplier" @cover-multiplier)
                                (uniform-float program-planet "cap" @cap)
                                (uniform-float program-planet "opacity_step" @opacity-step)
                                (uniform-float program-planet "cloud_threshold" @threshold)
                                (uniform-float program-planet "detail_scale" detail-scale)
                                (uniform-float program-planet "cloud_scale" cloud-scale)
                                (uniform-float program-planet "lod_offset" lod-offset)
                                (uniform-float program-planet "dense_height" dense-height)
                                (uniform-float program-planet "anisotropic" @anisotropic)
                                (uniform-float program-planet "depth" depth)
                                (uniform-vector3 program-planet "light_direction" light-dir)
                                (doseq [i (range num-steps)]
                                       (uniform-sampler program-planet (str "offset" i) (+ (* 2 i) 12))
                                       (uniform-sampler program-planet (str "opacity" i) (+ (* 2 i) 13)))
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-planet (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-planet (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-planet (str "depth" idx) (:depth item)))
                                (render-tree @tree (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cas))
                                ; Render atmosphere and clouds
                                (use-program program-atmosphere)
                                (uniform-sampler program-atmosphere "worley" 0)
                                (uniform-sampler program-atmosphere "perlin" 1)
                                (uniform-sampler program-atmosphere "bluenoise" 2)
                                (uniform-sampler program-atmosphere "cover" 3)
                                (uniform-sampler program-atmosphere "transmittance" 4)
                                (uniform-sampler program-atmosphere "ray_scatter" 5)
                                (uniform-sampler program-atmosphere "mie_strength" 6)
                                (uniform-sampler program-atmosphere "surface_radiance" 7)
                                (doseq [i (range num-steps)]
                                       (uniform-sampler program-atmosphere (str "offset" i) (+ (* 2 i) 8))
                                       (uniform-sampler program-atmosphere (str "opacity" i) (+ (* 2 i) 9)))
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-atmosphere (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-atmosphere (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-atmosphere (str "depth" idx) (:depth item)))
                                (uniform-int program-atmosphere "cover_size" 512)
                                (uniform-matrix4 program-atmosphere "projection" projection)
                                (uniform-matrix4 program-atmosphere "transform" transform)
                                (uniform-matrix4 program-atmosphere "inverse_transform" (inverse transform))
                                (uniform-float program-atmosphere "z_near" (+ 1 z-near))
                                (uniform-float program-atmosphere "z_far" z-far)
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
                                (uniform-float program-atmosphere "stepsize" @step)
                                (uniform-int program-atmosphere "num_opacity_layers" num-opacity-layers)
                                (uniform-int program-atmosphere "shadow_size" shadow-size)
                                (uniform-float program-atmosphere "radius" radius)
                                (uniform-float program-atmosphere "max_height" max-height)
                                (uniform-float program-atmosphere "cloud_bottom" cloud-bottom)
                                (uniform-float program-atmosphere "cloud_top" cloud-top)
                                (uniform-float program-atmosphere "cloud_multiplier" @cloud-multiplier)
                                (uniform-float program-atmosphere "cover_multiplier" @cover-multiplier)
                                (uniform-float program-atmosphere "cap" @cap)
                                (uniform-float program-atmosphere "opacity_step" @opacity-step)
                                (uniform-float program-atmosphere "cloud_threshold" @threshold)
                                (uniform-float program-atmosphere "detail_scale" detail-scale)
                                (uniform-float program-atmosphere "cloud_scale" cloud-scale)
                                (uniform-float program-atmosphere "lod_offset" lod-offset)
                                (uniform-float program-atmosphere "dense_height" dense-height)
                                (uniform-float program-atmosphere "anisotropic" @anisotropic)
                                (uniform-float program-atmosphere "specular" 1000)
                                (uniform-float program-atmosphere "amplification" 6)
                                (uniform-vector3 program-atmosphere "light_direction" light-dir)
                                (apply use-textures W L B C T S M E (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cas))
                                (render-quads vao))
               (doseq [{:keys [offset layer]} tex-cas]
                      (destroy-texture offset)
                      (destroy-texture layer))
               (destroy-vertex-array-object vao))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, aniso (e/d) %.3f, mult (r/f) %.1f, cov (u/j) %.1f, cap (t/g) %.3f, step (y/h) %.0f, dt %.3f"
                              @threshold @opacity-step @anisotropic @cloud-multiplier @cover-multiplier @cap @step (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  @@changes
  ; TODO: unload all planet tiles (vaos and textures)
  (destroy-texture E)
  (destroy-texture M)
  (destroy-texture S)
  (destroy-texture T)
  (destroy-texture C)
  (destroy-texture B)
  (destroy-texture W)
  (destroy-vertex-array-object opacity-vao)
  (destroy-program program-planet)
  (destroy-program program-opacity)
  (destroy-program program-planet)
  (destroy-program program-atmosphere)
  (GLFW/glfwDestroyWindow window)
  (GLFW/glfwTerminate)
  (set! *unchecked-math* false)
  (System/exit 0))
