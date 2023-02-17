(require '[clojure.core.matrix :refer (matrix)]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1080 2) (/ 1080 2)))
(Display/create)

(Keyboard/create)

(def worley-size 64)

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def vertex-shader
"#version 410 core
in vec3 point;
out VS_OUT
{
  vec3 point;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-shader
"#version 410 core
uniform sampler3D worley;
uniform float scale;
uniform float cloud_scale;
uniform float amount;
in VS_OUT
{
  vec3 point;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec3 fragColor;

vec3 rotate(vec3 point)
{
  float r = length(point.xy);
  float sigma = 0.1;
  float angle = -r * amount * exp(-r*r/(2*sigma*sigma)) / (sigma * exp(1));
  return vec3(cos(angle) * point.x - sin (angle) * point.y, sin(angle) * point.x + cos(angle) * point.y, point.z);
}
void main ()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = vec3(fs_in.point.xy, -1 + intersection.x);
    p = rotate(p);
    float density = 0.0;
    density += texture(worley, p / cloud_scale).r;
    density += 0.5 * texture(worley, 2 * p / cloud_scale).r;
    density += 0.25 * texture(worley, 4 * p / cloud_scale).r;
    density += 0.125 * texture(worley, 8 * p / cloud_scale).r;
    density /= 1.0 + 0.5 + 0.25 + 0.125;
    density = max(0, scale * density - scale + 1);
    fragColor = vec3(density, density, 1.0);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def keystates (atom {}))

(def program (make-program :vertex [vertex-shader] :fragment [fragment-shader shaders/ray-sphere]))
(use-program program)
(uniform-sampler program :worley 0)

(def indices [0 1 3 2])
(def vertices [-1 -1 0, 1 -1 0, -1 1 0, 1  1 0])
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def scale (atom 1.0))
(def magnification (atom 1.0))
(def amount (atom 1.0))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [dscale         (if (@keystates Keyboard/KEY_Q) 0.0005 (if (@keystates Keyboard/KEY_A) -0.0005 0))
             dmagnification (if (@keystates Keyboard/KEY_W) 0.0001 (if (@keystates Keyboard/KEY_S) -0.0001 0))
             damount        (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))]
         (swap! scale + dscale)
         (swap! magnification + dmagnification)
         (swap! amount + damount)
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 0 0]))
                          (uniform-float program :scale @scale)
                          (uniform-float program :cloud_scale @magnification)
                          (uniform-float program :amount @amount)
                          (use-textures W)
                          (render-quads vao))))

(destroy-program program)
(destroy-vertex-array-object vao)
(destroy-texture W)

(Display/destroy)
