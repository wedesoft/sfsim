(require '[clojure.math :refer (PI sqrt pow cos sin to-radians log tan)]
         '[fastmath.matrix :refer (mulv inverse)]
         '[fastmath.vector :refer (vec3)]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.bluenoise :refer :all]
         '[sfsim25.shaders :as s]
         '[sfsim25.ray :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])
(import '[org.lwjgl.opengl GL11 GL12 GL20 GL30 GL32 GL42]
        '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.input Keyboard]
        '[org.lwjgl BufferUtils])

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)

(def width 640)
(def height 480)

(def window (GLFW/glfwCreateWindow width height "scratch" 0 0))
(Keyboard/create)

(def z-near 50)
(def z-far 150)
(def fov (to-radians 75))
(def projection (projection-matrix width height z-near z-far fov))
(def origin (atom (vec3 0 0 100)))
(def orientation (atom (q/* (q/rotation (to-radians 125) (vec3 1 0 0)) (q/rotation (to-radians 24) (vec3 0 1 0)))))
(def octaves [0.5 0.25 0.125 0.125])
(def threshold (atom 0.53))
(def anisotropic (atom 0.2))
(def multiplier (atom 2.0))
(def cloud-scale 100)
(def light (atom (+ (/ PI 4) 0.1)))
(def keystates (atom {}))
(def shadow-size 128)

(def vertex-shader "#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 point;
out VS_OUT
{
  vec3 direction;
} vs_out;
void main()
{
  vs_out.direction = (transform * vec4(point, 0)).xyz;
  gl_Position = projection * vec4(point, 1);
}")

(def fragment-shader
"#version 410 core
uniform sampler3D worley;
uniform sampler3D opacity;
uniform sampler2D opacity_shape;
uniform vec3 origin;
uniform vec3 light_direction;
uniform mat4 shadow;
uniform int shadow_size;
uniform float threshold;
uniform float multiplier;
uniform float cloud_scale;
uniform float depth;
uniform float thickness;
in VS_OUT
{
  vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float octaves(vec3 point, float lod);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec4 convert_shadow_index(vec4 idx, int size_y, int size_x);
float cloud_density(vec3 point, float lod)
{
  float s = octaves(point / cloud_scale, lod);
  return max(s - threshold, 0) * multiplier;
}
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec4 p = convert_shadow_index(shadow * vec4(point, 1), shadow_size, shadow_size);
  float offset = texture(opacity_shape, p.xy).r;
  float z = (1 - p.z - offset) * depth / thickness;
  vec3 idx = vec3(p.xy, z);
  float o = texture(opacity, idx).r;
  return vec3(o, o, o);
}
vec3 transmittance_track(vec3 p, vec3 q)
{
  return vec3(1, 1, 1);
}
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  return vec3(0, 0, 0);
}
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float cos_light = dot(light_direction, direction);
  vec3 bg = 0.7 * pow(max(cos_light, 0), 1000) + vec3(0.3, 0.3, 0.5);
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), origin, direction);
  fragColor = cloud_track(light_direction, origin, direction, intersection.x, intersection.x + intersection.y, bg);
}")

(def program
  (make-program :vertex [vertex-shader]
                :fragment [fragment-shader s/ray-box (s/noise-octaves-lod "octaves" "lookup_3d" octaves)
                           (s/lookup-3d-lod "lookup_3d" "worley") phase-function
                           cloud-track linear-sampling s/is-above-horizon s/convert-shadow-index
                           sampling-offset]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4 4 -1, 4 4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def size 64)
(def values1 (slurp-floats "data/clouds/worley-cover.raw"))
; (def values1 (slurp-floats "data/clouds/perlin.raw"))
(def worley (make-float-texture-3d :linear :repeat {:width size :height size :depth size :data values1}))
(generate-mipmap worley)
(def noise-size 64)
(def values2 (slurp-floats "data/bluenoise.raw"))
(def bluenoise (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data values2}))

(use-program program)
(uniform-sampler program "worley" 0)
(uniform-sampler program "bluenoise" 1)
(uniform-sampler program "opacity" 2)
(uniform-sampler program "opacity_shape" 3)

(def svertex-shader
"#version 410 core
uniform mat4 iprojection;
uniform int shadow_size;
in vec3 point;
out VS_OUT
{
  vec3 origin;
} vs_out;
vec4 grow_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  gl_Position = vec4(point, 1);
  vec4 origin = iprojection * grow_shadow_index(vec4(point, 1), shadow_size, shadow_size);
  vs_out.origin = origin.xyz;
}")

(def sfragment-shader
"#version 410 core
uniform sampler3D worley;
uniform vec3 light_direction;
uniform float depth;
uniform float separation;
uniform float threshold;
uniform float anisotropic;
uniform float multiplier;
uniform float cloud_scale;
uniform float level_of_detail;
uniform int cloud_base_samples;
uniform int shadow_size;
in VS_OUT
{
  vec3 origin;
} fs_in;
layout (location = 0) out float opacity1;
layout (location = 1) out float opacity2;
layout (location = 2) out float opacity3;
layout (location = 3) out float opacity4;
layout (location = 4) out float opacity5;
layout (location = 5) out float opacity6;
layout (location = 6) out float opacity7;
layout (location = 7) out float opacity_shape;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float step_size(float a, float b, int num_samples);
float sample_point(float a, float idx, float step_size);
float octaves(vec3 point, float lod);
float phase(float g, float mu);
float cloud_density(vec3 point, float lod)
{
  float s = octaves(point / cloud_scale, lod);
  return max((s - threshold) * multiplier, 0);
}
void main()
{
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), fs_in.origin, -light_direction);
  int steps = int(ceil(cloud_base_samples * intersection.y / 60.0));
  float scatter_amount = anisotropic * phase(0.76, -1) + 1 - anisotropic;
  float stepsize = step_size(intersection.x, intersection.y + intersection.x, steps);
  float previous_transmittance = 1.0;
  float previous_depth = intersection.x - stepsize;
  float start_depth = 0.0;
  int filled = 0;
  for (int i=0; i<steps; i++) {
    float depth = sample_point(intersection.x, i, stepsize);
    vec3 point = fs_in.origin - light_direction * depth;
    float density = cloud_density(point, level_of_detail);
    float transmittance;
    if (previous_transmittance == 1.0) {
      start_depth = intersection.x + i * stepsize;
    };
    if (density > 0) {
      float transmittance_step = exp((scatter_amount - 1) * density * stepsize);
      transmittance = previous_transmittance * transmittance_step;
    } else {
      transmittance = previous_transmittance;
    };
    if (previous_depth < start_depth + 0 * separation && depth >= start_depth + 0 * separation) {
      opacity1 = 1.0;
      filled = 1;
    };
    if (previous_depth < start_depth + 1 * separation && depth >= start_depth + 1 * separation) {
      float s = (start_depth + 1 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity2 = t;
      filled = 2;
    };
    if (previous_depth < start_depth + 2 * separation && depth >= start_depth + 2 * separation) {
      float s = (start_depth + 2 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity3 = t;
      filled = 3;
    };
    if (previous_depth < start_depth + 3 * separation && depth >= start_depth + 3 * separation) {
      float s = (start_depth + 3 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity4 = t;
      filled = 4;
    };
    if (previous_depth < start_depth + 4 * separation && depth >= start_depth + 4 * separation) {
      float s = (start_depth + 4 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity5 = t;
      filled = 5;
    };
    if (previous_depth < start_depth + 5 * separation && depth >= start_depth + 5 * separation) {
      float s = (start_depth + 5 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity6 = t;
      filled = 6;
    };
    if (previous_depth < start_depth + 6 * separation && depth >= start_depth + 6 * separation) {
      float s = (start_depth + 6 * separation - previous_depth) / (depth - previous_depth);
      float t = s * transmittance + (1 - s) * previous_transmittance;
      opacity7 = t;
      filled = 7;
    };
    previous_depth = depth;
    previous_transmittance = transmittance;
  };
  if (filled <= 0) opacity1 = previous_transmittance;
  if (filled <= 1) opacity2 = previous_transmittance;
  if (filled <= 2) opacity3 = previous_transmittance;
  if (filled <= 3) opacity4 = previous_transmittance;
  if (filled <= 4) opacity5 = previous_transmittance;
  if (filled <= 5) opacity6 = previous_transmittance;
  if (filled <= 6) opacity7 = previous_transmittance;
  opacity_shape = start_depth / depth;
}")

(def sprogram
  (make-program :vertex [svertex-shader s/grow-shadow-index]
                :fragment [sfragment-shader s/ray-box (s/noise-octaves-lod "octaves" "lookup_3d" octaves)
                           (s/lookup-3d-lod "lookup_3d" "worley") linear-sampling phase-function]))

(use-program sprogram)
(uniform-sampler sprogram "worley" 0)

(def indices [0 1 3 2])
(def vertices [-1 -1 1, 1 -1 1, -1 1 1, 1 1 1])  ; quad near to light in NDCs
(def vao2 (make-vertex-array-object sprogram indices vertices [:point 3]))

(def opacity (create-texture-3d :linear :clamp shadow-size shadow-size 7 (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F shadow-size shadow-size 7)))
(def opacity-shape (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size))
(def separation 10.0)
(def samples (atom 16.0))

(def t0 (atom (System/currentTimeMillis)))
(def tf @t0)
(def n (atom 0))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates Keyboard/KEY_NUMPAD8) 0.001 (if (@keystates Keyboard/KEY_NUMPAD2) -0.001 0))
             rb (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             rc (if (@keystates Keyboard/KEY_NUMPAD3) 0.001 (if (@keystates Keyboard/KEY_NUMPAD1) -0.001 0))
             tr (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             ts (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))
             tm (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))
             tl (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.0005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.0005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
         (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
         (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
         (swap! threshold + (* dt tr))
         (swap! anisotropic + (* dt ts))
         (swap! multiplier + (* dt tm))
         (swap! samples + (* dt tl))
         (reset! origin (mulv (quaternion->matrix @orientation) (vec3 0 0 100)))
         (swap! light + (* l dt))
         (swap! t0 + dt)
         (swap! n + 1)
         (when (zero? (mod @n 10))
           (print "\rthreshold (q/a)" (format "%.3f" @threshold)
                  "anisotropic (w/s)" (format "%.3f" @anisotropic)
                  "multiplier (e/d)" (format "%.3f" @multiplier)
                  "samples (r/f)" (format "%3d" (int @samples))
                  "fps" (format "%.3f" (/ (* @n 1000.0) (- t1 tf))))
           (flush)))
       (let [light-direction (vec3 0 (cos @light) (sin @light))
             transform       (transformation-matrix (quaternion->matrix @orientation) @origin)
             shadow-mat      (shadow-matrices projection transform light-direction 0)
             lod             (/ (log (/ (/ (:scale shadow-mat) shadow-size) (/ cloud-scale size))) (log 2))
             lod-offset      (- (/ (log (/ (tan (/ fov 2)) (/ width 2) (/ cloud-scale size))) (log 2))
                                (dec (count octaves)))]
         (framebuffer-render shadow-size shadow-size :cullback nil [opacity opacity-shape]
                             (use-program sprogram)
                             (use-textures worley)
                             (uniform-matrix4 sprogram "iprojection" (inverse (:shadow-ndc-matrix shadow-mat)))
                             (uniform-vector3 sprogram "light_direction" light-direction)
                             (uniform-int sprogram "shadow_size" shadow-size)
                             (uniform-float sprogram "depth" (:depth shadow-mat))
                             (uniform-float sprogram "separation" separation)
                             (uniform-float sprogram "threshold" @threshold)
                             (uniform-float sprogram "anisotropic" @anisotropic)
                             (uniform-float sprogram "multiplier" @multiplier)
                             (uniform-int sprogram "cloud_base_samples" (int @samples))
                             (uniform-float sprogram "cloud_scale" cloud-scale)
                             (uniform-float sprogram "level_of_detail" lod)
                             (uniform-int sprogram "cloud_size" size)
                             (render-quads vao2))
         (onscreen-render width height
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (use-textures worley bluenoise opacity opacity-shape)
                          (uniform-matrix4 program "projection" projection)
                          (uniform-matrix4 program "transform" transform)
                          (uniform-matrix4 program "shadow" (:shadow-map-matrix shadow-mat))
                          (uniform-vector3 program "origin" @origin)
                          (uniform-int program "shadow_size" shadow-size)
                          (uniform-float program "threshold" @threshold)
                          (uniform-float program "anisotropic" @anisotropic)
                          (uniform-float program "cloud_max_step" 2.0)
                          (uniform-int program "noise_size" noise-size)
                          (uniform-float program "lod_offset" lod-offset)
                          (uniform-float program "cloud_scale" cloud-scale)
                          (uniform-int program "cloud_size" size)
                          (uniform-float program "multiplier" @multiplier)
                          (uniform-vector3 program "light_direction" light-direction)
                          (uniform-float program "depth" (:depth shadow-mat))
                          (uniform-float program "thickness" (* 7 separation))
                          (render-quads vao))))

(destroy-texture opacity)
(destroy-texture opacity-shape)
(destroy-vertex-array-object vao2)
(destroy-texture worley)
(destroy-vertex-array-object vao)
(destroy-program program)
(destroy-program sprogram)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)
