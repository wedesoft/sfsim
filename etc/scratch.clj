; https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/chapter27/chapter27.html
; https://github.com/LWJGL/lwjgl3/issues/558

(require '[clojure.math :refer :all])
(require '[sfsim25.model :refer :all])
(require '[sfsim25.render :refer :all])
(require '[sfsim25.matrix :refer :all])
(require '[sfsim25.quaternion :as q])
(require '[fastmath.vector :as v])
(import '[org.lwjgl.glfw GLFW GLFWKeyCallback])

(GLFW/glfwInit)

(def model (read-gltf "test/sfsim25/fixtures/model/cube.gltf"))

(def w 640)
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
} vs_out;
void main()
{
  vs_out.normal = mat3(transform) * normal;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-uniform
"#version 410 core
uniform vec3 light;
uniform vec3 diffuse_color;
in VS_OUT
{
  vec3 normal;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = diffuse_color * max(0, dot(light, fs_in.normal));
}")

(def program-uniform (make-program :vertex [vertex-uniform] :fragment [fragment-uniform]))

(def scene (load-scene-into-opengl (constantly program-uniform) model))

(def projection (projection-matrix w h 0.1 10.0 (to-radians 60.0)))

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

(def t0 (atom (System/currentTimeMillis)))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb (if (@keystates GLFW/GLFW_KEY_KP_6) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_4) -0.001 0))
             rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))]
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt ra) (v/vec3 1 0 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rb) (v/vec3 0 1 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rc) (v/vec3 0 0 1)))
         (onscreen-render window
                          (clear (v/vec3 0.1 0.1 0.1) 0)
                          (use-program program-uniform)
                          (uniform-matrix4 program-uniform "projection" projection)
                          (uniform-matrix4 program-uniform "transform"
                                           (transformation-matrix (quaternion->matrix @orientation) (v/vec3 0 0 -5)))
                          (uniform-vector3 program-uniform "light" (v/normalize (v/vec3 0 5 2)))
                          (render-scene (constantly program-uniform)
                                        (assoc-in scene [:root :transform] (transformation-matrix (quaternion->matrix @orientation) (v/vec3 0 0 -5)))
                                        (fn [{:keys [transform diffuse]}]
                                            (uniform-matrix4 program-uniform "transform" transform)
                                            (uniform-vector3 program-uniform "diffuse_color" diffuse))))
         (GLFW/glfwPollEvents)
         (swap! t0 + dt)))

(unload-scene-from-opengl scene)
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

(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer AIColor4D AIColor4D$Buffer AIMaterial AIString AITexture AIMaterialProperty AINode])
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

(def scene (Assimp/aiImportFile "test/sfsim25/fixtures/model/bricks.gltf" (bit-or Assimp/aiProcess_Triangulate Assimp/aiProcess_CalcTangentSpace)))
(.dataString (.mName scene))
(.mNumMeshes scene)

(def root (.mRootNode scene))
(def m1 (.mTransformation root))
(.mMeshes root)
(.dataString (.mName root))
(.get (.mMeshes root) 0)
(.mNumChildren root)
;(def child (AINode/create ^long (.get (.mChildren root) 0)))
;(def m2 (.mTransformation child))
;(.dataString (.mName child))
;(.get (.mMeshes child) 0)

(def buffer (.mMeshes scene))
(.limit buffer)

(def mesh (AIMesh/create ^long (.get buffer 0)))
(.dataString (.mName mesh))
(.mNumFaces mesh)
(.mNumVertices mesh)
(.mMaterialIndex mesh)

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
(def data (.pcDataCompressed texture))
(def width (int-array 1))
(def height (int-array 1))
(def channels (int-array 1))
(def buffer (STBImage/stbi_load_from_memory data width height channels 4))
(def width (aget width 0))
(def height (aget height 0))
(def b (byte-array (* width height 4)))
(.get buffer b)
(.flip buffer)
(STBImage/stbi_image_free buffer)
(def img {:data b :width width :height height :channels (aget channels 0)})
;(spit-png "test.png" img)

(def texture (AITexture/create ^long (.get (.mTextures scene) 1)))
(.mHeight texture)
(.mWidth texture)
(def data (.pcDataCompressed texture))
(def width (int-array 1))
(def height (int-array 1))
(def channels (int-array 1))
(def buffer (STBImage/stbi_load_from_memory data width height channels 4))
(def width (aget width 0))
(def height (aget height 0))
(def b (byte-array (* width height 4)))
(.get buffer b)
(.flip buffer)
(STBImage/stbi_image_free buffer)
(def normal {:data b :width width :height height :channels (aget channels 0)})
;(spit-png "test.png" img)

(GLFW/glfwInit)

(def w 640)
(def h 480)
(def window (make-window "cube" w h))
(GLFW/glfwShowWindow window)

(def colors-tex (make-rgba-texture :linear :repeat img))
(def normals-tex (make-rgba-texture :linear :repeat normal))

(def vertex-shader
"#version 410 core
uniform mat4 projection;
uniform mat4 rotation;
in vec3 point;
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
  gl_Position = projection * (rotation * vec4(point, 1) + vec4(0, 0, -4, 0));
  vs_out.surface = mat3(rotation) * mat3(tangent, bitangent, normal);
  vs_out.texcoord = texcoord;
}")

(def fragment-shader
"#version 410 core
uniform vec3 light;
uniform sampler2D normals;
uniform sampler2D colors;
uniform mat4 rotation;
in VS_OUT
{
  mat3 surface;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 n = 2.0 * texture(normals, fs_in.texcoord).xyz - 1.0;
  float brightness = 0.2 + 0.8 * max(0, dot(light, fs_in.surface * n));
  fragColor = texture(colors, fs_in.texcoord).rgb * brightness;
}")

(def program (make-program :vertex [vertex-shader] :fragment [fragment-shader]))

(def indices (flatten (map (fn [i] (let [face (.get faces i) indices (.mIndices face)] (map #(.get indices %) (range 3)))) (range 12))))
(def p (map (fn [i] (let [vertex (.get vertices i)] [(.x vertex) (.y vertex) (.z vertex)])) (range 24)))
(def t (map (fn [i] (let [tangent (.get tangents i)] [(.x tangent) (.y tangent) (.z tangent)])) (range 24)))
(def bt (map (fn [i] (let [bitangent (.get bitangents i)] [(.x bitangent) (.y bitangent) (.z bitangent)])) (range 24)))
(def n (map (fn [i] (let [normal (.get normals i)] [(.x normal) (.y normal) (.z normal)])) (range 24)))
(def tc (map (fn [i] (let [texcoord (.get texcoords i)] [(.x texcoord) (- 1.0 (.y texcoord))])) (range 24)))
(def verts (flatten (map concat p t bt n tc)))

(def vao (make-vertex-array-object program indices verts ["point" 3 "tangent" 3 "bitangent" 3 "normal" 3 "texcoord" 2]))

(def projection (projection-matrix w h 0.1 10.0 (m/to-radians 60.0)))

(def keystates (atom {}))
(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))
(GLFW/glfwSetKeyCallback window keyboard-callback)

(def orientation (atom (q/rotation (m/to-radians 0) (v/vec3 0 0 1))))

(def t0 (atom (System/currentTimeMillis)))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb (if (@keystates GLFW/GLFW_KEY_KP_6) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_4) -0.001 0))
             rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))]
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt ra) (v/vec3 1 0 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rb) (v/vec3 0 1 0)))
         (swap! orientation #(q/* %2 %1) (q/rotation (* dt rc) (v/vec3 0 0 1)))
         (onscreen-render window
                          (clear (v/vec3 0.1 0.1 0.1) 0)
                          (use-program program)
                          (uniform-sampler program "colors" 0)
                          (uniform-sampler program "normals" 1)
                          (uniform-matrix4 program "projection" projection)
                          (uniform-matrix4 program "rotation" (transformation-matrix (quaternion->matrix @orientation) (v/vec3 0 0 0)))
                          (uniform-vector3 program "light" (v/normalize (v/vec3 0 5 2)))
                          (use-textures colors-tex normals-tex)
                          (GL30/glBindVertexArray ^long (:vertex-array-object vao))
                          (GL11/glDrawElements GL11/GL_TRIANGLES ^long (:nrows vao) GL11/GL_UNSIGNED_INT 0))
         (GLFW/glfwPollEvents)
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)
(destroy-texture colors-tex)
(destroy-texture normals-tex)
(destroy-window window)
(GLFW/glfwTerminate)
(Assimp/aiReleaseImport scene)
