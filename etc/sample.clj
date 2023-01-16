(require '[clojure.math :refer (to-radians cos sin PI)]
         '[clojure.core.matrix :refer (matrix add mul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[comb.template :as template]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all]
         '[sfsim25.shaders :as shaders])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])


(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
(Display/create)

(def radius 6378000.0)
(def max-height 35000.0)
(def cloud-bottom 1500)
(def cloud-top 3000)
(def anisotropic 0.15)
(def cloud-scatter-amount 0.2)
(def cloud-multiplier 0.001)
(def z-near 100.0)
(def z-far 120000.0)
(def depth (/ z-far 2))
(def fov 45.0)
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def shadow-size 256)
(def num-opacity-layers 5)
(def opacity-step 100)

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) (to-radians fov)))
(def position (matrix [0 (* -0 radius) (+ radius 3050)]))
(def light (* 0.5 PI))
(def orientation (q/rotation (to-radians 90) (matrix [1 0 0])))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))
(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))
(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def cloud-density-mock
"#version 410 core
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_scale;
uniform float cloud_multiplier;
uniform sampler3D worley;
uniform sampler1D cloud_profile;
float cloud_density(vec3 point, float lod)
{
  return cloud_multiplier;
}")

(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell cloud-density-mock shaders/ray-sphere]))

(def indices [0 1 3 2])
(def shadow-vertices (map #(* % z-far) [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]))
(def shadow-vao (make-vertex-array-object program-shadow indices shadow-vertices [:point 3]))

(defn shadow-cascade [matrix-cascade light-direction scatter-amount]
  (mapv
    (fn [{:keys [shadow-ndc-matrix depth]}]
        (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
              opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)]
          (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                              (use-program program-shadow)
                              (uniform-int program-shadow :shadow_size shadow-size)
                              (uniform-float program-shadow :radius radius)
                              (uniform-float program-shadow :cloud_bottom cloud-bottom)
                              (uniform-float program-shadow :cloud_top cloud-top)
                              (uniform-matrix4 program-shadow :ndc_to_shadow (inverse shadow-ndc-matrix))
                              (uniform-vector3 program-shadow :light_direction light-direction)
                              (uniform-float program-shadow :cloud_multiplier cloud-multiplier)
                              (uniform-float program-shadow :scatter_amount scatter-amount)
                              (uniform-float program-shadow :depth depth)
                              (uniform-float program-shadow :opacity_step opacity-step)
                              (uniform-float program-shadow :cloud_max_step 200)
                              (render-quads shadow-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))

(def vertex-passthrough "#version 410 core
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def probe-shader
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
float opacity_cascade_lookup(vec4 point);
void main()
{
  vec4 point = vec4(<%= x %>, <%= y %>, <%= z %>, 1);
  float result = opacity_cascade_lookup(point);
  fragColor = vec3(result, result, result);
}"))

(defn probe [x y z]
  (let [result          (promise)
        transform       (transformation-matrix (quaternion->matrix orientation) position)
        light-direction (matrix [0 (cos light) (sin light)])
        matrix-cascade  (shadow-matrix-cascade projection transform light-direction depth 0.5 z-near z-far num-opacity-layers)
        splits          (map #(split-mixed 0.5 z-near z-far num-opacity-layers %) (range (inc num-opacity-layers)))
        scatter-amount  (* (+ (* anisotropic (phase 0.76 -1)) (- 1 anisotropic)) cloud-scatter-amount)
        tex-cascade     (shadow-cascade matrix-cascade light-direction scatter-amount)
        indices         [0 1 3 2]
        vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        program         (make-program :vertex [vertex-passthrough]
                                      :fragment [(probe-shader x y z) (opacity-cascade-lookup num-opacity-layers)
                                                 opacity-lookup shaders/convert-2d-index shaders/convert-3d-index])
        vao             (make-vertex-array-object program indices vertices [:point 3])
        tex             (texture-render-color 1 1 true
                                              (clear (matrix [0 0 0]))
                                              (use-program program)
                                              (uniform-sampler program :transmittance 0)
                                              (uniform-sampler program :ray_scatter 1)
                                              (uniform-sampler program :mie_strength 2)
                                              (doseq [i (range num-opacity-layers)]
                                                     (uniform-sampler program (keyword (str "offset" i)) (+ (* 2 i) 3))
                                                     (uniform-sampler program (keyword (str "opacity" i)) (+ (* 2 i) 4)))
                                              (doseq [[idx item] (map-indexed vector splits)]
                                                     (uniform-float program (keyword (str "split" idx)) item))
                                              (doseq [[idx item] (map-indexed vector matrix-cascade)]
                                                     (uniform-matrix4 program
                                                                      (keyword (str "shadow_map_matrix" idx))
                                                                      (:shadow-map-matrix item)))
                                              (uniform-matrix4 program :projection projection)
                                              (uniform-matrix4 program :transform transform)
                                              (uniform-matrix4 program :inverse_transform (inverse transform))
                                              (uniform-vector3 program :origin position)
                                              (uniform-vector3 program :light_direction light-direction)
                                              (uniform-float program :radius radius)
                                              (uniform-float program :max_height max-height)
                                              (uniform-int program :height_size height-size)
                                              (uniform-int program :elevation_size elevation-size)
                                              (uniform-int program :light_elevation_size light-elevation-size)
                                              (uniform-int program :heading_size heading-size)
                                              (uniform-int program :transmittance_height_size transmittance-height-size)
                                              (uniform-int program :transmittance_elevation_size transmittance-elevation-size)
                                              (uniform-float program :cloud_bottom cloud-bottom)
                                              (uniform-float program :cloud_top cloud-top)
                                              (uniform-float program :anisotropic anisotropic)
                                              (uniform-int program :cloud_min_samples 5)
                                              (uniform-int program :cloud_max_samples 64)
                                              (uniform-float program :cloud_scatter_amount cloud-scatter-amount)
                                              (uniform-float program :cloud_max_step 1.04)
                                              (uniform-float program :transparency_cutoff 0.05)
                                              (uniform-float program :depth depth)
                                              (uniform-float program :opacity_step opacity-step)
                                              (uniform-float program :amplification 6.0)
                                              (uniform-float program :cloud_multiplier cloud-multiplier)
                                              (apply use-textures
                                                     T S M
                                                     (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cascade))
                                              (render-quads vao))
        img             (rgb-texture->vectors3 tex)]
    (deliver result (get-vector3 img 0 0))
    (doseq [{:keys [offset layer]} tex-cascade]
           (destroy-texture offset)
           (destroy-texture layer))
    (destroy-vertex-array-object vao)
    (destroy-texture tex)
    (destroy-program program)
    @result))

(probe 0 100 (+ radius 3050))

(destroy-texture T)
(destroy-texture S)
(destroy-texture M)

(Display/destroy)