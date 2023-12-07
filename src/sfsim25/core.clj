(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin tan sqrt log exp)]
            [fastmath.matrix :refer (inverse)]
            [fastmath.vector :refer (vec3 add mult mag dot)]
            [sfsim25.render :refer (make-window destroy-window clear destroy-program destroy-texture destroy-vertex-array-object
                                    generate-mipmap make-float-cubemap make-float-texture-2d make-float-texture-3d
                                    make-program make-rgb-texture make-ubyte-texture-2d make-vector-texture-2d
                                    make-vertex-array-object onscreen-render render-quads texture-render-color-depth
                                    uniform-float uniform-int uniform-matrix4 uniform-sampler uniform-vector3 use-program
                                    use-textures shadow-cascade)]
            [sfsim25.atmosphere :refer (phase vertex-atmosphere fragment-atmosphere)
                                :as atmosphere]
            [sfsim25.planet :refer (geometry-planet make-cube-map-tile-vertices tess-control-planet tess-evaluation-planet
                                    vertex-planet render-tree fragment-planet)
                            :as planet]
            [sfsim25.quadtree :refer (increase-level? quadtree-update update-level-of-detail)]
            [sfsim25.clouds :refer (cloud-atmosphere cloud-planet)]
            [sfsim25.matrix :refer (projection-matrix quaternion->matrix shadow-matrix-cascade split-mixed
                                    transformation-matrix)]
            [sfsim25.quaternion :as q]
            [sfsim25.util :refer (slurp-floats sqr)]
            [sfsim25.shaders :as shaders]
            [sfsim25.opacity :as opacity])
  (:import [org.lwjgl.opengl GL11]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def width 1280)
(def height 720)

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
(def cap 0.007)
(def detail-scale 4000)
(def cloud-scale 100000)
(def series (take 4 (iterate #(* % 0.7) 1.0)))
(def sum-series (apply + series))
(def cloud-octaves (mapv #(/ % sum-series) series))
(def perlin-series (take 4 (iterate #(* % 0.7) 1.0)))
(def perlin-sum-series (apply + perlin-series))
(def perlin-octaves (mapv #(/ % perlin-sum-series) perlin-series))
(def mix 0.8)
(def opacity-step (atom 250.0))
(def step (atom 300.0))
(def worley-size 64)
(def shadow-size 512)
(def cover-size 512)
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
(def position (atom (vec3 (+ 3.0 radius) 0 0)))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def num-steps 3)
(def num-opacity-layers 7)
(def opacity-cutoff 0.01)

(GLFW/glfwInit)

(def window (make-window "sfsim25" width height))
(GLFW/glfwShowWindow window)

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def worley-tex (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap worley-tex)

(def worley-data (float-array (map #(+ (* 0.3 %1) (* 0.7 %2))
                                   (slurp-floats "data/clouds/perlin.raw")
                                   (slurp-floats "data/clouds/worley-cover.raw"))))
(def perlin-worley-tex (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data worley-data}))

(def noise-data (slurp-floats "data/bluenoise.raw"))
(def bluenoise-tex (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data noise-data}))

(def cover (map (fn [i] {:width cover-size :height cover-size :data (slurp-floats (str "data/clouds/cover" i ".raw"))}) (range 6)))
(def cloud-cover-tex (make-float-cubemap :linear :clamp cover))

(def transmittance-data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def transmittance-tex (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data transmittance-data}))

(def scatter-data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def scatter-tex (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data scatter-data}))

(def mie-data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def mie-tex (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data mie-data}))

(def surface-radiance-data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def surface-radiance-tex (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data surface-radiance-data}))

; Program to render cascade of deep opacity maps
(def opacity-renderer
  (opacity/make-opacity-renderer :num-opacity-layers num-opacity-layers
                                 :cloud-octaves cloud-octaves
                                 :perlin-octaves perlin-octaves
                                 :cover-size cover-size
                                 :shadow-size shadow-size
                                 :noise-size noise-size
                                 :worley-size worley-size
                                 :radius radius
                                 :cloud-bottom cloud-bottom
                                 :cloud-top cloud-top
                                 :cloud-multiplier cloud-multiplier
                                 :cover-multiplier cover-multiplier
                                 :cap cap
                                 :detail-scale detail-scale
                                 :cloud-scale cloud-scale
                                 :worley-tex worley-tex
                                 :perlin-worley-tex perlin-worley-tex
                                 :cloud-cover-tex cloud-cover-tex))

; Program to render shadow map of planet
(def planet-shadow-renderer
  (planet/make-planet-shadow-renderer :tilesize tilesize
                                      :shadow-size shadow-size))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def program-cloud-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet-clouds (cloud-planet num-steps perlin-octaves cloud-octaves)]))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def program-cloud-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere-clouds (cloud-atmosphere num-steps perlin-octaves cloud-octaves)]))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def program-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [(fragment-planet num-steps)]))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer
  (atmosphere/make-atmosphere-renderer :cover-size cover-size
                                       :num-steps num-steps
                                       :noise-size noise-size
                                       :height-size height-size
                                       :elevation-size elevation-size
                                       :light-elevation-size light-elevation-size
                                       :heading-size heading-size
                                       :transmittance-elevation-size transmittance-elevation-size
                                       :transmittance-height-size transmittance-height-size
                                       :surface-sun-elevation-size surface-sun-elevation-size
                                       :surface-height-size surface-height-size
                                       :albedo 0.9
                                       :reflectivity 0.1
                                       :opacity-cutoff opacity-cutoff
                                       :num-opacity-layers num-opacity-layers
                                       :shadow-size shadow-size
                                       :radius radius
                                       :max-height max-height
                                       :specular 1000
                                       :amplification 6
                                       :transmittance-tex transmittance-tex
                                       :scatter-tex scatter-tex
                                       :mie-tex mie-tex
                                       :surface-radiance-tex surface-radiance-tex))

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
(uniform-float program-cloud-planet "opacity_cutoff" opacity-cutoff)
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
(uniform-float program-cloud-atmosphere "opacity_cutoff" opacity-cutoff)
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
(uniform-float program-planet "opacity_cutoff" opacity-cutoff)
(uniform-int program-planet "num_opacity_layers" num-opacity-layers)
(uniform-int program-planet "shadow_size" shadow-size)
(uniform-float program-planet "radius" radius)
(uniform-float program-planet "max_height" max-height)

(def tree (atom []))
(def changes (atom (future {:tree {} :drop [] :load []})))

(defn background-tree-update [tree]
  (let [increase? (partial increase-level? tilesize radius width 60 10 6 @position)]
    (update-level-of-detail tree radius increase? true)))

(defn load-tile-into-opengl
  [tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program-planet indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
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
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 8 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -8 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))
                 ta (if (@keystates GLFW/GLFW_KEY_E) 0.0001 (if (@keystates GLFW/GLFW_KEY_D) -0.0001 0))
                 ts (if (@keystates GLFW/GLFW_KEY_Y) 0.05 (if (@keystates GLFW/GLFW_KEY_H) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! threshold + (* dt tr))
             (swap! opacity-step + (* dt to))
             (swap! anisotropic + (* dt ta))
             (swap! step + (* dt ts))
             (GL11/glFinish)
             (let [norm-pos   (mag @position)
                   dist       (- norm-pos radius cloud-top)
                   z-near     (max 1.0 (* 0.4 dist))
                   z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                 (sqrt (- (sqr norm-pos) (sqr radius))))
                   indices    [0 1 3 2]
                   vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
                   vao        (make-vertex-array-object program-cloud-atmosphere indices vertices ["point" 3])
                   light-dir  (vec3 (cos @light) (sin @light) 0)
                   projection (projection-matrix (aget w 0) (aget h 0) z-near (+ z-far 1) fov)
                   lod-offset (/ (log (/ (tan (/ fov 2)) (/ (aget w 0) 2) (/ detail-scale worley-size))) (log 2))
                   extrinsics (transformation-matrix (quaternion->matrix @orientation) @position)
                   matrix-cas (shadow-matrix-cascade projection extrinsics light-dir depth mix z-near z-far num-steps)
                   splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
                   scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
                   cos-light  (/ (dot light-dir @position) (mag @position))
                   sin-light  (sqrt (- 1 (sqr cos-light)))
                   opac-step  (* (+ cos-light (* 10 sin-light)) @opacity-step)
                   opacities  (opacity/render-cascade opacity-renderer matrix-cas light-dir @threshold scatter-am opac-step)
                   shadows    (shadow-cascade shadow-size matrix-cas (:program planet-shadow-renderer)
                                              (fn [transform]
                                                  (render-tree (:program planet-shadow-renderer) @tree transform [:surf-tex])))
                   w2         (quot (aget w 0) 2)
                   h2         (quot (aget h 0) 2)
                   clouds     (texture-render-color-depth
                                w2 h2 true
                                (clear (vec3 0 0 0) 0)
                                ; Render clouds in front of planet
                                (use-program program-cloud-planet)
                                (uniform-float program-cloud-planet "cloud_step" @step)
                                (uniform-float program-cloud-planet "cloud_multiplier" cloud-multiplier)
                                (uniform-float program-cloud-planet "cover_multiplier" cover-multiplier)
                                (uniform-float program-cloud-planet "cap" cap)
                                (uniform-float program-cloud-planet "cloud_threshold" @threshold)
                                (uniform-float program-cloud-planet "lod_offset" lod-offset)
                                (uniform-float program-cloud-planet "anisotropic" @anisotropic)
                                (uniform-matrix4 program-cloud-planet "projection" projection)
                                (uniform-vector3 program-cloud-planet "origin" @position)
                                (uniform-matrix4 program-cloud-planet "transform" (inverse extrinsics))
                                (uniform-vector3 program-cloud-planet "light_direction" light-dir)
                                (uniform-float program-cloud-planet "opacity_step" opac-step)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-cloud-planet (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-cloud-planet (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-cloud-planet (str "depth" idx) (:depth item)))
                                (apply use-textures nil transmittance-tex scatter-tex mie-tex worley-tex perlin-worley-tex bluenoise-tex cloud-cover-tex (concat shadows opacities))
                                (render-tree program-cloud-planet @tree (inverse extrinsics) [:surf-tex])
                                ; Render clouds above the horizon
                                (use-program program-cloud-atmosphere)
                                (uniform-float program-cloud-atmosphere "cloud_step" @step)
                                (uniform-float program-cloud-atmosphere "cloud_multiplier" cloud-multiplier)
                                (uniform-float program-cloud-atmosphere "cover_multiplier" cover-multiplier)
                                (uniform-float program-cloud-atmosphere "cap" cap)
                                (uniform-float program-cloud-atmosphere "cloud_threshold" @threshold)
                                (uniform-float program-cloud-atmosphere "lod_offset" lod-offset)
                                (uniform-float program-cloud-atmosphere "anisotropic" @anisotropic)
                                (uniform-matrix4 program-cloud-atmosphere "projection" projection)
                                (uniform-vector3 program-cloud-atmosphere "origin" @position)
                                (uniform-matrix4 program-cloud-atmosphere "extrinsics" extrinsics)
                                (uniform-matrix4 program-cloud-atmosphere "transform" (inverse extrinsics))
                                (uniform-vector3 program-cloud-atmosphere "light_direction" light-dir)
                                (uniform-float program-cloud-atmosphere "opacity_step" opac-step)
                                (doseq [[idx item] (map-indexed vector splits)]
                                       (uniform-float program-cloud-atmosphere (str "split" idx) item))
                                (doseq [[idx item] (map-indexed vector matrix-cas)]
                                       (uniform-matrix4 program-cloud-atmosphere (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                       (uniform-float program-cloud-atmosphere (str "depth" idx) (:depth item)))
                                (apply use-textures transmittance-tex scatter-tex mie-tex worley-tex perlin-worley-tex bluenoise-tex cloud-cover-tex (concat shadows opacities))
                                (render-quads vao))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0)
                                ; Render planet with cloud overlay
                                (use-program program-planet)
                                (uniform-matrix4 program-planet "projection" projection)
                                (uniform-vector3 program-planet "origin" @position)
                                (uniform-matrix4 program-planet "transform" (inverse extrinsics))
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
                                (apply use-textures nil nil nil nil nil transmittance-tex scatter-tex mie-tex surface-radiance-tex clouds (concat shadows opacities))
                                (render-tree program-planet @tree (inverse extrinsics)
                                             [:surf-tex :day-tex :night-tex :normal-tex :water-tex])
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer
                                                              :splits splits
                                                              :matrix-cascade matrix-cas
                                                              :projection projection
                                                              :extrinsics extrinsics
                                                              :origin @position
                                                              :opacity-step opac-step
                                                              :window-width (aget w 0)
                                                              :window-height (aget h 0)
                                                              :light-direction light-dir
                                                              :z-far z-far
                                                              :clouds clouds
                                                              :opacities opacities))
               (destroy-texture clouds)
               (opacity/destroy-cascade opacities)
               (doseq [shadow shadows]
                      (destroy-texture shadow))
               (destroy-vertex-array-object vao))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, aniso (e/d) %.3f, step (y/h) %.0f, dt %.3f"
                              @threshold @opacity-step @anisotropic @step (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (destroy-texture surface-radiance-tex)
  (destroy-texture mie-tex)
  (destroy-texture scatter-tex)
  (destroy-texture transmittance-tex)
  (destroy-texture cloud-cover-tex)
  (destroy-texture bluenoise-tex)
  (destroy-texture perlin-worley-tex)
  (destroy-texture worley-tex)
  (destroy-program program-cloud-atmosphere)
  (destroy-program program-cloud-planet)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (destroy-program program-planet)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
