(require '[clojure.math :refer (to-radians cos sin PI round floor pow)]
         '[clojure.core.matrix :refer (matrix array dimension-count eseq slice-view add sub mul inverse mmul mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[com.climate.claypoole :refer (pfor ncpus)]
         '[comb.template :as template]
         '[sfsim25.render :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL30]
        '[org.lwjgl BufferUtils])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 640))
(Display/create)

(defn random-point-grid
  "Create a 3D grid with a random point in each cell"
  ([divisions size]
   (random-point-grid divisions size rand))
  ([divisions size random]
   (let [cellsize (/ size divisions)]
     (array
       (map (fn [j]
                (map (fn [i]
                         (add (matrix [(* i cellsize) (* j cellsize)])
                              (matrix (repeatedly 2 #(random cellsize)))))
                     (range divisions)))
            (range divisions))))))

(defn- clipped-index-and-offset
  "Return index modulo dimension of grid and offset for corresponding coordinate"
  [grid size dimension index]
  (let [divisions     (dimension-count grid dimension)
        clipped-index (if (< index divisions) (if (>= index 0) index (+ index divisions)) (- index divisions))
        offset        (if (< index divisions) (if (>= index 0) 0 (- size)) size)]
    [clipped-index offset]))

(defn extract-point-from-grid
  "Extract the random point from the given grid cell"
  [grid size j i]
  (let [[i-clip x-offset] (clipped-index-and-offset grid size 1 i)
        [j-clip y-offset] (clipped-index-and-offset grid size 0 j)]
    (add (slice-view (slice-view grid j-clip) i-clip) (matrix [x-offset y-offset]))))

(defn closest-distance-to-point-in-grid
  "Return distance to closest point in 3D grid"
  [grid divisions size point]
  (let [cellsize (/ size divisions)
        [x y]  (eseq point)
        [i j]  [(quot x cellsize) (quot y cellsize)]
        points   (for [dj [-1 0 1] di [-1 0 1]] (extract-point-from-grid grid size (+ j dj) (+ i di)))]
    (apply min (map #(norm (sub point %)) points))))

(defn normalise-vector
  "Normalise the values of a vector"
  [values]
  (let [maximum (apply max values)]
    (vec (pmap #(/ % maximum) values))))

(defn invert-vector
  "Invert values of a vector"
  [values]
  (mapv #(- 1 %) values))

(defn worley-noise
  "Create 3D Worley noise"
  [divisions size]
  (let [grid (random-point-grid divisions size)]
    (invert-vector
      (normalise-vector
        (for [j (range size) i (range size)]
              (do
                (closest-distance-to-point-in-grid grid divisions size (matrix [(+ j 0.5) (+ i 0.5)]))))))))

(defn rg-texture->vectors3
  "Extract floating-point BGR vectors from texture"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 2))
          data (float-array (* width height 2))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL30/GL_RG GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(def size 64)
(def large-size 640)
(def divisions 8)
(def clouds (worley-noise divisions size))
(def potential (worley-noise divisions size))
(def cloud-octaves [0.5 0.25 0.125 0.0625])
(def potential-octaves [0.8 0.2])
;(defn worley-octaves [point]
;  (apply + (map-indexed #(* %2 (worley-smooth (mul (pow 2 %1) point))) octaves1)))
;(defn potential-octaves [point]
;  (+ (apply + (map-indexed #(* %2 (potential-smooth (mul (pow 2 %1) point))) octaves2))
;     (* 2 (sin (* (* 1.0 PI) (/ (- (mget point 1) 32) 32))))))
(def clouds-tex (make-float-texture-2d :linear :repeat {:width size :height size :data (float-array clouds)}))
(def potential-tex (make-float-texture-2d :linear :repeat {:width size :height size :data (float-array potential)}))

(def texture-octaves
  (template/fn [octaves]
"#version 410 core
float texture_octaves(sampler2D tex, vec2 point)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * texture(tex, point).r;
  point *= 2;
<% ) %>
  return result;
}"))

(def gradient-shader
"#version 410 core
uniform float epsilon;
uniform int size;
float texture_octaves(sampler2D tex, vec2 point);
vec2 gradient(sampler2D tex, vec2 point)
{
  vec2 delta_x = vec2(epsilon / size, 0);
  vec2 delta_y = vec2(0, epsilon / size);
  float dx = (texture_octaves(tex, point + delta_x) - texture_octaves(tex, point - delta_x)) / (2 * epsilon);
  float dy = (texture_octaves(tex, point + delta_y) - texture_octaves(tex, point - delta_y)) / (2 * epsilon);
  return vec2(dx, dy);
}")

(def curl-shader
"#version 410 core
vec2 gradient(sampler2D tex, vec2 point);
vec2 curl(sampler2D tex, vec2 point)
{
  vec2 grad = gradient(tex, point);
  return vec2(grad.y, -grad.x);
}")

(def epsilon (pow 0.5 1))

(def field (make-empty-texture-2d :linear :repeat GL30/GL_RG32F large-size large-size))

(def vertex-simple
"#version 410 core
in vec2 point;
void main()
{
  gl_Position = vec4(point, 0.5, 1);
}")

(def gradient-fragment
"#version 410 core
layout (location = 0) out vec2 gradient_out;
uniform sampler2D potential;
uniform int large_size;
vec2 curl(sampler2D tex, vec2 point);
void main()
{
  gradient_out = curl(potential, vec2(gl_FragCoord.x / large_size, gl_FragCoord.y / large_size));
}")

(def gradient-program
  (make-program :vertex [vertex-simple]
                :fragment [gradient-fragment curl-shader gradient-shader (texture-octaves potential-octaves)]))

(def indices [0 1 3 2])
(def vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0])
(def gradient-vao (make-vertex-array-object gradient-program indices vertices [:point 2]))

(framebuffer-render large-size large-size :cullback nil [field]
                    (use-program gradient-program)
                    (uniform-sampler gradient-program :potential 0)
                    (uniform-int gradient-program :size size)
                    (uniform-int gradient-program :large_size large-size)
                    (uniform-float gradient-program :epsilon epsilon)
                    (use-textures potential-tex)
                    (render-quads gradient-vao))

(def cloud-fragment
"#version 410 core
uniform sampler2D clouds;
uniform sampler2D warp;
uniform int large_size;
out vec3 fragColor;
float texture_octaves(sampler2D tex, vec2 point);
void main()
{
  vec2 pos = vec2(gl_FragCoord.x / large_size, gl_FragCoord.y / large_size);
  vec2 delta = texture(warp, pos).xy / large_size;
  float result = texture_octaves(clouds, pos + delta);
  fragColor = vec3(result, result, result);
}")

(def cloud-program
  (make-program :vertex [vertex-simple]
                :fragment [cloud-fragment (texture-octaves cloud-octaves)]))

(def clouds-vao (make-vertex-array-object gradient-program indices vertices [:point 2]))

(onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 1 0]))
                 (use-program cloud-program)
                 (uniform-sampler cloud-program :clouds 0)
                 (uniform-sampler cloud-program :warp 1)
                 (uniform-int cloud-program :large_size large-size)
                 (use-textures clouds-tex field)
                 (render-quads clouds-vao))

(destroy-program cloud-program)
(destroy-vertex-array-object clouds-vao)
(destroy-program gradient-program)
(destroy-vertex-array-object gradient-vao)
(destroy-texture potential-tex)
(destroy-texture field)
(Display/destroy)
