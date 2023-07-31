(require '[clojure.reflect :as r]
         '[clojure.pprint :as p])

(import '[org.lwjgl.assimp Assimp AIMesh AIVector3D AIVector3D$Buffer])
(import '[org.lwjgl.system MemoryUtil])

(defn all-methods [x]
  (->> x r/reflect
       :members
       (filter :return-type)
       (map :name)
       sort
       (map #(str "." %) )
       distinct
       (map symbol)))

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

;(def texcoords (AIVector3D/create ^long (.get (.mTextureCoords mesh) 0)))
;[(.x texcoords) (.y texcoords) (.z texcoords)]

(Assimp/aiReleaseImport scene)

; (def ptr (MemoryUtil/memAllocPointer 1))
; (MemoryUtil/memFree ptr)
