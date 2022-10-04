(require '[clojure.math :refer (PI to-radians)]
         '[clojure.core.matrix :refer (matrix identity-matrix)]
         '[sfsim25.render :refer :all]
         '[sfsim25.util :refer :all]
         '[sfsim25.matrix :refer :all])

(import '[org.lwjgl.opengl Pbuffer Display DisplayMode PixelFormat GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL43 GL45]
        '[org.lwjgl BufferUtils])

(def size 256)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. size size))
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
uniform mat4 projection;
in vec3 point;
out vec4 pos;
void main(void)
{
  gl_Position = projection * vec4(point, 1);
  pos = vec4(point, 1);
}")

(def fragment-scene
"#version 410 core
uniform vec3 light;
uniform mat4 shadow_map_matrix;
// uniform sampler2DShadow shadow_map;
uniform sampler2D shadow_map;
in vec4 pos;
out vec3 fragColor;
void main(void)
{
  vec4 pos2 = shadow_map_matrix * pos;
  //float shade = textureProj(shadow_map, pos2);
  //float brightness = max(light.z, 0) * (0.5 * shade + 0.1) + 0.1 * (4 + pos.z);
  vec2 p = pos2.xy / pos2.w;
  p.y = 1.0 - p.y;
  float brightness = texture(shadow_map, p).r * 0.8 + 0.2 + 0.1 * (4 + pos.z);
  float red = 0;
  if (p.x < 0 || p.x > 1 || p.y < 0 || p.y > 1)
    red = 1;
  fragColor = vec3(red, brightness, brightness);
}")

(def projection (projection-matrix size size 2 5 (to-radians 60)))
(def transform (identity-matrix 4))
(def light-vector (normalize (matrix [1 1 2])))
(def shadow (shadow-matrices projection transform light-vector))

(def indices [0 1 3 2 4 5 7 6])
(def vertices [-2 -2 -4, 2 -2 -4, -2 2 -4, 2 2 -4
               -1 -1 -3, 1 -1 -3, -1 1 -3, 1 1 -3])
(def program-main (make-program :vertex [vertex-scene] :fragment [fragment-scene]))
(def vao (make-vertex-array-object program-main indices vertices [:point 3]))

(def shadow-map (texture-render-depth
                  size size
                  (clear)
                  (use-program program-shadow)
                  (uniform-matrix4 program-shadow :shadow_ndc_matrix (:shadow-ndc-matrix shadow))
                  (render-quads vao)))

(show-floats
 (with-texture (:target shadow-map) (:texture shadow-map)
   (let [buf  (BufferUtils/createFloatBuffer (* size size))
         data (float-array (* size size))]
     (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
     (.get buf data)
     {:width size :height size :data data})))


(onscreen-render size size
                 (clear (matrix [0.0 0.0 0.0]))
                 (use-program program-main)
                 (uniform-sampler program-main :shadow_map 0)
                 (uniform-matrix4 program-main :projection projection)
                 (uniform-matrix4 program-main :shadow_map_matrix (:shadow-map-matrix shadow))
                 (uniform-vector3 program-main :light light-vector)
                 (use-textures shadow-map)
                 (render-quads vao))

(destroy-texture shadow-map)

(destroy-vertex-array-object vao)
(destroy-program program-main)

(Display/destroy)
