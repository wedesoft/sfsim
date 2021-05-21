(require '[clojure.core.async :refer (go go-loop chan <! >! <!! >!! poll! close!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.vector3 :refer (->Vector3) :as v]
         '[sfsim25.matrix3x3 :refer (identity-matrix rotation-x rotation-y)]
         '[sfsim25.matrix4x4 :refer (matrix3x3->matrix4x4 projection-matrix)]
         '[sfsim25.cubemap :refer :all]
         '[sfsim25.quadtree :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40]
        '[org.lwjgl.input Keyboard]
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
uniform int tesselate_up;
uniform int tesselate_left;
uniform int tesselate_down;
uniform int tesselate_right;
void main(void)
{
  if (gl_InvocationID == 0) {
    if (tesselate_up != 0) {
      gl_TessLevelOuter[0] = 32.0;
    } else {
      gl_TessLevelOuter[0] = 16.0;
    };
    if (tesselate_left != 0) {
      gl_TessLevelOuter[1] = 32.0;
    } else {
      gl_TessLevelOuter[1] = 16.0;
    };
    if (tesselate_down != 0) {
      gl_TessLevelOuter[2] = 32.0;
    } else {
      gl_TessLevelOuter[2] = 16.0;
    };
    if (tesselate_right != 0) {
      gl_TessLevelOuter[3] = 32.0;
    } else {
      gl_TessLevelOuter[3] = 16.0;
    };
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
  float s = texture(hf, texcoord_geo).r;
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

(Keyboard/create)

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

(def tree-state (chan))
(def changes (chan))

(def position (atom (->Vector3 0 0 (* 3 radius1))))
(def tree (atom {}))

(def tilesize 33)

(go-loop []
  (if-let [tree (<! tree-state)]
    (let [increase? (partial increase-level? tilesize radius1 radius2 640 60 20 4 @position)]
      (>! changes (update-level-of-detail tree increase? true))
      (recur))))

(def-context-macro with-vertex-array (fn [vao] (GL30/glBindVertexArray vao)) (fn [vao] (GL30/glBindVertexArray 0)))

(def-context-create-macro create-vertex-array (fn [] (GL30/glGenVertexArrays)) 'with-vertex-array)

(def c0 (/ 0.5 tilesize))
(def c1 (- 1.0 (/ 0.5 tilesize)))

(defn make-vertices
  [face level y x]
  (let [[a b c d] (cube-map-corners face level y x)]
    (float-array [(:x a) (:y a) (:z a) c0 c0
                  (:x b) (:y b) (:z b) c1 c0
                  (:x c) (:y c) (:z c) c0 c1
                  (:x d) (:y d) (:z d) c1 c1])))

(defn create-texture
  [varname index internal-format tex-format tex-type interpolation buffer]
  (let [texture (GL11/glGenTextures)]
    (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 index))
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL20/glUniform1i (GL20/glGetUniformLocation program varname) index)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 internal-format tilesize tilesize 0 tex-format tex-type buffer)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER interpolation)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER interpolation)
    texture))

(defn load-tile-into-opengl
  [tile]
  (create-vertex-array vao
    (let [vbo             (GL15/glGenBuffers)
          vertices-buffer (make-float-buffer (make-vertices (:face tile) (:level tile) (:y tile) (:x tile)))
          idx             (GL15/glGenBuffers)
          indices-buffer  (make-int-buffer (int-array [0 2 3 1]))]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER vertices-buffer GL15/GL_STATIC_DRAW)
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER indices-buffer GL15/GL_STATIC_DRAW)
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "point"   )
                                  3 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 0 Float/BYTES))
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation program "texcoord")
                                  2 GL11/GL_FLOAT false (* 5 Float/BYTES) (* 3 Float/BYTES))
      (GL20/glEnableVertexAttribArray 0)
      (GL20/glEnableVertexAttribArray 1)
      (let [pixels      (get-in tile [:colors :data])
            heights     (:scales tile)
            texture     (create-texture "tex" 0 GL11/GL_RGB GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE GL11/GL_NEAREST (make-byte-buffer pixels))
            heightfield (create-texture "hf" 1 GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT GL11/GL_NEAREST (make-float-buffer heights))]
        (assoc tile :vao vao :vbo vbo :idx idx :texture texture :heightfield heightfield)))))

(defn unload-tile-from-opengl
  [tile]
  (with-vertex-array (:vao tile)
    (GL20/glDisableVertexAttribArray 0)
    (GL20/glDisableVertexAttribArray 1)
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (GL11/glDeleteTextures (:texture tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE1)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (GL11/glDeleteTextures (:heightfield tile))
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers (:idx tile))
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers (:vbo tile))
    (GL30/glBindVertexArray 0)
    (GL30/glDeleteVertexArrays (:vao tile))))

(GL11/glEnable GL11/GL_DEPTH_TEST)

(GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)

(GL11/glEnable GL11/GL_CULL_FACE)
(GL11/glCullFace GL11/GL_BACK)

(GL20/glUseProgram program)

(def p (float-array (vals (projection-matrix 640 480 10000 (* 4 6378000) (/ (* 60 Math/PI) 180)))))
(GL20/glUniformMatrix4 (GL20/glGetUniformLocation program "projection") true (make-float-buffer p))

(defn is-leaf?
  [node]
  (not (or (nil? node) (contains? node :0) (contains? node :1) (contains? node :2) (contains? node :3))))

(defn render-tile
  [tile]
  (with-vertex-array (:vao tile)
    (GL20/glUniform1i (GL20/glGetUniformLocation program "tesselate_up"   ) (:up    tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation program "tesselate_left" ) (:left  tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation program "tesselate_down" ) (:down  tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation program "tesselate_right") (:right tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:texture tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE1)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:heightfield tile))
    (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
    (GL11/glDrawElements GL40/GL_PATCHES 4 GL11/GL_UNSIGNED_INT 0)))

(defn render-tree
  [node]
  (if node
    (if (is-leaf? node)
      (render-tile node)
      (doseq [selector [:0 :1 :2 :3 :4 :5]]
        (render-tree (selector node))))))

(>!! tree-state @tree)

(def t0 (System/currentTimeMillis))
(while (not (Display/isCloseRequested))
  (when-let [data (poll! changes)]
    (doseq [tile (:drop data)] (unload-tile-from-opengl tile))
    (>!! tree-state (reset! tree (quadtree-update (:tree data) (:load data) load-tile-into-opengl))))
  (if (Keyboard/next)
    (println (Keyboard/getEventKey) (Keyboard/getEventKeyState)))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (let [t1 (System/currentTimeMillis)
        dt (- t1 t0)
        z  (- (* dt 200) (* 3 6378000))
        angle (* (+ (* dt 0.005) 0) (/ Math/PI 180))
        t  (float-array (vals (matrix3x3->matrix4x4 (rotation-y angle) (->Vector3 0 0 z))))]
    (reset! position (->Vector3 (* (Math/sin angle) z) 0 (* (Math/cos angle) (- z))))
    (GL20/glUniformMatrix4 (GL20/glGetUniformLocation program "transform") true (make-float-buffer t))
    (render-tree @tree)
    (Display/update)))

(Keyboard/destroy)
(Display/destroy)

(close! tree-state)
(close! changes)

(System/exit 0)
