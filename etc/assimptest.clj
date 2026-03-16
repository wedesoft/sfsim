;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(require '[clojure.reflect :as r]
         '[clojure.pprint :as p]
         '[clojure.math :as m]
         '[fastmath.vector :as v]
         '[sfsim.render :refer :all]
         '[sfsim.util :refer :all]
         '[sfsim.matrix :refer (projection-matrix transformation-matrix quaternion->matrix)]
         '[sfsim.quaternion :as q])

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
