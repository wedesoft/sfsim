; clojure -cp /usr/share/java/lwjgl.jar raw-opengl.clj<Paste>
(ns raw-opengl
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Display GL11 GL15 GL20 GL30]
           [org.lwjgl.input Keyboard]))

(def vertex-source "#version 130
in mediump vec3 point;
in mediump vec2 texcoord;
out mediump vec2 UV;
void main()
{
  gl_Position = vec4(point, 1);
  UV = texcoord;
}")

(def fragment-source "#version 130
in mediump vec2 UV;
out mediump vec3 fragColor;
uniform sampler2D tex;
void main()
{
  fragColor = texture(tex, UV).rgb;
}")

(def vertices
  (float-array [ 0.5  0.5 0.0 1.0 1.0
                -0.5  0.5 0.0 0.0 1.0
                -0.5 -0.5 0.0 0.0 0.0]))

(def indices
  (int-array [0 1 2]))

(defn make-shader [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (if (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))

(defn make-program [vertex-shader fragment-shader]
  (let [program (GL20/glCreateProgram)]
    (GL20/glAttachShader program vertex-shader)
    (GL20/glAttachShader program fragment-shader)
    (GL20/glLinkProgram program)
    (if (zero? (GL20/glGetShaderi program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog program 1024))))
    program))

(defn make-float-buffer [data]
  (let [buffer (BufferUtils/createFloatBuffer (count data))]
    (.put buffer data)
    (.flip buffer)
    buffer))

(defn make-int-buffer [data]
  (let [buffer (BufferUtils/createIntBuffer (count data))]
    (.put buffer data)
    (.flip buffer)
    buffer))

(Display/setTitle "mini")
(Display/create)

(def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def program (make-program vertex-shader fragment-shader))

(def vao (GL30/glGenVertexArrays))
(GL30/glBindVertexArray vao)

(def vbo (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
(def vertices-buffer (make-float-buffer vertices))
(GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)

(def idx (GL15/glGenBuffers))
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx)
(def indices-buffer (make-int-buffer indices))
(GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer GL15/GL_STATIC_DRAW)

(GL11/glGetError)

(GL20/glGetAttribLocation program "point")
