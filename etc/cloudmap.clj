(require '[clojure.core.matrix :refer (matrix eseq mmul)]
         '[clojure.math :refer (to-radians)]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.render :refer :all])
(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL20 GL30]
        '[mikera.matrixx Matrix])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 400))
(Display/create)

; https://www.khronos.org/opengl/wiki/Cubemap_Texture

(def image {:width 3 :height 3 :data (float-array [0 1 0 1 0 1 0 1 0])})
(def tex {:texture (GL11/glGenTextures) :target GL13/GL_TEXTURE_CUBE_MAP :width 3 :height 3 :depth 6})
(GL11/glBindTexture GL13/GL_TEXTURE_CUBE_MAP (:texture tex))
(def buffer (make-float-buffer (:data image)))
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_X 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_Y 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_Y 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_Z 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_Z 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)

(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE)

(def vertex
"#version 410 core
uniform float aspect;
in vec2 point;
out VS_OUT
{
  vec2 point;
} vs_out;
void main()
{
  gl_Position = vec4(point, 0, 1);
  vs_out.point = point * vec2(aspect, 1);
}")

(def fragment
"#version 410 core
uniform samplerCube cubemap;
uniform mat3 rotation;
in VS_OUT
{
  vec2 point;
} fs_in;
out vec3 fragColor;
void main()
{
  if (length(fs_in.point) <= 1) {
    float z = -sqrt(1 - fs_in.point.x * fs_in.point.x - fs_in.point.y * fs_in.point.y);
    vec3 pos = vec3(fs_in.point, z);
    float v = texture(cubemap, rotation * pos).r;
    fragColor = vec3(v, v, v);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def program (make-program :vertex [vertex] :fragment [fragment]))
(use-program program)
(uniform-sampler program :cubemap 0)
(uniform-float program :aspect (/ (Display/getWidth) (Display/getHeight)))
(use-textures tex)

(def indices [0 1 3 2])
(def vertices [-1 -1, 1 -1, -1 1, 1  1])
(def vao (make-vertex-array-object program indices vertices [:point 2]))

(def t0 (atom (System/currentTimeMillis)))
(def angle (atom 0.0))
(while (not (Display/isCloseRequested))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)]
         (swap! angle + (* 0.001 dt))
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 0 0]))
                          (uniform-matrix3 program :rotation (mmul (rotation-y @angle) (rotation-x (to-radians 30))))
                          (render-quads vao))
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)
(destroy-texture tex)
(Display/destroy)
