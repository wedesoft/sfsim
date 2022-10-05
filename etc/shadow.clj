(require '[clojure.math :refer (PI to-radians cos sin)]
         '[clojure.core.matrix :refer (matrix identity-matrix inverse mmul)]
         '[sfsim25.render :refer :all]
         '[sfsim25.util :refer :all]
         '[sfsim25.matrix :refer :all])

(import '[org.lwjgl.opengl Pbuffer Display DisplayMode PixelFormat GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL43 GL45]
        '[org.lwjgl BufferUtils])

(def size 256)
(def width 320)
(def height 240)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. width height))
(Display/create)

(def vertex-shadow
"#version 410 core
uniform mat4 shadow_ndc_matrix;
in vec3 point;
void main(void)
{
  gl_Position = shadow_ndc_matrix * vec4(point, 1);
}
")

(def fragment-shadow
"#version 410 core
void main(void)
{
}")

(def program-shadow (make-program :vertex [vertex-shadow] :fragment [fragment-shadow]))

(def vertex-scene
"#version 410 core
uniform mat4 transform;
uniform mat4 projection;
uniform mat4 shadow_map_matrix;
in vec3 point;
out vec4 shadow_pos;
out vec3 pos;
void main(void)
{
  gl_Position = projection * transform * vec4(point, 1);
  shadow_pos = shadow_map_matrix * vec4(point, 1);
  pos = point;
}")

(def fragment-scene
"#version 410 core
uniform vec3 light;
uniform sampler2DShadow shadow_map;
in vec4 shadow_pos;
in vec3 pos;
out vec3 fragColor;
void main(void)
{
  float shade = textureProj(shadow_map, shadow_pos);
  float brightness = (0.1 + 0.9 * max(light.z, 0)) * (0.7 * shade + 0.1) * (0.8 + (4 + pos.z) * 0.2);
  fragColor = vec3(brightness, brightness, brightness);
}")

(def projection (projection-matrix width height 2 5 (to-radians 90)))

(def indices [0 1 3 2 6 7 5 4 8 9 11 10])
(def vertices [-2 -2 -4, 2 -2 -4, -2 2 -4, 2 2 -4
               -1 -1 -3, 1 -1 -3, -1 1 -3, 1 1 -3
               -1 -1 -2.9, 1 -1 -2.9, -1 1 -2.9, 1 1 -2.9])
(def program-main (make-program :vertex [vertex-scene] :fragment [fragment-scene]))
(def vao (make-vertex-array-object program-main indices vertices [:point 3]))
(def light (atom 1.559))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (let [t1           (System/currentTimeMillis)
             dt           (- t1 @t0)
             rotation     (rotation-z (* 0.3 @light))
             light-vector (mmul (inverse rotation) (matrix [0 (cos @light) (sin @light)]))
             transform    (transformation-matrix rotation (matrix [0 0 0]))
             shadow       (shadow-matrices projection transform light-vector)
             shadow-map   (texture-render-depth
                            size size
                            (clear)
                            (use-program program-shadow)
                            (uniform-matrix4 program-shadow :shadow_ndc_matrix (:shadow-ndc-matrix shadow))
                            (render-quads vao))]
         (onscreen-render width height
                          (clear (matrix [0.0 0.0 0.0]))
                          (use-program program-main)
                          (uniform-sampler program-main :shadow_map 0)
                          (uniform-matrix4 program-main :projection projection)
                          (uniform-matrix4 program-main :shadow_map_matrix (:shadow-map-matrix shadow))
                          (uniform-matrix4 program-main :transform transform)
                          (uniform-vector3 program-main :light light-vector)
                          (use-textures shadow-map)
                          (render-quads vao))
         (destroy-texture shadow-map)
         (swap! light + (* 0.0005 dt))
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program-main)
(destroy-program program-shadow)

(Display/destroy)
