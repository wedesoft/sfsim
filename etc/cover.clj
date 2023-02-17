(require '[clojure.core.matrix :refer (matrix eseq)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (sin cos PI sqrt)]
         '[comb.template :as template]
         '[sfsim25.render :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode GL20]
        '[org.lwjgl.input Keyboard]
        '[mikera.matrixx Matrix])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1080 2) (/ 1080 2)))
(Display/create)

(Keyboard/create)

(defn uniform-matrix3
  "Set uniform 3x3 matrix in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^Matrix value]
  (GL20/glUniformMatrix3 (GL20/glGetUniformLocation ^int program (name k)) true (make-float-buffer (float-array (eseq value)))))

(def worley-size 64)

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

; http://marc-b-reynolds.github.io/distribution/2016/11/28/Uniform.html
(defn uniform-disc []
  (let [a     (rand)
        theta (* PI (- (* 2 (rand)) 1))]
    (matrix [(* (sqrt a) (cos theta)) (* (sqrt a) (sin theta))])))

(defn uniform-sphere []
  (let [[x y] (eseq (uniform-disc))]
    (matrix [(* 2 x (sqrt (- 1 (+ (* x x) (* y y)))))
             (* 2 y (sqrt (- 1 (+ (* x x) (* y y)))))
             (- 1 (* 2 (+ (* x x) (* y y))))])))

(defn random-oriented-matrix []
  (oriented-matrix (uniform-sphere)))

(def n 20)

(def orientation (atom (repeatedly n random-oriented-matrix)))
(def fraction (atom (repeatedly n rand)))

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
  (template/fn [n]
"#version 410 core
uniform sampler3D worley;
uniform float scale;
uniform float cloud_scale;
uniform float amount;
uniform float sigma;
<% (doseq [i (range n)] %>
uniform float fraction<%= i %>;
uniform mat3 orientation<%= i %>;
<% ) %>
in VS_OUT
{
  vec3 point;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec3 fragColor;

vec3 rotate(vec3 point, float y)
{
  float r = length(point.xy);
  float angle = -r * y * amount * exp(-r*r/(2*sigma*sigma)) / (sigma * exp(1));
  return vec3(cos(angle) * point.x - sin (angle) * point.y, sin(angle) * point.x + cos(angle) * point.y, point.z);
}
vec3 swirl(mat3 orientation, float fraction, vec3 point)
{
  return inverse(orientation) * rotate(orientation * point, 2 * fraction - 1);
}
void main()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = vec3(fs_in.point.xy, -1 + intersection.x);
<% (doseq [i (range n)] %>
    p = swirl(orientation<%= i %>, fraction<%= i %>, p);
<% ) %>
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
}") )

(def keystates (atom {}))

(def program (make-program :vertex [vertex-shader] :fragment [(fragment-shader n) shaders/ray-sphere]))
(use-program program)
(uniform-sampler program :worley 0)

(def indices [0 1 3 2])
(def vertices [-1 -1 0, 1 -1 0, -1 1 0, 1  1 0])
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def scale (atom 1.0))
(def magnification (atom 1.0))
(def amount (atom 1.0))
(def sigma (atom 0.1))


(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [dscale         (if (@keystates Keyboard/KEY_Q) 0.0005 (if (@keystates Keyboard/KEY_A) -0.0005 0))
             dmagnification (if (@keystates Keyboard/KEY_W) 0.0005 (if (@keystates Keyboard/KEY_S) -0.0005 0))
             damount        (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))
             dsigma         (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))]
         (when (@keystates Keyboard/KEY_X)
           (reset! orientation (repeatedly n random-oriented-matrix))
           (reset! fraction (repeatedly n rand)))
         (swap! scale + dscale)
         (swap! magnification + dmagnification)
         (swap! amount + damount)
         (swap! sigma + dsigma)
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 0 0]))
                          (uniform-float program :scale @scale)
                          (uniform-float program :cloud_scale @magnification)
                          (uniform-float program :amount @amount)
                          (uniform-float program :sigma @sigma)
                          (doseq [i (range n)]
                                 (uniform-float program (keyword (str "fraction" i)) (nth @fraction i))
                                 (uniform-matrix3 program (keyword (str "orientation" i)) (nth @orientation i)))
                          (use-textures W)
                          (render-quads vao))
         (print (format "\rscale = %5.3f, cloud_scale = %5.3f, amount = %5.3f, sigma = %5.3f          "
                        @scale @magnification @amount @sigma))
         (flush)))

(destroy-program program)
(destroy-vertex-array-object vao)
(destroy-texture W)

(Display/destroy)
