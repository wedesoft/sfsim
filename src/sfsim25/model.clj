(ns sfsim25.model
    "Import glTF models into Clojure"
    (:require [fastmath.matrix :refer (mat4x4)]
              [fastmath.vector :refer (vec3)])
    (:import [org.lwjgl.assimp Assimp AIMesh AIMaterial AIColor4D]))

(defn- decode-matrix
  "Convert AIMatrix4x4 to mat4x4"
  [m]
  (mat4x4 (.a1 m) (.a2 m) (.a3 m) (.a4 m)
          (.b1 m) (.b2 m) (.b3 m) (.b4 m)
          (.c1 m) (.c2 m) (.c3 m) (.c4 m)
          (.d1 m) (.d2 m) (.d3 m) (.d4 m)))

(defn- decode-node
  "Fetch data of a node"
  [node]
  {:name (.dataString (.mName node))
   :transform (decode-matrix (.mTransformation node))})

(defn- decode-face
  "Get indices from face"
  [face]
  (let [indices (.mIndices face)]
    (map #(.get indices %) (range (.mNumIndices face)))))

(defn- decode-indices
  "Get vertex indices of faces from mesh"
  [mesh]
  (let [faces (.mFaces mesh)]
    (vec (mapcat #(decode-face (.get faces %)) (range (.mNumFaces mesh))))))

(defn- decode-vector
  "Get x, y, and z from vector"
  [v]
  [(.x v) (.y v) (.z v)])

(defn- decode-vertices
  "Get vertex data from mesh"
  [mesh]
  (let [vertices (.mVertices mesh)
        normals  (.mNormals mesh)]
    (vec (mapcat (fn [i] (concat (decode-vector (.get vertices i)) (decode-vector (.get normals i)))) (range (.mNumVertices mesh))))))

(defn- decode-color
  "Get RGB color of material"
  [material property]
  (let [color (AIColor4D/create)]
    (Assimp/aiGetMaterialColor material Assimp/AI_MATKEY_COLOR_DIFFUSE Assimp/aiTextureType_NONE 0 color)
    (vec3 (.r color) (.g color) (.b color))))

(defn- decode-material
  "Fetch material data for material with given index"
  [scene i]
  (let [material (AIMaterial/create ^long (.get (.mMaterials scene) i))]
    {:diffuse (decode-color material Assimp/AI_MATKEY_COLOR_DIFFUSE)}))

(defn- decode-mesh
  "Fetch vertex and index data for mesh with given index"
  [scene i]
  (let [buffer (.mMeshes scene)
        mesh   (AIMesh/create ^long (.get buffer i))]
    {:indices        (decode-indices mesh)
     :vertices       (decode-vertices mesh)
     :attributes     ["vertex" 3 "normal" 3]
     :material-index (.mMaterialIndex mesh)}))

(defn read-gltf
  "Import a glTF model file"
  [filename]
  (let [scene (Assimp/aiImportFile filename Assimp/aiProcess_Triangulate)]
    {:root      (decode-node (.mRootNode scene))
     :materials (mapv #(decode-material scene %) (range (.mNumMaterials scene)))
     :meshes    (mapv #(decode-mesh scene %) (range (.mNumMeshes scene)))}))
