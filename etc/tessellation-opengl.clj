; clojure -cp /usr/share/java/lwjgl.jar raw-opengl.clj
(ns tessellation-opengl
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40]))

(def vertex-source "#version 410 core
in mediump vec3 point;
in mediump vec2 texcoord;
out mediump vec2 texcoord_tcs;
void main()
{
  gl_Position = vec4(point, 1);
  texcoord_tcs = texcoord;
}")

(def tcs-source "#version 410 core
layout(vertices = 4) out;
in mediump vec2 texcoord_tcs[];
out mediump vec2 texcoord_tes[];
void main(void)
{
  if (gl_InvocationID == 0) {
    if (texcoord_tcs[3].x != 1)
      gl_TessLevelOuter[0] = 4.0;
    else
      gl_TessLevelOuter[0] = 2.0;
    if (texcoord_tcs[0].y != 1)
      gl_TessLevelOuter[1] = 4.0;
    else
      gl_TessLevelOuter[1] = 2.0;
    if (texcoord_tcs[1].x != 0)
      gl_TessLevelOuter[2] = 4.0;
    else
      gl_TessLevelOuter[2] = 2.0;
    if (texcoord_tcs[2].y != 0)
      gl_TessLevelOuter[3] = 4.0;
    else
      gl_TessLevelOuter[3] = 2.0;
    gl_TessLevelInner[0] = 4.0;
    gl_TessLevelInner[1] = 4.0;
  }
  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
  texcoord_tes[gl_InvocationID] = texcoord_tcs[gl_InvocationID];
}")

(def tes-source "#version 410 core
layout(quads, equal_spacing, ccw) in;
in mediump vec2 texcoord_tes[];
out mediump vec2 texcoord_geo;
uniform sampler2D hf;
void main()
{
  vec2 c = mix(texcoord_tes[0], texcoord_tes[1], gl_TessCoord.x);
  vec2 d = mix(texcoord_tes[3], texcoord_tes[2], gl_TessCoord.x);
  texcoord_geo = mix(c, d, gl_TessCoord.y);
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  float height = texture(hf, texcoord_geo).g;
  gl_Position = mix(a, b, gl_TessCoord.y) + vec4(0, height, 0, 0);
}")

(def geo-source "#version 410 core
layout(triangles) in;
in mediump vec2 texcoord_geo[3];
layout(triangle_strip, max_vertices = 3) out;
out mediump vec2 UV;
void main(void)
{
	gl_Position = gl_in[0].gl_Position;
  UV = texcoord_geo[0];
	EmitVertex();
	gl_Position = gl_in[1].gl_Position;
  UV = texcoord_geo[1];
	EmitVertex();
	gl_Position = gl_in[2].gl_Position;
  UV = texcoord_geo[2];
	EmitVertex();
	EndPrimitive();
}")

(def fragment-source "#version 410 core
in mediump vec2 UV;
out mediump vec3 fragColor;
uniform sampler2D tex;
void main()
{
  fragColor = texture(tex, UV).rgb;
}")

(def vertices
  (float-array [ 0.5  0.5 0.0 1.0 1.0
                 0.0  0.5 0.0 0.5 1.0
                -0.5  0.5 0.0 0.0 1.0
                 0.5  0.0 0.0 1.0 0.5
                 0.0  0.0 0.0 0.5 0.5
                -0.5  0.0 0.0 0.0 0.5
                 0.5 -0.5 0.0 1.0 0.0
                 0.0 -0.5 0.0 0.5 0.0
                -0.5 -0.5 0.0 0.0 0.0]))

(def indices
  (int-array [0 1 4 3
              1 2 5 4
              3 4 7 6
              4 5 8 7]))

(def pixels
  (float-array [0.0 0.0 1.0 0.0 1.0 0.0 1.0 0.0 0.0
                0.0 1.0 0.0 1.0 0.0 0.0 0.0 0.0 1.0
                1.0 0.0 0.0 0.0 0.0 1.0 0.0 1.0 0.0]))

(def heights
  (float-array [0.1 0.2 0.3
                0.1 0.2 0.3
                0.1 0.2 0.3]))

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
(Display/setDisplayMode (DisplayMode. 640 480))
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

(GL20/glUseProgram program)

(def hf (GL11/glGenTextures))
(GL13/glActiveTexture GL13/GL_TEXTURE0)
; (GL11/glEnable GL11/GL_TEXTURE_2D)
(GL11/glBindTexture GL11/GL_TEXTURE_2D hf)
(GL20/glUniform1i (GL20/glGetUniformLocation program "hf") 0)
(def height-buffer (make-float-buffer heights))
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB 3 3 0 GL11/GL_LUMINANCE GL11/GL_FLOAT height-buffer)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)

(def tex (GL11/glGenTextures))
(GL13/glActiveTexture (inc GL13/GL_TEXTURE0))
; (GL11/glEnable GL11/GL_TEXTURE_2D)
(GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
(GL20/glUniform1i (GL20/glGetUniformLocation program "tex") 1)
(def pixel-buffer (make-float-buffer pixels))
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB 3 3 0 GL12/GL_BGR GL11/GL_FLOAT pixel-buffer)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)
;(GL30/glGenerateMipmap GL11/GL_TEXTURE_2D)

(GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)

(while (not (Display/isCloseRequested))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  ;(GL20/glUseProgram program)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
  (GL11/glDrawElements GL40/GL_PATCHES 16 GL11/GL_UNSIGNED_INT 0)
  (Display/update)
  (Thread/sleep 40))

(GL20/glDisableVertexAttribArray 1)
(GL20/glDisableVertexAttribArray 0)

(GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
(GL11/glDeleteTextures tex)

(GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
(GL11/glDeleteTextures hf)

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
