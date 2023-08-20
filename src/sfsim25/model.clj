(ns sfsim25.model
    "Import glTF models into Clojure"
    (:require [fastmath.matrix :refer (mat4x4)])
    (:import [org.lwjgl.assimp Assimp AIMesh]))

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
  [mesh]
  (let [faces (.mFaces mesh)]
    (vec (mapcat #(decode-face (.get faces %)) (range (.mNumFaces mesh))))))

(defn- decode-mesh
  "Fetch vertex and index data for mesh with given index"
  [scene i]
  (let [buffer (.mMeshes scene)
        mesh   (AIMesh/create ^long (.get buffer i))]
    {:indices (decode-indices mesh)}))

(defn read-gltf
  "Import a glTF model file"
  [filename]
  (let [scene (Assimp/aiImportFile filename Assimp/aiProcess_Triangulate)]
    {:root (decode-node (.mRootNode scene))
     :meshes (mapv #(decode-mesh scene %) (range (.mNumMeshes scene)))}))
