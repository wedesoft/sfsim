; clojure -cp /usr/share/java/lwjgl.jar raw-opengl.clj
(ns raw-opengl
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL15 GL20 GL30]
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

(def pixels
  (float-array [0.0 0.0 1.0
                0.0 1.0 0.0
                1.0 0.0 0.0
                1.0 1.0 1.0]))

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

(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer BufferUtils/createIntBuffer)

(Display/setTitle "mini")
(Display/setDisplayMode (DisplayMode. 320 240))
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

(def float-size 4)
(GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point"   ) 3 GL11/GL_FLOAT false (* 5 float-size) (* 0 float-size))
(GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "texcoord") 2 GL11/GL_FLOAT false (* 5 float-size) (* 3 float-size))
(GL20/glEnableVertexAttribArray 0)
(GL20/glEnableVertexAttribArray 1)

(def tex (GL11/glGenTextures))
(GL13/glActiveTexture GL13/GL_TEXTURE0)
(GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
(GL20/glUniform1i (GL20/glGetUniformLocation program "tex") 0)
(def pixel-buffer (make-float-buffer pixels))
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB 2 2 0 GL12/GL_BGR GL11/GL_FLOAT pixel-buffer)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)
(GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)

(while (not (Display/isCloseRequested))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (GL20/glUseProgram program)
  (GL11/glDrawElements GL11/GL_TRIANGLES 3 GL11/GL_UNSIGNED_INT 0)
  (Display/update)
  (Thread/sleep 40))

(GL20/glDisableVertexAttribArray 1)
(GL20/glDisableVertexAttribArray 0)

(GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
(GL11/glDeleteTextures tex)

(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
(GL15/glDeleteBuffers idx)

(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
(GL15/glDeleteBuffers vbo)

(GL30/glBindVertexArray 0)
(GL30/glDeleteVertexArrays vao)

(GL20/glDetachShader program vertex-shader)
(GL20/glDetachShader program fragment-shader)
(GL20/glDeleteProgram program)
(GL20/glDeleteShader vertex-shader)
(GL20/glDeleteShader fragment-shader)

(Display/destroy)
