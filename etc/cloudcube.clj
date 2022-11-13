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
(def threshold (atom 0.1))
(def anisotropic (atom 0.4))
(def multiplier (atom 0.8))
(def light (atom (/ PI 4)))

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
uniform vec3 origin;
uniform vec3 light_direction;
uniform sampler3D worley;
uniform float threshold;
uniform float multiplier;
uniform int cloud_size;
uniform float cloud_scale;
in VS_OUT
{
  vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float interpolate_3d(sampler3D tex, vec3 point, vec3 box_min, vec3 box_max);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 cloud_track_base(vec3 origin, vec3 light_direction, float a, float b, vec3 incoming, float lod);
float cloud_density(vec3 point, float lod)
{
  float s = interpolate_3d(worley, point * cloud_size / cloud_scale, vec3(-30, -30, -30), vec3(30, 30, 30));
  return max((s - threshold) * multiplier, 0);
}
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), point, light_direction);
  vec3 p = point + intersection.x * light_direction;
  vec3 q = point + (intersection.x + intersection.y) * light_direction;
  return cloud_track_base(point, light_direction, intersection.x, intersection.x + intersection.y, vec3(1, 1, 1), 0);
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
                :fragment [fragment-shader s/ray-box s/convert-3d-index s/interpolate-3d phase-function cloud-track-base
                           cloud-track exponential-sampling s/is-above-horizon]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def size 128)

;(def values (worley-noise 12 size true))
;(spit-floats "values.raw" (float-array values))

(def values (slurp-floats "data/worley.raw"))
(def worley (make-float-texture-3d {:width size :height size :depth size :data values}))

(use-program program)
(uniform-sampler program :worley 0)

(def keystates (atom {}))

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
uniform vec3 light_vector;
uniform float depth;
uniform float threshold;
uniform float multiplier;
uniform int cloud_size;
uniform float cloud_scale;
uniform int cloud_base_samples;
in VS_OUT
{
  vec3 origin;
} fs_in;
layout (location = 0) out float color0;
layout (location = 1) out float color1;
layout (location = 2) out float color2;
layout (location = 3) out float color3;
layout (location = 4) out float color4;
layout (location = 5) out float color5;
layout (location = 6) out float color6;
layout (location = 7) out float color7;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float cloud_density(vec3 point, float lod)
{
  float s = interpolate_3d(worley, point * cloud_size / cloud_scale, vec3(-30, -30, -30), vec3(30, 30, 30));
  return max((s - threshold) * multiplier, 0);
}
void main()
{
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), fs_in.origin, -light_vector);
  float f = intersection.y;
  color0 = 0.1 * f;
  color1 = 0.2 * f;
  color2 = 0.3 * f;
  color3 = 0.4 * f;
  color4 = 0.5 * f;
  color5 = 0.6 * f;
  color6 = 0.7 * f;
  color7 = 0.8 * f;
}")

(def sprogram
  (make-program :vertex [svertex-shader]
                :fragment [sfragment-shader s/ray-box]))

(use-program program)
(uniform-sampler program :worley 0)

(def light-vector (matrix [0 (cos @light) (sin @light)]))
(def transform    (transformation-matrix (quaternion->matrix @orientation) @origin))
(def shadow-mat   (shadow-matrices projection transform light-vector 0))

(def result
  (let [indices      [0 1 3 2]
        vertices     [-1 -1 1, 1 -1 1, -1 1 1, 1 1 1]  ; quad near to light in NDCs
        vao          (make-vertex-array-object sprogram indices vertices [:point 3])
        fbo          (GL30/glGenFramebuffers)
        tex          (GL11/glGenTextures)]
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo)
    (GL11/glBindTexture GL12/GL_TEXTURE_3D tex)
    (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F 512 512 8)
    (doseq [i (range 8)]
           (GL30/glFramebufferTexture3D GL30/GL_FRAMEBUFFER (+ GL30/GL_COLOR_ATTACHMENT0 i) GL12/GL_TEXTURE_3D tex 0 i))
    (GL20/glDrawBuffers (make-int-buffer (int-array (for [i (range 8)] (+ GL30/GL_COLOR_ATTACHMENT0 i)))))
    (setup-rendering 512 512 false)
    (use-program sprogram)
    (use-textures worley)
    (uniform-matrix4 sprogram :iprojection (inverse (:shadow-ndc-matrix shadow-mat)))
    (uniform-vector3 sprogram :light_vector light-vector)
    (uniform-float sprogram :depth (:depth shadow-mat))
    (uniform-float sprogram :threshold @threshold)
    (uniform-float sprogram :anisotropic @anisotropic)
    (uniform-float sprogram :multiplier (* 0.1 @multiplier))
    (uniform-float sprogram :cloud_scatter_amount 1.0)
    (uniform-int program :cloud_base_samples 64)
    (uniform-int sprogram :cloud_size size)
    (uniform-float sprogram :cloud_scale 350)
    (render-quads vao)
    (destroy-vertex-array-object vao)
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
    (GL30/glDeleteFramebuffers fbo)
    {:texture tex :target GL12/GL_TEXTURE_3D}))


(defn texture->floats
  "Extract floating-point depth map from texture"
  [texture width height depth layer]
  (with-texture (:target texture) (:texture texture)
    (let [buf  (BufferUtils/createFloatBuffer (* width height depth))
          size (* width height)
          data (float-array (* width height depth))]
      (GL11/glGetTexImage GL12/GL_TEXTURE_3D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data (float-array (take size (drop (* layer size) data)))})))

(def img (texture->floats result 512 512 8 7))
(apply max (:data img))
(show-floats img)

(destroy-program sprogram)

(def t0 (atom (System/currentTimeMillis)))
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
         (print "\rthreshold" (format "%.3f" @threshold)
                "anisotropic" (format "%.3f" @anisotropic)
                "multiplier" (format "%.3f" @multiplier)
                "dt" (format "%.3f" (* dt 0.001)) "              "))
       (onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 0 0]))
                 (use-program program)
                 (use-textures worley)
                 (uniform-matrix4 program :projection projection)
                 (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @origin))
                 (uniform-vector3 program :origin @origin)
                 (uniform-float program :threshold @threshold)
                 (uniform-float program :anisotropic @anisotropic)
                 (uniform-float program :cloud_scatter_amount 1.0)
                 (uniform-int program :cloud_min_samples 1)
                 (uniform-int program :cloud_max_samples 64)
                 (uniform-int program :cloud_size size)
                 (uniform-float program :cloud_scale 350)
                 (uniform-float program :cloud_max_step 1.05)
                 (uniform-int program :cloud_base_samples 64)
                 (uniform-float program :multiplier (* 0.1 @multiplier))
                 (uniform-vector3 program :light_direction (matrix [0 (cos @light) (sin @light)]))
                 (render-quads vao)))

(destroy-texture worley)
(destroy-vertex-array-object vao)
(destroy-program program)
(Display/destroy)
