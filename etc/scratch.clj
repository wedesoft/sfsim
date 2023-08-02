; https://lwjglgamedev.gitbooks.io/3d-game-development-with-lwjgl/content/chapter27/chapter27.html
; https://github.com/LWJGL/lwjgl3/issues/558

(require '[clojure.reflect :as r]
         '[clojure.pprint :as p])

(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer AIMaterial AIString AITexture])
(import '[org.lwjgl.stb STBImage])

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

(def material (AIMaterial/create ^long (.get (.mMaterials scene) 0)))

(.get (.mProperties material) 0)

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

(Assimp/aiReleaseImport scene)
