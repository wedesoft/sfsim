; https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/chapter27/chapter27.html
; https://github.com/LWJGL/lwjgl3/issues/558

(require '[clojure.math :refer :all])
(require '[sfsim25.model :refer :all])
(require '[sfsim25.render :refer :all])
(require '[sfsim25.matrix :refer :all])
(require '[sfsim25.quaternion :as q])
(require '[fastmath.vector :as v])
(require '[fastmath.matrix :as m])
(require '[clojure.pprint :refer (pprint)])
(import '[org.lwjgl.glfw GLFW GLFWKeyCallback])
(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer AIColor4D AIColor4D$Buffer AIMaterial AIString AITexture AIMaterialProperty AINode AIAnimation AINodeAnim])

(GLFW/glfwInit)

; (def model (atom (read-gltf "etc/gear.gltf")))
; (def model (atom (read-gltf "etc/cone.gltf")))
; (def model (atom (read-gltf "etc/venturestar.gltf")))
(def model (atom (read-gltf "test/sfsim25/fixtures/model/cube.gltf")))

(defn extract [child]
  (merge {:name (:name child)}
         (if (:children child) {:children (mapv extract (:children child))})))

(defn tree [model]
  {:root (extract (:root model))})

(pprint (tree @model))

(def duration (get-in @model [:animations "Root" :duration]))

(def h (into {} (mapcat (fn [[k v]] (map (fn [[k v]] [k (interpolate-transformation v 0)]) (:channels v))) (:animations @model))))

(swap! model apply-transforms h)

(def w 854)
(def h 480)
(def window (make-window "model" w h))
(GLFW/glfwShowWindow window)

(def vertex-uniform
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 normal;
out VS_OUT
{
  vec3 normal;
  vec3 direction;
} vs_out;
void main()
{
  vs_out.normal = mat3(transform) * normal;
  vs_out.direction = (transform * vec4(vertex, 1)).xyz;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-uniform
"#version 410 core
uniform vec3 light;
uniform vec3 diffuse_color;
in VS_OUT
{
  vec3 normal;
  vec3 direction;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float specular = pow(max(dot(reflect(light, fs_in.normal), direction), 0), 100);
  fragColor = diffuse_color * max(0, dot(light, fs_in.normal)) + specular;
}")

(def program-uniform (make-program :vertex [vertex-uniform] :fragment [fragment-uniform]))

(def vertex-textured
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 normal;
in vec2 texcoord;
out VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} vs_out;
void main()
{
  vs_out.normal = mat3(transform) * normal;
  vs_out.texcoord = texcoord;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-textured
"#version 410 core
uniform vec3 light;
uniform sampler2D colors;
in VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 color = texture(colors, fs_in.texcoord).rgb;
  fragColor = color * max(0, dot(light, fs_in.normal));
}")

(def program-textured (make-program :vertex [vertex-textured] :fragment [fragment-textured]))
(use-program program-textured)
(uniform-sampler program-textured "colors" 0)

(def vertex-rough
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 tangent;
in vec3 bitangent;
in vec3 normal;
in vec2 texcoord;
out VS_OUT
{
  mat3 surface;
  vec2 texcoord;
} vs_out;
void main()
{
  vs_out.surface = mat3(transform) * mat3(tangent, bitangent, normal);
  vs_out.texcoord = texcoord;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-rough
"#version 410 core
uniform vec3 light;
uniform sampler2D colors;
uniform sampler2D normals;
in VS_OUT
{
  mat3 surface;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 normal = 2.0 * texture(normals, fs_in.texcoord).xyz - 1.0;
  vec3 color = texture(colors, fs_in.texcoord).rgb;
  float brightness = 0.2 + 0.8 * max(0, dot(light, fs_in.surface * normal));
  fragColor = color * brightness;
}")

(def program-rough (make-program :vertex [vertex-rough] :fragment [fragment-rough]))
(use-program program-rough)
(uniform-sampler program-rough "colors" 0)
(uniform-sampler program-rough "normals" 1)

(defmulti program-selection (fn [material] [(type (:color-texture-index material)) (type (:normal-texture-index material))]))

(defmethod program-selection [nil nil] [material] program-uniform)

(defmethod program-selection [Number nil] [material] program-textured)

(defmethod program-selection [Number Number] [material] program-rough)

(defmulti render-model (fn [material] [(type (:color-texture-index material)) (type (:normal-texture-index material))]))

(defmethod render-model [nil nil] [{:keys [program transform diffuse]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (uniform-vector3 program "diffuse_color" diffuse))

(defmethod render-model [Number nil] [{:keys [program transform colors]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (use-textures {0 colors}))

(defmethod render-model [Number Number] [{:keys [program transform colors normals]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (use-textures {0 colors 1 normals}))

(def scene (atom (load-scene-into-opengl program-selection @model)))

(def projection (projection-matrix w h 0.1 150.0 (to-radians 60.0)))

(def keystates (atom {}))
(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))
(GLFW/glfwSetKeyCallback window keyboard-callback)

(def orientation (atom (q/rotation (to-radians 0) (v/vec3 0 0 1))))
(def pos (atom (v/vec3 0.0 -0.3 -2.5)))

(def gear (atom 0.0))
(def nose (atom 0.0))

(def t0 (atom (System/currentTimeMillis)))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             dx (if (@keystates GLFW/GLFW_KEY_Q) 0.01 (if (@keystates GLFW/GLFW_KEY_A) -0.01 0))
             dy (if (@keystates GLFW/GLFW_KEY_W) 0.01 (if (@keystates GLFW/GLFW_KEY_S) -0.01 0))
             dz (if (@keystates GLFW/GLFW_KEY_E) 0.01 (if (@keystates GLFW/GLFW_KEY_D) -0.01 0))
             dg (if (@keystates GLFW/GLFW_KEY_R) 0.05 (if (@keystates GLFW/GLFW_KEY_F) -0.05 0))
             dn (if (@keystates GLFW/GLFW_KEY_T) 0.05 (if (@keystates GLFW/GLFW_KEY_G) -0.05 0))
             ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb (if (@keystates GLFW/GLFW_KEY_KP_6) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_4) -0.001 0))
             rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))]
         (swap! gear + dg)
         (swap! nose + dn)
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt ra) (v/vec3 1 0 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rb) (v/vec3 0 1 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rc) (v/vec3 0 0 1)))
         (swap! pos v/add (v/mult (v/vec3 dx dy dz) dt))
         (swap! scene apply-transforms (animations-frame @scene {"NoseGear" @nose "DeployAction" @gear}))
         (onscreen-render window
                          (clear (v/vec3 0.1 0.1 0.1) 0)
                          (doseq [program [program-uniform program-textured program-rough]]
                                 (use-program program)
                                 (uniform-matrix4 program "projection" projection)
                                 (uniform-vector3 program "light" (v/normalize (v/vec3 0 5 2))))
                          (render-scene program-selection
                                        (assoc-in @scene
                                                  [:root :transform]
                                                  (transformation-matrix (quaternion->matrix @orientation) @pos))
                                        render-model))
         (GLFW/glfwPollEvents)
         (swap! t0 + dt)))

(unload-scene-from-opengl scene)
(destroy-program program-rough)
(destroy-program program-textured)
(destroy-program program-uniform)
(destroy-window window)
(GLFW/glfwTerminate)

(System/exit 0)
