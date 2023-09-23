(ns sfsim25.model
    "Import glTF models into Clojure"
    (:require [clojure.math :refer (floor)]
              [fastmath.matrix :refer (mat4x4 mulm eye diagonal)]
              [fastmath.vector :refer (vec3 mult add)]
              [sfsim25.render :refer (use-program uniform-matrix4 uniform-vector3 make-vertex-array-object
                                      destroy-vertex-array-object render-triangles make-rgba-texture destroy-texture
                                      use-textures uniform-sampler)]
              [sfsim25.matrix :refer (transformation-matrix quaternion->matrix)]
              [sfsim25.quaternion :refer (->Quaternion) :as q])
    (:import [org.lwjgl.assimp Assimp AIMesh AIMaterial AIColor4D AINode AITexture AIString AIVector3D$Buffer AIAnimation
              AINodeAnim]
             [org.lwjgl.stb STBImage]))

(set! *unchecked-math* true)

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
  {:name         (.dataString (.mName node))
   :transform    (decode-matrix (.mTransformation node))
   :mesh-indices (mapv #(.get (.mMeshes node) %) (range (.mNumMeshes node)))
   :children     (mapv #(decode-node (AINode/create ^long (.get (.mChildren node) %))) (range (.mNumChildren node)))})

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

(defn- decode-vector2
  "Get x, y, and z from vector"
  [v]
  [(.x v) (- 1.0 (.y v))])

(defn- decode-vector3
  "Get x, y, and z from vector"
  [v]
  [(.x v) (.y v) (.z v)])

(defn- decode-vertices
  "Get vertex data from mesh"
  [mesh has-color-texture has-normal-texture]
  (let [vertices   (.mVertices mesh)
        tangents   (.mTangents mesh)
        bitangents (.mBitangents mesh)
        normals    (.mNormals mesh)
        texcoords  (AIVector3D$Buffer. ^long (.get (.mTextureCoords mesh) 0) (.mNumVertices mesh))]
    (vec
      (mapcat
        (fn [i] (concat
                  (decode-vector3 (.get vertices i))
                  (if has-normal-texture (decode-vector3 (.get tangents i)) [])
                  (if has-normal-texture (decode-vector3 (.get bitangents i)) [])
                  (decode-vector3 (.get normals i))
                  (if (or has-color-texture has-normal-texture) (decode-vector2 (.get texcoords i)) [])))
        (range (.mNumVertices mesh))))))

(defn- decode-color
  "Get RGB color of material"
  [material property]
  (let [color (AIColor4D/create)]
    (Assimp/aiGetMaterialColor material Assimp/AI_MATKEY_COLOR_DIFFUSE Assimp/aiTextureType_NONE 0 color)
    (vec3 (.r color) (.g color) (.b color))))

(defn- decode-texture-index
  "Get texture index of material"
  [material property]
  (when (not (zero? (Assimp/aiGetMaterialTextureCount material property)))
    (let [path (AIString/calloc)]
      (Assimp/aiGetMaterialTexture material property 0 path nil nil nil nil nil nil)
      (Integer/parseInt (subs (.dataString path) 1)))))

(defn- decode-material
  "Fetch material data for material with given index"
  [scene i]
  (let [material (AIMaterial/create ^long (.get (.mMaterials scene) i))]
    {:diffuse              (decode-color material Assimp/AI_MATKEY_COLOR_DIFFUSE)
     :color-texture-index  (decode-texture-index material Assimp/aiTextureType_DIFFUSE)
     :normal-texture-index (decode-texture-index material Assimp/aiTextureType_NORMALS)}))

(defn- decode-mesh
  "Fetch vertex and index data for mesh with given index"
  [scene materials i]
  (let [buffer              (.mMeshes scene)
        mesh                (AIMesh/create ^long (.get buffer i))
        material-index      (.mMaterialIndex mesh)
        material            (nth materials material-index)
        has-color-texture   (:color-texture-index material)
        has-normal-texture  (:normal-texture-index material)
        ]
    {:indices             (decode-indices mesh)
     :vertices            (decode-vertices mesh has-color-texture has-normal-texture)
     :attributes          (if has-color-texture
                            (if has-normal-texture
                              ["vertex" 3 "tangent" 3 "bitangent" 3 "normal" 3 "texcoord" 2]
                              ["vertex" 3 "normal" 3 "texcoord" 2])
                            ["vertex" 3 "normal" 3])
     :material-index      material-index}))

(defn- decode-texture
  "Read texture with specified index from memory"
  [scene i]
  (let [texture  (AITexture/create ^long (.get (.mTextures scene) i))
        width    (int-array 1)
        height   (int-array 1)
        channels (int-array 1)
        buffer   (STBImage/stbi_load_from_memory (.pcDataCompressed texture) width height channels 4)
        data     (byte-array (.limit buffer))]
    (.get buffer data)
    (.flip buffer)
    (STBImage/stbi_image_free buffer)
    {:width (aget width 0) :height (aget height 0) :channels (aget channels 0) :data data}))

(defn- decode-position-key
  "Read a position key from an animation channel"
  [channel ticks-per-second i]
  (let [position-key (.get (.mPositionKeys channel) i)
        position     (.mValue position-key)]
    {:time (/ (.mTime position-key) ticks-per-second)
     :position (vec3 (.x position) (.y position) (.z position))}))

(defn- decode-rotation-key
  "Read a rotation key from an animation channel"
  [channel ticks-per-second i]
  (let [rotation-key (.get (.mRotationKeys channel) i)
        rotation     (.mValue rotation-key)]
    {:time (/ (.mTime rotation-key) ticks-per-second)
     :rotation (->Quaternion (.w rotation) (.x rotation) (.y rotation) (.z rotation))}))

(defn- decode-scaling-key
  "Read a scaling key from an animation channel"
  [channel ticks-per-second i]
  (let [scaling-key (.get (.mScalingKeys channel) i)
        scaling     (.mValue scaling-key)]
    {:time (/ (.mTime scaling-key) ticks-per-second)
     :scaling (vec3 (.x scaling) (.y scaling) (.z scaling))}))

(defn- decode-channel
  "Read channel of an animation"
  [animation ticks-per-second i]
  (let [channel (AINodeAnim/create ^long (.get (.mChannels animation) i))]
    [(.dataString (.mNodeName channel))
     {:position-keys (mapv #(decode-position-key channel ticks-per-second %) (range (.mNumPositionKeys channel)))
      :rotation-keys (mapv #(decode-rotation-key channel ticks-per-second %) (range (.mNumRotationKeys channel)))
      :scaling-keys (mapv #(decode-scaling-key channel ticks-per-second %) (range (.mNumScalingKeys channel)))}]))

(defn- decode-animation
  "Read animation data of scene"
  [scene i]
  (let [animation        (AIAnimation/create ^long (.get (.mAnimations scene) i))
        ticks-per-second (.mTicksPerSecond animation)]
    [(.dataString (.mName animation))
     {:duration (/ (.mDuration animation) ticks-per-second)
      :channels (into {} (map #(decode-channel animation ticks-per-second %) (range (.mNumChannels animation))))}]))

(defn read-gltf
  "Import a glTF model file"
  [filename]
  (let [scene      (Assimp/aiImportFile filename (bit-or Assimp/aiProcess_Triangulate Assimp/aiProcess_CalcTangentSpace))
        materials  (mapv #(decode-material scene %) (range (.mNumMaterials scene)))
        meshes     (mapv #(decode-mesh scene materials %) (range (.mNumMeshes scene)))
        textures   (mapv #(decode-texture scene %) (range (.mNumTextures scene)))
        animations (into {} (map #(decode-animation scene %) (range (.mNumAnimations scene))))
        result     {:root (decode-node (.mRootNode scene))
                    :materials materials
                    :meshes meshes
                    :textures textures
                    :animations animations}]
    (Assimp/aiReleaseImport scene)
    result))

(defn- load-mesh-into-opengl
  "Load index and vertex data into OpenGL buffer"
  [program-selection mesh material]
  (assoc mesh :vao (make-vertex-array-object (program-selection material) (:indices mesh) (:vertices mesh) (:attributes mesh))))

(defn- load-meshes-into-opengl
  "Load meshes into OpenGL buffers"
  [scene program-selection]
  (let [material (fn [mesh] (nth (:materials scene) (:material-index mesh)))]
    (update scene :meshes (fn [meshes] (mapv #(load-mesh-into-opengl program-selection % (material %)) meshes)))))

(defn- load-textures-into-opengl
  "Load images into OpenGL textures"
  [scene]
  (update scene :textures (fn [textures] (mapv #(make-rgba-texture :linear :repeat %) textures))))

(defn- propagate-texture
  "Add color and normal textures to material"
  [material textures]
  (merge material {:colors  (some->> material :color-texture-index (nth textures))
                   :normals (some->> material :normal-texture-index (nth textures))}))

(defn- propagate-textures
  "Add OpenGL textures to materials"
  [scene]
  (update scene :materials (fn [materials] (mapv #(propagate-texture % (:textures scene)) materials))))

(defn- propagate-materials
  "Add material information to meshes"
  [scene]
  (update scene :meshes (fn [meshes] (mapv #(assoc % :material (nth (:materials scene) (:material-index %))) meshes))))

(defn load-scene-into-opengl
  "Load indices and vertices into OpenGL buffers"
  [program-selection scene]
  (-> scene
      load-textures-into-opengl
      (load-meshes-into-opengl program-selection)
      propagate-textures
      propagate-materials))

(defn unload-scene-from-opengl
  "Destroy vertex array objects of scene"
  [scene]
  (doseq [mesh (:meshes scene)] (destroy-vertex-array-object (:vao mesh)))
  (doseq [texture (:textures scene)] (destroy-texture texture)))

(defn render-scene
  "Render meshes of specified scene"
  ([program-selection scene callback]
   (render-scene program-selection scene callback (eye 4) (:root scene)))
  ([program-selection scene callback transform node]
   (let [transform (mulm transform (:transform node))]
     (doseq [child-node (:children node)]
            (render-scene program-selection scene callback transform child-node))
     (doseq [mesh-index (:mesh-indices node)]
            (let [mesh                (nth (:meshes scene) mesh-index)
                  material            (:material mesh)
                  program             (program-selection material)]
              (callback (merge material {:program program :transform transform}))
              (render-triangles (:vao mesh)))))))

(defn interpolate-frame
  "Interpolate between pose frames"
  [key-frames t k add mult]
  (let [n       (count key-frames)
        t0      (:time (first key-frames))
        t1      (:time (last key-frames))
        delta-t (if (<= n 1) 1.0 (/ (- t1 t0) (dec n)))
        index   (int (floor (/ (- t t0) delta-t)))]
    (cond (<  index 0)       (k (first key-frames))
          (>= index (dec n)) (k (last key-frames))
          :else              (let [frame-a  (nth key-frames index)
                                   frame-b  (nth key-frames (inc index))
                                   weight   (/ (- (:time frame-b) t) delta-t)]
                               (add (mult (k frame-a) weight)
                                    (mult (k frame-b) (- 1 weight)))))))

(defn interpolate-position
  "Interpolate between scaling frames"
  [key-frames t]
  (interpolate-frame key-frames t :position add mult))

(defn interpolate-rotation
  "Interpolate between rotation frames"
  [key-frames t]
  (interpolate-frame key-frames t :rotation q/+ q/scale))

(defn interpolate-scaling
  "Interpolate between scaling frames"
  [key-frames t]
  (interpolate-frame key-frames t :scaling add mult))

(defn interpolate-transformation
  "Determine transformation matrix for a given channel and time"
  [channel t]
  (let [position (interpolate-position (:position-keys channel) t)
        rotation (interpolate-rotation (:rotation-keys channel) t)
        scaling (interpolate-scaling (:scaling-keys channel) t)]
    (transformation-matrix (mulm (quaternion->matrix rotation) (diagonal scaling)) position)))

(set! *unchecked-math* false)
