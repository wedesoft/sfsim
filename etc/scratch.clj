(require '[clojure.core.async :refer (go chan <! >! <!! >!! poll! close!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.vector3 :refer (->Vector3)]
         '[sfsim25.matrix3x3 :refer (identity-matrix rotation-y)]
         '[sfsim25.matrix4x4 :refer (matrix3x3->matrix4x4 projection-matrix)]
         '[sfsim25.cubemap :refer :all]
         '[sfsim25.quadtree :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40]
        '[org.lwjgl BufferUtils])

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
    gl_TessLevelOuter[0] = 32.0;
    gl_TessLevelOuter[1] = 32.0;
    gl_TessLevelOuter[2] = 32.0;
    gl_TessLevelOuter[3] = 32.0;
    gl_TessLevelInner[0] = 32.0;
    gl_TessLevelInner[1] = 32.0;
  }
  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
  texcoord_tes[gl_InvocationID] = texcoord_tcs[gl_InvocationID];
}")

(def tes-source "#version 410 core
layout(quads, equal_spacing, ccw) in;
in mediump vec2 texcoord_tes[];
out mediump vec2 texcoord_geo;
uniform sampler2D hf;
uniform mat4 projection;
uniform mat4 transform;
void main()
{
  vec2 c = mix(texcoord_tes[0], texcoord_tes[1], gl_TessCoord.x);
  vec2 d = mix(texcoord_tes[3], texcoord_tes[2], gl_TessCoord.x);
  texcoord_geo = mix(c, d, gl_TessCoord.y);
  float s = texture(hf, texcoord_geo).g;
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  vec4 p = mix(a, b, gl_TessCoord.y);
  gl_Position = projection * transform * vec4(p.xyz * s * 6388000, 1);
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
(def-make-buffer make-byte-buffer BufferUtils/createByteBuffer)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(GL11/glClearColor 0.0 0.0 0.0 0.0)
(GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))

(Display/update)

(def vertex-shader (make-shader vertex-source GL20/GL_VERTEX_SHADER))
(def fragment-shader (make-shader fragment-source GL20/GL_FRAGMENT_SHADER))
(def tcs-shader (make-shader tcs-source GL40/GL_TESS_CONTROL_SHADER))
(def tes-shader (make-shader tes-source GL40/GL_TESS_EVALUATION_SHADER))
(def geo-shader (make-shader geo-source GL32/GL_GEOMETRY_SHADER))
(def program (make-program vertex-shader fragment-shader geo-shader tcs-shader tes-shader))

(def radius1 6378000.0)
(def radius2 6357000.0)

(def tree-state (chan 1))
(def changes (chan 1))

(def position (atom (->Vector3 0 0 (* 3 radius1))))
(def tree {})

(go
  (loop [tree (<! tree-state)]
    (when tree
      (let [increase? (partial increase-level? 33 radius1 radius2 1280 60 10 @position)
            drop-list (tiles-to-drop tree increase?)
            load-list (tiles-to-load tree increase?)
            tiles     (load-tiles-data (tiles-meta-data load-list))]
        (>! changes {:drop drop-list :load load-list :tiles tiles})
        (recur (<! tree-state))))))

(>!! tree-state tree)

(Thread/sleep 100)
(def data (poll! changes))

(defmacro with-vertex-array [vao & body]
  `(do
     (GL30/glBindVertexArray ~vao)
     (let [result# (do ~@body)]
       (GL30/glBindVertexArray 0)
       result#)))

(defmacro create-vertex-array [vao & body]
  `(let [~vao (GL30/glGenVertexArrays)]
     (with-vertex-array ~vao ~@body)))

(defn make-vertices
  [face level y x]
  (let [[a b c d] (cube-map-corners face level y x)]
    (float-array [(.x a) (.y a) (.z a) 0.0 0.0
                  (.x b) (.y b) (.z b) 1.0 0.0
                  (.x c) (.y c) (.z c) 0.0 1.0
                  (.x d) (.y d) (.z d) 1.0 1.0])))

(defn create-texture
  [varname index tex-format tex-type buffer]
  (let [texture (GL11/glGenTextures)]
    (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 index))
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL20/glUniform1i (GL20/glGetUniformLocation program varname) index)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB 33 33 0 tex-format tex-type buffer)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)
    texture))

(def tiles
  (for [[path tile] (map list (:load data) (:tiles data))]
    (create-vertex-array vao
      (let [vbo             (GL15/glGenBuffers)
            vertices-buffer (make-float-buffer (make-vertices (:face tile) (:level tile) (:y tile) (:x tile)))]
        (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
        (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW))
      (let [idx (GL15/glGenBuffers)
            indices-buffer (make-int-buffer (int-array [0 1 3 2]))]
        (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx)
        (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer GL15/GL_STATIC_DRAW))
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point"   )
                                  3 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 0 Float/BYTES))
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "texcoord")
                                  2 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 3 Float/BYTES))
      (GL20/glEnableVertexAttribArray 0)
      (GL20/glEnableVertexAttribArray 1)
      (let [pixels      (byte-array (flatten (map (fn [[b g r]] (list r g b 255)) (partition 3 (get-in tile [:colors :data])))))
            heights     (float-array (map #(/ % 6388000.0) (:scales tile)))
            texture     (create-texture "tex" 0 GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE (make-byte-buffer pixels))
            heightfield (create-texture "hf" 1 GL11/GL_LUMINANCE GL11/GL_FLOAT (make-float-buffer heights))]
        (assoc tile :vao vao :texture texture :heightfield heightfield)))))

(GL11/glEnable GL11/GL_DEPTH_TEST)

(def tree (quadtree-add (quadtree-drop tree (:drop data)) (:load data) tiles))

(GL20/glUseProgram program)

(def p (float-array (projection-matrix 640 480 6378000 (* 4 6378000) (/ (* 60 Math/PI) 180))))
(GL20/glUniformMatrix4 (GL20/glGetUniformLocation program "projection") true (make-float-buffer p))

(def t0 (System/currentTimeMillis))

(while (not (Display/isCloseRequested))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (def t1 (System/currentTimeMillis))
  (def angle (* 0.001 (- t1 t0)))
  (def t (float-array (matrix3x3->matrix4x4 (rotation-y angle) (->Vector3 0 0 (* -3 6378000)))))
  (GL20/glUniformMatrix4 (GL20/glGetUniformLocation program "transform") true (make-float-buffer t))
  (doseq [{:keys [vao texture heightfield]} tiles]
    (with-vertex-array vao
      (GL13/glActiveTexture GL13/GL_TEXTURE0)
      (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
      (GL13/glActiveTexture GL13/GL_TEXTURE1)
      (GL11/glBindTexture GL11/GL_TEXTURE_2D heightfield)
      (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
      (GL11/glDrawElements GL40/GL_PATCHES 4 GL11/GL_UNSIGNED_INT 0)))
  (Display/update))

(Display/destroy)
