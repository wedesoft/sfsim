; clojure -cp /usr/share/java/lwjgl.jar raw-opengl.clj
(ns raw-opengl
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40]))

(def vertex-source "#version 410 core
in mediump vec3 point;
in mediump vec2 texcoord;
out mediump vec2 UV;
void main()
{
  gl_Position = vec4(point, 1);
  UV = texcoord;
}")

(def fragment-source "#version 410 core
in mediump vec2 UV;
out mediump vec3 fragColor;
uniform sampler2D tex;
void main()
{
  fragColor = vec3(1, 1, 1); // texture(tex, UV).rgb;
}")

(def tcs-source "#version 410 core
layout(vertices = 4) out;
void main(void)
{
  if (gl_InvocationID == 0) {
    gl_TessLevelOuter[0] = 2.0;
    gl_TessLevelOuter[1] = 3.0;
    gl_TessLevelOuter[2] = 4.0;
    gl_TessLevelInner[0] = 5.0;
  }
  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
}")

(def tes-source "#version 410 core
layout(triangles, equal_spacing, ccw) in;
void main()
{
  gl_Position.xyzw = gl_in[0].gl_Position.xyzw * gl_TessCoord.x +
                     gl_in[1].gl_Position.xyzw * gl_TessCoord.y +
                     gl_in[2].gl_Position.xyzw * gl_TessCoord.z;
}")

(def geo-source "#version 410 core
layout(triangles, invocations = 1) in;
layout(line_strip, max_vertices = 4) out;
void main(void)
{
	gl_Position = gl_in[0].gl_Position;
	EmitVertex();	
	gl_Position = gl_in[1].gl_Position;
	EmitVertex();
	gl_Position = gl_in[2].gl_Position;
	EmitVertex();
	EndPrimitive();
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

(defn make-program [& shaders]
  (let [program (GL20/glCreateProgram)]
    (doseq [shader shaders] (GL20/glAttachShader program shader))
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
(def tcs-shader (make-shader tcs-source GL40/GL_TESS_CONTROL_SHADER))
(def tes-shader (make-shader tes-source GL40/GL_TESS_EVALUATION_SHADER))
(def geo-shader (make-shader geo-source GL32/GL_GEOMETRY_SHADER))
(def program (make-program vertex-shader fragment-shader geo-shader tcs-shader tes-shader))

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

(GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point"   ) 3 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 0 Float/BYTES))
(GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "texcoord") 2 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 3 Float/BYTES))
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

(GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

(while (not (Display/isCloseRequested))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (GL20/glUseProgram program)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 3)
  (GL11/glDrawElements GL40/GL_PATCHES 3 GL11/GL_UNSIGNED_INT 0)
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
