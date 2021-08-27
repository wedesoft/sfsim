(require '[clojure.core.async :refer (go-loop chan <! >! <!! >!! poll! close!) :as a]
         '[clojure.core.matrix :refer :all]
         '[sfsim25.util :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.matrix :refer (transformation-matrix quaternion->matrix projection-matrix)]
         '[sfsim25.cubemap :refer :all]
         '[sfsim25.quadtree :refer :all]
         '[sfsim25.quaternion :as q])

(import '[org.lwjgl.opengl Pbuffer Display DisplayMode GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 Util]
        '[org.lwjgl.input Keyboard]
        '[org.lwjgl BufferUtils])


(def vertex-source "#version 410 core
in highp vec3 point;
in mediump vec2 texcoord;
in mediump vec2 ctexcoord;
out mediump vec2 texcoord_tcs;
out mediump vec2 ctexcoord_tcs;
void main()
{
  gl_Position = vec4(point, 1);
  texcoord_tcs = texcoord;
  ctexcoord_tcs = ctexcoord;
}")

(def tcs-source "#version 410 core
layout(vertices = 4) out;
in mediump vec2 texcoord_tcs[];
in mediump vec2 ctexcoord_tcs[];
out mediump vec2 texcoord_tes[];
out mediump vec2 ctexcoord_tes[];
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
  ctexcoord_tes[gl_InvocationID] = ctexcoord_tcs[gl_InvocationID];
}")

(def tes-source "#version 410 core
layout(quads, equal_spacing, ccw) in;
in mediump vec2 texcoord_tes[];
in mediump vec2 ctexcoord_tes[];
out mediump vec2 ctexcoord_geo;
out highp vec3 vertex;
uniform sampler2D hf;
uniform mat4 projection;
uniform mat4 transform;
void main()
{
  vec2 c = mix(texcoord_tes[0], texcoord_tes[1], gl_TessCoord.x);
  vec2 d = mix(texcoord_tes[3], texcoord_tes[2], gl_TessCoord.x);
  vec2 texcoord_geo = mix(c, d, gl_TessCoord.y);
  vec2 cc = mix(ctexcoord_tes[0], ctexcoord_tes[1], gl_TessCoord.x);
  vec2 cd = mix(ctexcoord_tes[3], ctexcoord_tes[2], gl_TessCoord.x);
  ctexcoord_geo = mix(cc, cd, gl_TessCoord.y);
  float s = texture(hf, texcoord_geo).r;
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  vec4 p = mix(a, b, gl_TessCoord.y);
  gl_Position = projection * transform * vec4(p.xyz * s * 6388000, 1);
  vertex = (transform * vec4(p.xyz * s * 6388000, 1)).xyz;
}")

(def geo-source "#version 410 core
layout(triangles) in;
in mediump vec2 ctexcoord_geo[3];
in highp vec3 vertex[3];
layout(triangle_strip, max_vertices = 3) out;
out mediump vec2 UV;
out highp vec3 v;
void main(void)
{
	gl_Position = gl_in[0].gl_Position;
  UV = ctexcoord_geo[0];
  v = vertex[0];
	EmitVertex();
	gl_Position = gl_in[1].gl_Position;
  UV = ctexcoord_geo[1];
  v = vertex[1];
	EmitVertex();
	gl_Position = gl_in[2].gl_Position;
  UV = ctexcoord_geo[2];
  v = vertex[2];
	EmitVertex();
	EndPrimitive();
}")

(def fragment-source "#version 410 core
in mediump vec2 UV;
in highp vec3 v;
out lowp vec3 fragColor;
uniform sampler2D tex;
uniform sampler2D normals;
uniform sampler2D water;
uniform vec3 light;
uniform mat4 transform;
void main()
{
  vec3 normal = texture(normals, UV).xyz;
  float wet = texture(water, UV).r;
  float specular = pow(max(dot((transform * vec4(reflect(light, normal), 0)).xyz, normalize(v)), 0), 50);
  float diffuse = max(dot(light, normal), 0.05);
  vec3 landColor = texture(tex, UV).rgb * diffuse;
  vec3 waterColor = vec3(0.09, 0.11, 0.34) * diffuse + 0.5 * specular;
  fragColor = mix(landColor, waterColor, wet);
}")

(Display/setTitle "scratch")
; (def desktop (Display/getDesktopDisplayMode))
; (Display/setDisplayModeAndFullscreen desktop)
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(Keyboard/create)

(def program (make-program :vertex vertex-source :tess-control tcs-source :tess-evaluation tes-source :geometry geo-source :fragment fragment-source))

(def radius1 6378000.0)
(def radius2 6357000.0)

(def tree-state (chan))
(def changes (chan))

(def position (atom (matrix [0 0 (* 3 radius1)])))
(def orientation (atom (q/->Quaternion 1 0 0 0)))

(def tree (atom {}))

(def tilesize 33)
(def ctilesize 129)

(go-loop []
  (if-let [tree (<! tree-state)]
    (let [increase? (partial increase-level? tilesize radius1 radius2 640 60 10 3 @position)]
      (>! changes (update-level-of-detail tree increase? true))
      (recur))))

(def-context-macro with-vertex-array (fn [vao] (GL30/glBindVertexArray vao)) (fn [vao] (GL30/glBindVertexArray 0)))

(def-context-create-macro create-vertex-array (fn [] (GL30/glGenVertexArrays)) 'with-vertex-array)

(def c0 (/ 0.5 tilesize))
(def c1 (- 1.0 (/ 0.5 tilesize)))

(def d0 (/ 0.5 ctilesize))
(def d1 (- 1.0 (/ 0.5 ctilesize)))

(defn make-vertices
  [face level y x]
  (let [[a b c d] (cube-map-corners face level y x)]
    (float-array [(mget a 0) (mget a 1) (mget a 2) c0 c0 d0 d0
                  (mget b 0) (mget b 1) (mget b 2) c1 c0 d1 d0
                  (mget c 0) (mget c 1) (mget c 2) c0 c1 d0 d1
                  (mget d 0) (mget d 1) (mget d 2) c1 c1 d1 d1])))

(defn create-texture
  [varname index size internal-format tex-format tex-type interpolation buffer]
  (let [texture (GL11/glGenTextures)]
    ; (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 index))
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (use-program program (uniform-sampler varname index))
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 internal-format size size 0 tex-format tex-type buffer)
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
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation (:program program) "point"   )
                                  3 GL11/GL_FLOAT false (* 7 Float/BYTES) (* 0 Float/BYTES))
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation (:program program) "texcoord")
                                  2 GL11/GL_FLOAT false (* 7 Float/BYTES) (* 3 Float/BYTES))
      (GL20/glVertexAttribPointer (GL20/glGetAttribLocation (:program program) "ctexcoord")
                                  2 GL11/GL_FLOAT false (* 7 Float/BYTES) (* 5 Float/BYTES))
      (GL20/glEnableVertexAttribArray 0)
      (GL20/glEnableVertexAttribArray 1)
      (GL20/glEnableVertexAttribArray 2)
      (use-program program (uniform-sampler :tex 0))
      (let [pixels      (:colors tile)
            heights     (:scales tile)
            normals     (:normals tile)
            water       (:water tile)
            texture     (make-rgb-texture pixels)
            heightfield (create-texture :hf 1 tilesize GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT GL11/GL_NEAREST (make-float-buffer heights))
            normal-map  (create-texture :normals 2 ctilesize GL30/GL_RGBA32F GL12/GL_BGR GL11/GL_FLOAT GL11/GL_LINEAR (make-float-buffer normals))
            water-map   (create-texture :water 3 ctilesize GL11/GL_RED GL11/GL_RED GL11/GL_UNSIGNED_BYTE GL11/GL_LINEAR (make-byte-buffer water))]
        (assoc (dissoc tile :colors :scales :normals :water)
               :vao vao :vbo vbo :idx idx :color-tex texture :height-tex heightfield :normal-tex normal-map :water-tex water-map)))))

(defn unload-tile-from-opengl
  [tile]
  (with-vertex-array (:vao tile)
    (GL20/glDisableVertexAttribArray 0)
    (GL20/glDisableVertexAttribArray 1)
    (GL20/glDisableVertexAttribArray 2)
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (destroy-texture (:color-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE1)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (GL11/glDeleteTextures (:height-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE2)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (GL11/glDeleteTextures (:normal-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE3)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
    (GL11/glDeleteTextures (:water-tex tile))
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers (:idx tile))
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
    (GL15/glDeleteBuffers (:vbo tile)))
  (GL30/glDeleteVertexArrays (:vao tile)))

(GL11/glEnable GL11/GL_DEPTH_TEST)

; (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
(GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)

(GL11/glEnable GL11/GL_CULL_FACE)
(GL11/glCullFace GL11/GL_BACK)

(GL20/glUseProgram (:program program))

(use-program program
  (uniform-matrix4 :projection (projection-matrix 640 480 10000 (* 4 6378000) (/ (* 60 Math/PI) 180))))

(defn is-leaf?
  [node]
  (not (or (nil? node) (contains? node :0) (contains? node :1) (contains? node :2) (contains? node :3))))

(defn render-tile
  [tile]
  (with-vertex-array (:vao tile)
    (GL20/glUniform1i (GL20/glGetUniformLocation (:program program) "tesselate_up"   ) (:sfsim25.quadtree/up    tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation (:program program) "tesselate_left" ) (:sfsim25.quadtree/left  tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation (:program program) "tesselate_down" ) (:sfsim25.quadtree/down  tile))
    (GL20/glUniform1i (GL20/glGetUniformLocation (:program program) "tesselate_right") (:sfsim25.quadtree/right tile))
    (use-textures (:color-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE1)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:height-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE2)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:normal-tex tile))
    (GL13/glActiveTexture GL13/GL_TEXTURE3)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D (:water-tex tile))
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

(def keystates (atom {}))

(defn unload-tiles-from-opengl
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))

(defn load-tiles-into-opengl
  [tree paths]
  (quadtree-update tree paths load-tile-into-opengl))

(def light (atom 0))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
  (when-let [data (poll! changes)]
    (unload-tiles-from-opengl (:drop data))
    (>!! tree-state (reset! tree (load-tiles-into-opengl (update-tree-parents (:tree data) (:load data)) (:load data)))))
  (while (Keyboard/next)
    (let [state      (Keyboard/getEventKeyState)
          event-key  (Keyboard/getEventKey)]
      (swap! keystates assoc event-key state)))
  (GL11/glClearColor 0.0 0.0 0.0 0.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
  (let [t1 (System/currentTimeMillis)
        dt (- t1 @t0)
        ra (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
        rb (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
        rc (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
        l  (if (@keystates Keyboard/KEY_ADD) 0.001 (if (@keystates Keyboard/KEY_SUBTRACT) -0.001 0))
        v  (if (@keystates Keyboard/KEY_PRIOR) 1000 (if (@keystates Keyboard/KEY_NEXT) -1000 0))]
    (swap! t0 + dt)
    (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
    (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
    (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
    (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
    (swap! light + (* l dt))
    (use-program program
      (uniform-matrix4 :transform (inverse (transformation-matrix (quaternion->matrix @orientation) @position)))
      (uniform-vector3 :light (matrix [(Math/cos @light) (Math/sin @light) 0])))
    (render-tree @tree)
    (GL11/glFlush)
    (Display/update)))

(destroy-program program)

(Keyboard/destroy)
(Display/destroy)

(close! tree-state)
(close! changes)

(System/exit 0)

; --------------------------------------------------------------------------------

(require '[clojure.core.matrix :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.rgb :refer (->RGB)]
         '[sfsim25.matrix :refer (transformation-matrix quaternion->matrix projection-matrix)]
         '[sfsim25.quaternion :as q])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])


(def vertex-source "#version 130
in highp vec3 point;
out highp vec3 pos;
out highp vec3 orig;
uniform mat4 projection;
uniform mat4 itransform;
void main()
{
  gl_Position = projection * vec4(point, 1);
  pos = (itransform * vec4(point, 0)).xyz;
  orig = (itransform * vec4(0, 0, 0, 1)).xyz;
}")

(def fragment-source "#version 130
out lowp vec3 fragColor;
in highp vec3 pos;
in highp vec3 orig;
uniform vec3 light;
uniform float rayleigh_scatter_strength;
uniform float mie_scatter_strength;
uniform float g;
uniform sampler1D density_texture;
uniform sampler2D depth_texture;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction) {
  vec3 offset = origin - centre;
  float direction_sqr = dot(direction, direction);
  float discriminant = pow(dot(direction, offset), 2) - direction_sqr * (dot(offset, offset) - radius * radius);
  if (discriminant > 0) {
    float length2 = sqrt(discriminant) / direction_sqr;
    float middle = -dot(direction, offset) / direction_sqr;
    vec2 result = vec2(middle - length2, 2 * length2);
    if (result.x < 0) {
      result.y = max(0, result.y + result.x);
      result.x = 0;
    };
    return result;
  } else {
    return vec2(0, 0);
  }
}

vec3 scale(vec3 v) {
  return vec3(v.x, v.y, v.z * 6378000 / 6357000);
}

float density(vec3 point) {
  vec3 centre = vec3(0, 0, 0);
  float height = distance(scale(point), centre) - 6378000;
  float height01 = height / 80000;
  return texture(density_texture, height01).r;
}

float optical_depth(vec3 origin, vec3 direction)
{
  vec3 centre = vec3(0, 0, 0);
  float dist = distance(scale(origin), centre);
  float height = dist - 6378000;
  float height01 = height / 80000;
  vec3 normal = scale(origin) / dist;
  float cos_angle = dot(normal, scale(direction) / length(scale(direction)));
  float cos_angle01 = 0.5 + 0.5 * cos_angle;
  return texture(depth_texture, vec2(height01, cos_angle01)).r;
}

float optical_depth_ltd(vec3 origin, vec3 direction, float ray_length)
{
  vec3 point = origin + direction * ray_length;
  vec3 centre = vec3(0, 0, 0);
  if (dot(scale(direction), scale(origin) - centre) > 0) {
    return max(optical_depth(origin, direction) - optical_depth(point, direction), 0);
  } else {
    return max(optical_depth(point, -direction) - optical_depth(origin, -direction), 0);
  }
}

vec3 calculate_light(vec3 origin, vec3 direction, float ray_length)
{
  int num_points = 10;
  float step_size = ray_length / num_points;
  vec3 point = origin + 0.5 * step_size * direction;
  vec3 scatter = vec3(0, 0, 0);
  vec3 wavelength = vec3(700, 530, 440);
  vec3 rayleigh_scatter_coeffs = pow(400 / wavelength, vec3(4, 4, 4)) * rayleigh_scatter_strength;
  for (int i=0; i<num_points; i++) {
    float sunray_depth = optical_depth(point, light);
    float view_depth = optical_depth_ltd(point, -direction, step_size * i);
    float cos_theta = dot(direction, light);
    float phase = (3.0 * (1 - g * g)) / (2.0 * (2.0 + g * g)) * (1.0 + cos_theta * cos_theta) / (1 + g * g - 2 * g * cos_theta);
    vec3 rayleigh_transmittance = exp(-(sunray_depth + view_depth) * rayleigh_scatter_coeffs);
    float mie_transmittance = exp(-(sunray_depth + view_depth) * mie_scatter_strength) * phase;
    float point_density = density(point);
    scatter += point_density * rayleigh_transmittance * rayleigh_scatter_coeffs * step_size;
    scatter += point_density * mie_transmittance * mie_scatter_strength * step_size;
    point += direction * step_size;
  };
  return scatter;
}

void main()
{
  vec3 direction = normalize(pos);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), 6378000 + 80000, scale(orig), scale(direction));
  vec2 planet = ray_sphere(vec3(0, 0, 0), 6378000, scale(orig), scale(direction));
  vec3 bg;
  if (planet.x > 0) {
    vec3 wavelength = vec3(700, 530, 440);
    vec3 rayleigh_scatter_coeffs = pow(400 / wavelength, vec3(4, 4, 4)) * rayleigh_scatter_strength;
    vec3 point = orig + planet.x * direction;
    float view_depth = optical_depth(point, light);
    vec3 rayleigh_transmittance = exp(-view_depth * rayleigh_scatter_coeffs);
    bg = max(0.5 * dot(point / 6378000, light), 0.0) * rayleigh_transmittance;
    atmosphere.y = planet.x - atmosphere.x;
  } else {
    if (dot(light, direction) > 0) {
      float b = pow(dot(light, direction), 8000);
      bg = vec3(b, b, b);
    } else {
      bg = vec3(0, 0, 0);
    }
  };
  if (atmosphere.y > 0) {
    vec3 point = orig + direction * atmosphere.x;
    vec3 scatter = calculate_light(point, direction, atmosphere.y);
    fragColor = scatter + (1 - scatter) * bg;
  } else {
    fragColor = bg;
  }
}")

(Display/setTitle "atmosphere")
(def desktop (DisplayMode. 1280 720))
(Display/setDisplayMode desktop)
(Display/create (PixelFormat. 24, 0, 24, 0, 1))
(Keyboard/create)

(def program (make-program :vertex vertex-source :fragment fragment-source))

(def indices [0 1 3 2])
(def vertices (map #(* % 4 6378000) [-1 -1 -1, 1 -1 -1, -1  1 -1, 1  1 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) 10000 (* 4 6378000) (/ (* 60 Math/PI) 180)))

(def density-texture (make-float-texture-1d (air-density-table 1.0 256 80000 8429)))
(def depth-texture (make-float-texture-2d (optical-depth-table 256 256 1.0 6378000 80000 8429 20)))

(def position (atom (matrix [0 (* -3 6378000) (* 0.5 6378000)])))
(def orientation (atom (q/rotation (/ Math/PI 2) (matrix [1 0 0]))))

(def light (atom -1.4))
(def keystates (atom {}))
(def rayleigh-scatter-strength (atom 0.00009))
(def mie-scatter-strength (atom 0.0000002))
(def g (atom 0.9))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
  (while (Keyboard/next)
    (let [state      (Keyboard/getEventKeyState)
          event-key  (Keyboard/getEventKey)]
      (swap! keystates assoc event-key state)))
  (let [t1 (System/currentTimeMillis)
        dt (* 0.001 (- t1 @t0))
        ra (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
        rb (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
        rc (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
        v  (if (@keystates Keyboard/KEY_PRIOR) 1000 (if (@keystates Keyboard/KEY_NEXT) -1000 0))
        d  (if (@keystates Keyboard/KEY_Q) 0.0001 (if (@keystates Keyboard/KEY_A) -0.0001 0))
        s  (if (@keystates Keyboard/KEY_W) 1e-7 (if (@keystates Keyboard/KEY_S) -1e-7 0))
        m  (if (@keystates Keyboard/KEY_E) 1e-8 (if (@keystates Keyboard/KEY_D) -1e-8 0))
        l  (if (@keystates Keyboard/KEY_ADD) 0.0006 (if (@keystates Keyboard/KEY_SUBTRACT) -0.0006 0))]
    (swap! t0 + dt)
    (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
    (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
    (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
    (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
    (swap! g + (* d dt))
    (swap! rayleigh-scatter-strength + (* s dt))
    (swap! mie-scatter-strength + (* m dt))
    (swap! light + (* l dt)))
  (onscreen-render (.getWidth desktop) (.getHeight desktop)
    (use-program program
      (uniform-matrix4 :projection projection)
      (uniform-sampler :density_texture 0)
      (uniform-sampler :depth_texture 1)
      (uniform-matrix4 :itransform (transformation-matrix (quaternion->matrix @orientation) @position))
      (uniform-float :rayleigh_scatter_strength @rayleigh-scatter-strength)
      (uniform-float :mie_scatter_strength @mie-scatter-strength)
      (uniform-float :g @g)
      (uniform-vector3 :light (matrix [0 (Math/cos @light) (Math/sin @light)])))
    (use-textures density-texture depth-texture)
    (clear (->RGB 0 0 0))
    (render-quads vao)
    (println "rayleigh:" @rayleigh-scatter-strength "mie:" @mie-scatter-strength "g:" @g)))

(destroy-texture depth-texture)
(destroy-texture density-texture)
(destroy-vertex-array-object vao)
(destroy-program program)

(Keyboard/destroy)
(Display/destroy)
