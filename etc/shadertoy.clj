(import '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.opengl GL GL11 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils]
        )

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(def width 1920)
(def height 1080)
(def window (GLFW/glfwCreateWindow width height "Shadertoy" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwSwapInterval 1)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def vertex-source "#version 130
in mediump vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-source "#version 130
out mediump vec4 fragColor;
void mainImage(out vec4 fragColor, in vec2 fragCoord);
void main()
{
  mainImage(fragColor, gl_FragCoord.xy);
}")

; parse first command line argument
(def shadertoy-source (slurp (-> *command-line-args* first)))

(def vertices
  (float-array [ 1.0  1.0 0.0
                -1.0  1.0 0.0
                -1.0 -1.0 0.0
                 1.0 -1.0 0.0]))

(def indices
  (int-array [0 1 2 3]))


(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer BufferUtils/createIntBuffer)


(defn make-shader [source shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))

(defn make-program [vertex-shader & fragment-shaders]
  (let [program (GL20/glCreateProgram)]
    (GL20/glAttachShader program vertex-shader)
    (doseq [fragment-shader fragment-shaders]
           (GL20/glAttachShader program fragment-shader)
           (GL20/glDeleteShader fragment-shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    program))

(def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def shadertoy-shader (make-shader shadertoy-source GL30/GL_FRAGMENT_SHADER))
(def program (make-program vertex-shader fragment-shader shadertoy-shader))

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

(GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point"   ) 3 GL11/GL_FLOAT false (* 3 Float/BYTES) (* 0 Float/BYTES))
(GL20/glEnableVertexAttribArray 0)

(GL20/glUseProgram program)

(GL20/glUniform2f (GL20/glGetUniformLocation program "iResolution") width height)

(def t (atom 0.0))

(while (not (GLFW/glfwWindowShouldClose window))
  (GL20/glUniform1f (GL20/glGetUniformLocation program "iTime") @t)
  (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0)
  (GLFW/glfwSwapBuffers window)
  (GLFW/glfwPollEvents)
  (GLFW/glfwWaitEventsTimeout 0.04)
  (swap! t + 0.04))

(GL20/glDeleteProgram program)
(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
