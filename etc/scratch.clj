; https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/chapter27/chapter27.html
; https://github.com/LWJGL/lwjgl3/issues/558

(require '[clojure.reflect :as r]
         '[clojure.pprint :as p]
         '[clojure.math :as m]
         '[fastmath.vector :as v]
         '[sfsim25.render :refer :all]
         '[sfsim25.matrix :refer (projection-matrix transformation-matrix quaternion->matrix)]
         '[sfsim25.quaternion :as q])

(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer AIMaterial AIString AITexture AIMaterialProperty])
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

(def scene (Assimp/aiImportFile "etc/cube.gltf" Assimp/aiProcess_Triangulate))
(.dataString (.mName scene))
(.mNumMeshes scene)

(def buffer (.mMeshes scene))
(.limit buffer)

(def mesh (AIMesh/create ^long (.get buffer 0)))
(.dataString (.mName mesh))
(.mNumFaces mesh)
(.mNumVertices mesh)
(.mMaterialIndex mesh)

(def faces (.mFaces mesh))
(.limit faces)

(def face (.get faces 0))
(.mNumIndices face)

(map (fn [i] (let [face (.get faces i) indices (.mIndices face)] (map #(.get indices %) (range 3)))) (range 12))

(def vertices (.mVertices mesh))
(map (fn [i] (let [vertex (.get vertices i)] [(.x vertex) (.y vertex) (.z vertex)])) (range 24))

(def normals (.mNormals mesh))
(map (fn [i] (let [normal (.get normals i)] [(.x normal) (.y normal) (.z normal)])) (range 24))

(def texcoords (AIVector3D$Buffer. ^long (.get (.mTextureCoords mesh) 0) 24))
(map (fn [i] (let [texcoord (.get texcoords i)] [(.x texcoord) (.y texcoord) (.z texcoord)])) (range 24))

(.mNumMaterials scene)
(def material (AIMaterial/create ^long (.get (.mMaterials scene) 0)))

(def properties (map #(AIMaterialProperty/create (.get (.mProperties material) %)) (range (.mNumProperties material))))

Assimp/AI_MATKEY_SPECULAR_FACTOR

(defn read-property [prop]
  (case (.mType prop)
    4  (.getFloat (.mData prop))
    nil))

(map (fn [prop] (.dataString (.mKey prop))) properties)
(map (fn [prop] [(.getFloat (.mData prop))]) properties)
(map (fn [prop] [(.dataString (.mKey prop)) (read-property prop) (.mDataLength prop)]) properties)

(def property (nth properties 20))
{(.dataString (.mKey property)) (.getFloat (.mData property))}

(def property (nth properties 20))
(.dataString (.mKey property))
(.mData property)
(.getFloat (.mData property))

(Assimp/aiGetMaterialTextureCount material Assimp/aiTextureType_DIFFUSE)

(def path (AIString/calloc))
(Assimp/aiGetMaterialTexture material Assimp/aiTextureType_DIFFUSE 0 path nil nil nil nil nil nil)
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

(GLFW/glfwInit)

(def w 640)
(def h 480)
(def window (make-window "cube" w h))
(GLFW/glfwShowWindow window)

(def tex (make-rgba-texture :linear :clamp img))

(def vertex-shader
"#version 410 core
uniform mat4 projection;
uniform mat4 rotation;
in vec3 point;
in vec3 normal;
in vec2 texcoord;
out VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} vs_out;
void main()
{
  gl_Position = projection * (rotation * vec4(point, 1) + vec4(0, 0, -4, 0));
  vs_out.normal = normal;
  vs_out.texcoord = texcoord;
}")

(def fragment-shader
"#version 410 core
uniform sampler2D tex;
in VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} fs_in;
out vec4 fragColor;
void main()
{
  fragColor = texture(tex, fs_in.texcoord);
}")

(def program (make-program :vertex [vertex-shader] :fragment [fragment-shader]))

(def indices (flatten (map (fn [i] (let [face (.get faces i) indices (.mIndices face)] (map #(.get indices %) (range 3)))) (range 12))))
(def p (map (fn [i] (let [vertex (.get vertices i)] [(.x vertex) (.y vertex) (.z vertex)])) (range 24)))
(def n (map (fn [i] (let [normal (.get normals i)] [(.x normal) (.y normal) (.z normal)])) (range 24)))
(def t (map (fn [i] (let [texcoord (.get texcoords i)] [(.x texcoord) (- 1.0 (.y texcoord))])) (range 24)))
(def verts (flatten (map concat p n t)))

(def vao (make-vertex-array-object program indices verts [:point 3 :normal 3 :texcoord 2]))

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
             rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
             rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (v/vec3 1 0 0)))
         (swap! orientation q/* (q/rotation (* dt rb) (v/vec3 0 1 0)))
         (swap! orientation q/* (q/rotation (* dt rc) (v/vec3 0 0 1)))
         (onscreen-render window
                          (clear (v/vec3 0 1 0) 0)
                          (use-program program)
                          (uniform-sampler program "tex" 0)
                          (uniform-matrix4 program "projection" projection)
                          (uniform-matrix4 program "rotation" (transformation-matrix (quaternion->matrix @orientation) (v/vec3 0 0 0)))
                          (use-textures tex)
                          (GL30/glBindVertexArray ^long (:vertex-array-object vao))
                          (GL11/glDrawElements GL11/GL_TRIANGLES ^long (:nrows vao) GL11/GL_UNSIGNED_INT 0))
         (GLFW/glfwPollEvents)
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)
(destroy-texture tex)
(destroy-window window)
(GLFW/glfwTerminate)
(Assimp/aiReleaseImport scene)
