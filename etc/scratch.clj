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

(def model (atom (read-gltf "etc/gear.gltf")))

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
  (use-textures colors))

(defmethod render-model [Number Number] [{:keys [program transform colors normals]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (use-textures colors normals))

(def scene (atom (load-scene-into-opengl program-selection @model)))

(def projection (projection-matrix w h 0.01 15.0 (to-radians 60.0)))

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
             dx (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
             dy (if (@keystates GLFW/GLFW_KEY_W) 0.001 (if (@keystates GLFW/GLFW_KEY_S) -0.001 0))
             dz (if (@keystates GLFW/GLFW_KEY_E) 0.001 (if (@keystates GLFW/GLFW_KEY_D) -0.001 0))
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
; ------------------------------------------------------------------------------

(require '[clojure.reflect :as r]
         '[clojure.pprint :as p]
         '[clojure.math :as m]
         '[fastmath.vector :as v]
         '[sfsim25.render :refer :all]
         '[sfsim25.util :refer :all]
         '[sfsim25.matrix :refer (projection-matrix transformation-matrix quaternion->matrix)]
         '[sfsim25.quaternion :as q])

(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer AIColor4D AIColor4D$Buffer AIMaterial AIString AITexture AIMaterialProperty AINode AIAnimation AINodeAnim])
(import '[org.lwjgl PointerBuffer])
(import '[org.lwjgl.stb STBImage])
(import '[org.lwjgl.glfw GLFW GLFWKeyCallback])
(import '[org.lwjgl.opengl GL11 GL30])

(defn all-methods [x]
  (->> x r/reflect
       :members
       (filter :return-type)
       (map :name)
       sort
       (map #(str "." %) )
       distinct
       (map symbol)))

(all-methods Assimp)

(def scene (Assimp/aiImportFile "etc/test.gltf" (bit-or Assimp/aiProcess_Triangulate Assimp/aiProcess_CalcTangentSpace)))
(.dataString (.mName scene))
(.mNumMeshes scene)

(def root (.mRootNode scene))
(.dataString (.mName root))
(.mNumChildren root)
(map #(.dataString (.mName (AINode/create ^long (.get (.mChildren root) %)))) (range (.mNumChildren root)))
(def child (AINode/create ^long (.get (.mChildren root) 0)))
(.dataString (.mName child))
(.mNumChildren child)
(def child2 (AINode/create ^long (.get (.mChildren child) 0)))
(.dataString (.mName child2))
(.mNumChildren child2)
(def child3 (AINode/create ^long (.get (.mChildren child2) 0)))
(.dataString (.mName child3))
(def m (.mTransformation child))
(.get (.mMeshes child) 0)
(.mNumBones mesh)

(def buffer (.mMeshes scene))
(.limit buffer)

(def mesh (AIMesh/create ^long (.get buffer 0)))
(.dataString (.mName mesh))
(.mNumFaces mesh)
(.mNumVertices mesh)
(.mMaterialIndex mesh)


(.mNumAnimations scene)
(map #(.dataString (.mName (AIAnimation/create ^long (.get (.mAnimations scene) %)))) (range (.mNumAnimations scene)))
(def animation (AIAnimation/create ^long (.get (.mAnimations scene) 1)))
(.dataString (.mName animation))

(/ (.mDuration animation) (.mTicksPerSecond animation))
(/ 100.0 24.0)
(.mNumChannels animation)
(map #(.dataString (.mNodeName (AINodeAnim/create ^long (.get (.mChannels animation) %)))) (range (.mNumChannels animation)))

(def na1 (AINodeAnim/create ^long (.get (.mChannels animation) 0)))
(def na2 (AINodeAnim/create ^long (.get (.mChannels animation) 1)))
(.dataString (.mNodeName na1))
(.dataString (.mNodeName na2))

(.mNumPositionKeys na1)
(.mNumRotationKeys na1)
(.mNumScalingKeys na1)
(.mNumPositionKeys na2)
(.mNumRotationKeys na2)

(defn ai-vector [v] (v/vec3 (.x v) (.y v) (.z v)))
(defn ai-quaternion [q] (q/->Quaternion (.w q) (.x q) (.y q) (.z q)))

(all-methods (.get (.mPositionKeys na1) 0))
(.mTime (.get (.mPositionKeys na1) 0))
(.mTime (.get (.mPositionKeys na1) 1))
(.mTime (.get (.mPositionKeys na1) 100))
(ai-quaternion (.mValue (.get (.mRotationKeys na1) 0)))
(ai-vector (.mValue (.get (.mRotationKeys na2) 0)))
(ai-vector (.mValue (.get (.mRotationKeys na2) 1)))
(ai-vector (.mValue (.get (.mPositionKeys na1) 0)))
(ai-vector (.mValue (.get (.mPositionKeys na1) 1)))
(ai-vector (.mValue (.get (.mPositionKeys na1) 100)))


(map #(.get (.mColors mesh) %) (range 8))

(def faces (.mFaces mesh))
(.limit faces)

(def face (.get faces 0))
(.mNumIndices face)

(map (fn [i] (let [face (.get faces i) indices (.mIndices face)] (map #(.get indices %) (range 3)))) (range 12))

(def vertices (.mVertices mesh))
(map (fn [i] (let [vertex (.get vertices i)] [(.x vertex) (.y vertex) (.z vertex)])) (range 24))

(def normals (.mNormals mesh))
(map (fn [i] (let [normal (.get normals i)] [(.x normal) (.y normal) (.z normal)])) (range 24))

(def tangents (.mTangents mesh))
(map (fn [i] (let [tangent (.get tangents i)] [(.x tangent) (.y tangent) (.z tangent)])) (range 24))

(def bitangents (.mBitangents mesh))
(map (fn [i] (let [bitangent (.get bitangents i)] [(.x bitangent) (.y bitangent) (.z bitangent)])) (range 24))

(def texcoords (AIVector3D$Buffer. ^long (.get (.mTextureCoords mesh) 0) 24))
(map (fn [i] (let [texcoord (.get texcoords i)] [(.x texcoord) (.y texcoord)])) (range 24))

(.mMaterialIndex mesh)

(.mNumMaterials scene)
(def material (AIMaterial/create ^long (.get (.mMaterials scene) 0)))

(def properties (map #(AIMaterialProperty/create (.get (.mProperties material) %)) (range (.mNumProperties material))))

(map (fn [prop] (.dataString (.mKey prop))) properties)

(def color (AIColor4D/create))
(Assimp/aiGetMaterialColor material Assimp/AI_MATKEY_COLOR_AMBIENT Assimp/aiTextureType_NONE 0 color)
(Assimp/aiGetMaterialColor material Assimp/AI_MATKEY_COLOR_DIFFUSE Assimp/aiTextureType_NONE 0 color)
(Assimp/aiGetMaterialColor material Assimp/AI_MATKEY_COLOR_SPECULAR Assimp/aiTextureType_NONE 0 color)

(def p (PointerBuffer/allocateDirect 1))
(Assimp/aiGetMaterialProperty material Assimp/AI_MATKEY_ROUGHNESS_FACTOR 0 0 p)
(.getFloat (.mData (AIMaterialProperty/create ^long (.get p 0))))

(def p (PointerBuffer/allocateDirect 1))
(Assimp/aiGetMaterialProperty material Assimp/AI_MATKEY_METALLIC_FACTOR 0 0 p)
(.getFloat (.mData (AIMaterialProperty/create ^long (.get p 0))))

(def p (PointerBuffer/allocateDirect 1))
(Assimp/aiGetMaterialProperty material Assimp/AI_MATKEY_SPECULAR_FACTOR 0 0 p)

(Assimp/aiGetMaterialTextureCount material Assimp/aiTextureType_DIFFUSE)
(Assimp/aiGetMaterialTextureCount material Assimp/aiTextureType_NORMALS)

(def path (AIString/calloc))
(Assimp/aiGetMaterialTexture material Assimp/aiTextureType_DIFFUSE 0 path nil nil nil nil nil nil)
(.dataString path)

(def path (AIString/calloc))
(Assimp/aiGetMaterialTexture material Assimp/aiTextureType_NORMALS 0 path nil nil nil nil nil nil)
(.dataString path)

(def texture (AITexture/create ^long (.get (.mTextures scene) 0)))
(.mHeight texture)
(.mWidth texture)

(Assimp/aiReleaseImport scene)
