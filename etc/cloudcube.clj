(require '[clojure.core.matrix :refer (matrix div sub mul add mget mmul inverse) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin to-radians)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.shaders :as s]
         '[sfsim25.ray :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat GL11 GL12 GL20 GL30 GL32 GL42]
        '[org.lwjgl.input Keyboard]
        '[org.lwjgl BufferUtils])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 1280 720))
;(Display/setFullscreen true)
(Display/create)
(Keyboard/create)

(def z-near 50)
(def z-far 150)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 75)))
(def origin (atom (matrix [0 0 100])))
(def orientation (atom (q/rotation (to-radians 0) (matrix [1 0 0]))))
(def threshold (atom 0.5))
(def anisotropic (atom 0.3))
(def multiplier (atom 5.0))
(def light (atom (/ PI 4)))
(def keystates (atom {}))

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
float lookup_3d(sampler3D tex, vec3 point);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 cloud_track_base(vec3 origin, vec3 light_direction, float a, float b, vec3 incoming, float lod);
float cloud_density(vec3 point, float lod)
{
  float s = lookup_3d(worley, point / cloud_scale);
  return max((s - threshold) * multiplier, 0);
}
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec4 p = shadow * vec4(point, 1);
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
                :fragment [fragment-shader s/ray-box s/lookup-3d phase-function cloud-track-base
                           cloud-track exponential-sampling s/is-above-horizon]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def size 128)
;(def values (worley-noise 12 size true))
;(spit-floats "values.raw" (float-array values))
(def values (slurp-floats "data/worley.raw"))
(def worley (make-float-texture-3d :linear :repeat {:width size :height size :depth size :data values}))

(use-program program)
(uniform-sampler program :worley 0)
(uniform-sampler program :opacity 1)
(uniform-sampler program :opacity_shape 2)

(def svertex-shader
"#version 410 core
uniform mat4 iprojection;
in vec3 point;
out VS_OUT
{
  vec3 origin;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vec4 point = iprojection * vec4(point, 1);
  vs_out.origin = point.xyz / point.w;
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
uniform int cloud_base_samples;
uniform float cloud_scatter_amount;
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
float lookup_3d(sampler3D table, vec3 point);
float phase(float g, float mu);
float cloud_density(vec3 point, float lod)
{
  float s = lookup_3d(worley, point / cloud_scale);
  return max((s - threshold) * multiplier, 0);
}
void main()
{
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), fs_in.origin, -light_direction);
  float scatter_amount = (anisotropic * phase(0.76, -1) + 1 - anisotropic) * cloud_scatter_amount;
  float stepsize = 60.0 / cloud_base_samples;
  int steps = int(ceil(intersection.y / stepsize));
  stepsize = intersection.y / steps;
  float previous_transmittance = 1.0;
  float previous_depth = intersection.x - stepsize;
  float start_depth = 0.0;
  int filled = 0;
  for (int i=0; i<steps; i++) {
    float dist = intersection.x + (i + 0.5) * stepsize;
    float depth = intersection.x + (i + 1) * stepsize;
    vec3 point = fs_in.origin - light_direction * dist;
    float density = cloud_density(point, 0.0);
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
  (make-program :vertex [svertex-shader]
                :fragment [sfragment-shader s/ray-box s/lookup-3d phase-function]))

(use-program sprogram)
(uniform-sampler sprogram :worley 0)

(def indices [0 1 3 2])
(def vertices [-1 -1 1, 1 -1 1, -1 1 1, 1 1 1])  ; quad near to light in NDCs
(def vao2 (make-vertex-array-object sprogram indices vertices [:point 3]))

(def opacity (create-texture-3d :linear :clamp 512 512 7 (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F 512 512 7)))
(def opacity-shape (make-empty-float-texture-2d :linear :clamp 512 512))

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
             ti (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.0005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.0005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! threshold + (* dt tr))
         (swap! anisotropic + (* dt ts))
         (swap! multiplier + (* dt tm))
         (reset! origin (mmul (quaternion->matrix @orientation) (matrix [0 0 100])))
         (swap! light + (* l dt))
         (swap! t0 + dt)
         (swap! n + 1)
         (print "\rthreshold" (format "%.3f" @threshold)
                "anisotropic" (format "%.3f" @anisotropic)
                "multiplier" (format "%.3f" @multiplier)
                "fps" (format "%.3f" (/ (* @n 1000.0) (- t1 tf)))))
       (let [light-direction (matrix [0 (cos @light) (sin @light)])
             transform       (transformation-matrix (quaternion->matrix @orientation) @origin)
             shadow-mat      (shadow-matrices 512 512 projection transform light-direction 0)]
         (framebuffer-render 512 512 :cullback nil [opacity opacity-shape]
                             (use-program sprogram)
                             (use-textures worley)
                             (uniform-matrix4 sprogram :iprojection (inverse (:shadow-ndc-matrix shadow-mat)))
                             (uniform-vector3 sprogram :light_direction light-direction)
                             (uniform-float sprogram :depth (:depth shadow-mat))
                             (uniform-float sprogram :separation 10.0)
                             (uniform-float sprogram :threshold @threshold)
                             (uniform-float sprogram :anisotropic @anisotropic)
                             (uniform-float sprogram :multiplier (* 0.1 @multiplier))
                             (uniform-float sprogram :cloud_scatter_amount 1.0)
                             (uniform-int sprogram :cloud_base_samples 64)
                             (uniform-float sprogram :cloud_scale 200)
                             (render-quads vao2))
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (use-textures worley opacity opacity-shape)
                          (uniform-matrix4 program :projection projection)
                          (uniform-matrix4 program :transform transform)
                          (uniform-matrix4 program :shadow (:shadow-map-matrix shadow-mat))
                          (uniform-vector3 program :origin @origin)
                          (uniform-float program :threshold @threshold)
                          (uniform-float program :anisotropic @anisotropic)
                          (uniform-float program :cloud_scatter_amount 1.0)
                          (uniform-int program :cloud_min_samples 1)
                          (uniform-int program :cloud_max_samples 64)
                          (uniform-float program :cloud_scale 200)
                          (uniform-float program :cloud_max_step 1.05)
                          (uniform-int program :cloud_base_samples 64)
                          (uniform-float program :multiplier (* 0.1 @multiplier))
                          (uniform-vector3 program :light_direction light-direction)
                          (uniform-float program :depth (:depth shadow-mat))
                          (uniform-float program :thickness (* 7 10.0))
                          (render-quads vao))))

(destroy-texture opacity)
(destroy-texture opacity-shape)
(GL30/glDeleteFramebuffers fbo)
(destroy-vertex-array-object vao2)
(destroy-texture worley)
(destroy-vertex-array-object vao)
(destroy-program program)
(destroy-program sprogram)
(Display/destroy)
