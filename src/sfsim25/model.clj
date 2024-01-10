(ns sfsim25.model
    "Import glTF models into Clojure"
    (:require [clojure.math :refer (floor)]
              [malli.core :as m]
              [fastmath.matrix :refer (mat4x4 mulm eye diagonal)]
              [fastmath.vector :refer (vec3 mult add)]
              [sfsim25.render :refer (make-vertex-array-object destroy-vertex-array-object render-triangles make-rgba-texture
                                      destroy-texture vertex-array-object texture-2d)]
              [sfsim25.matrix :refer (transformation-matrix quaternion->matrix fvec3 fmat4)]
              [sfsim25.util :refer (N0 image)]
              [sfsim25.quaternion :refer (->Quaternion quaternion) :as q])
    (:import [org.lwjgl.assimp Assimp AIMesh AIMaterial AIColor4D AINode AITexture AIString AIVector3D$Buffer AIAnimation
              AINodeAnim]
             [org.lwjgl.stb STBImage]))

(set! *unchecked-math* true)
; (set! *warn-on-reflection* true)

(defn- decode-matrix
  "Convert AIMatrix4x4 to mat4x4"
  {:malli/schema [:=> [:cat :some] fmat4]}
  [m]
  (mat4x4 (.a1 m) (.a2 m) (.a3 m) (.a4 m)
          (.b1 m) (.b2 m) (.b3 m) (.b4 m)
          (.c1 m) (.c2 m) (.c3 m) (.c4 m)
          (.d1 m) (.d2 m) (.d3 m) (.d4 m)))

(def node (m/schema [:schema {:registry {::node [:map [:name :string]
                                                      [:transform fmat4]
                                                      [:mesh-indices [:vector N0]]
                                                      [:children [:vector [:ref ::node]]]]}}
                     [:ref ::node]]))

(defn- decode-node
  "Fetch data of a node"
  {:malli/schema [:=> [:cat :some] node]}
  [node]
  {:name         (.dataString (.mName node))
   :transform    (decode-matrix (.mTransformation node))
   :mesh-indices (mapv #(.get (.mMeshes node) %) (range (.mNumMeshes node)))
   :children     (mapv #(decode-node (AINode/create ^long (.get (.mChildren node) %))) (range (.mNumChildren node)))})

(defn- decode-face
  "Get indices from face"
  {:malli/schema [:=> [:cat :some] [:sequential N0]]}
  [face]
  (let [indices (.mIndices face)]
    (map #(.get indices %) (range (.mNumIndices face)))))

(defn- decode-indices
  "Get vertex indices of faces from mesh"
  {:malli/schema [:=> [:cat :some] [:vector N0]]}
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
    (Assimp/aiGetMaterialColor material property Assimp/aiTextureType_NONE 0 color)
    (vec3 (.r color) (.g color) (.b color))))

(defn- decode-texture-index
  "Get texture index of material"
  [material property]
  (when (not (zero? (Assimp/aiGetMaterialTextureCount material property)))
    (let [path (AIString/calloc)]
      (Assimp/aiGetMaterialTexture material property 0 path nil nil nil nil nil nil)
      (Integer/parseInt (subs (.dataString path) 1)))))

(def material (m/schema [:map [:diffuse fvec3] [:color-texture-index [:maybe N0]] [:normal-texture-index [:maybe N0]]]))

(defn- decode-material
  "Fetch material data for material with given index"
  {:malli/schema [:=> [:cat :some N0] material]}
  [scene i]
  (let [material (AIMaterial/create ^long (.get (.mMaterials scene) i))]
    {:diffuse              (decode-color material Assimp/AI_MATKEY_COLOR_DIFFUSE)
     :color-texture-index  (decode-texture-index material Assimp/aiTextureType_DIFFUSE)
     :normal-texture-index (decode-texture-index material Assimp/aiTextureType_NORMALS)}))

(def mesh (m/schema [:map [:indices [:vector N0]]
                          [:vertices [:vector number?]]
                          [:attributes [:vector [:or :string N0]]]
                          [:material-index N0]]))

(defn- decode-mesh
  "Fetch vertex and index data for mesh with given index"
  {:malli/schema [:=> [:cat :some [:vector material] N0] :map]}
  [scene materials i]
  (let [buffer              (.mMeshes scene)
        mesh                (AIMesh/create ^long (.get buffer i))
        material-index      (.mMaterialIndex mesh)
        material            (nth materials material-index)
        has-color-texture   (:color-texture-index material)
        has-normal-texture  (:normal-texture-index material)]
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
  {:malli/schema [:=> [:cat :some N0] image]}
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

(def position-key (m/schema [:map [:time :double] [:position fvec3]]))

(defn- decode-position-key
  "Read a position key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] position-key]}
  [channel ticks-per-second i]
  (let [position-key (.get (.mPositionKeys channel) i)
        position     (.mValue position-key)]
    {:time (/ (.mTime position-key) ticks-per-second)
     :position (vec3 (.x position) (.y position) (.z position))}))

(def rotation-key (m/schema [:map [:time :double] [:rotation quaternion]]))

(defn- decode-rotation-key
  "Read a rotation key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] rotation-key]}
  [channel ticks-per-second i]
  (let [rotation-key (.get (.mRotationKeys channel) i)
        rotation     (.mValue rotation-key)]
    {:time (/ (.mTime rotation-key) ticks-per-second)
     :rotation (->Quaternion (.w rotation) (.x rotation) (.y rotation) (.z rotation))}))

(def scaling-key (m/schema [:map [:time :double] [:scaling fvec3]]))

(defn- decode-scaling-key
  "Read a scaling key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] scaling-key]}
  [channel ticks-per-second i]
  (let [scaling-key (.get (.mScalingKeys channel) i)
        scaling     (.mValue scaling-key)]
    {:time (/ (.mTime scaling-key) ticks-per-second)
     :scaling (vec3 (.x scaling) (.y scaling) (.z scaling))}))

(def channel (m/schema [:map [:position-keys [:vector position-key]]
                             [:rotation-keys [:vector rotation-key]]
                             [:scaling-keys [:vector scaling-key]]]))

(defn- decode-channel
  "Read channel of an animation"
  {:malli/schema [:=> [:cat :some :double N0] [:tuple :string channel]]}
  [animation ticks-per-second i]
  (let [channel (AINodeAnim/create ^long (.get (.mChannels animation) i))]
    [(.dataString (.mNodeName channel))
     {:position-keys (mapv #(decode-position-key channel ticks-per-second %) (range (.mNumPositionKeys channel)))
      :rotation-keys (mapv #(decode-rotation-key channel ticks-per-second %) (range (.mNumRotationKeys channel)))
      :scaling-keys (mapv #(decode-scaling-key channel ticks-per-second %) (range (.mNumScalingKeys channel)))}]))

(defn- decode-animation
  "Read animation data of scene"
  {:malli/schema [:=> [:cat :some N0] [:tuple :string [:map [:duration :double] [:channels [:map-of :string channel]]]]]}
  [scene i]
  (let [animation        (AIAnimation/create ^long (.get (.mAnimations scene) i))
        ticks-per-second (.mTicksPerSecond animation)]
    [(.dataString (.mName animation))
     {:duration (/ (.mDuration animation) ticks-per-second)
      :channels (into {} (map #(decode-channel animation ticks-per-second %) (range (.mNumChannels animation))))}]))

(defn read-gltf
  "Import a glTF model file"
  {:malli/schema [:=> [:cat :string] [:map [:root node]]]}
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
  {:malli/schema [:=> [:cat fn? mesh material] [:map [:vao vertex-array-object]]]}
  [program-selection mesh material]
  (assoc mesh :vao (make-vertex-array-object (program-selection material) (:indices mesh) (:vertices mesh) (:attributes mesh))))

(defn- load-meshes-into-opengl
  "Load meshes into OpenGL buffers"
  {:malli/schema [:=> [:cat [:map [:root node]] fn?] [:map [:root node] [:meshes [:vector mesh]]]]}
  [scene program-selection]
  (let [material (fn [mesh] (nth (:materials scene) (:material-index mesh)))]
    (update scene :meshes (fn [meshes] (mapv #(load-mesh-into-opengl program-selection % (material %)) meshes)))))

(defn- load-textures-into-opengl
  "Load images into OpenGL textures"
  {:malli/schema [:=> [:cat [:map [:root node]]] [:map [:root node] [:textures [:vector texture-2d]]]]}
  [scene]
  (update scene :textures (fn [textures] (mapv #(make-rgba-texture :linear :repeat %) textures))))

(defn- propagate-texture
  "Add color and normal textures to material"
  {:malli/schema [:=> [:cat material [:vector texture-2d]] material]}
  [material textures]
  (merge material {:colors  (some->> material :color-texture-index (nth textures))
                   :normals (some->> material :normal-texture-index (nth textures))}))

(defn- propagate-textures
  "Add OpenGL textures to materials"
  {:malli/schema [:=> [:cat [:map [:root node]]] [:map [:root node] [:materials [:vector material]]]]}
  [scene]
  (update scene :materials (fn [materials] (mapv #(propagate-texture % (:textures scene)) materials))))

(defn- propagate-materials
  "Add material information to meshes"
  {:malli/schema [:=> [:cat [:map [:root node]]] [:map [:root node] [:meshes [:vector mesh]]]]}
  [scene]
  (update scene :meshes (fn [meshes] (mapv #(assoc % :material (nth (:materials scene) (:material-index %))) meshes))))

(defn load-scene-into-opengl
  "Load indices and vertices into OpenGL buffers"
  {:malli/schema [:=> [:cat fn? [:map [:root node]]] [:map [:root node]]]}
  [program-selection scene]
  (-> scene
      load-textures-into-opengl
      (load-meshes-into-opengl program-selection)
      propagate-textures
      propagate-materials))

(defn unload-scene-from-opengl
  "Destroy vertex array objects of scene"
  {:malli/schema [:=> [:cat [:map [:root node]]] :nil]}
  [scene]
  (doseq [mesh (:meshes scene)] (destroy-vertex-array-object (:vao mesh)))
  (doseq [texture (:textures scene)] (destroy-texture texture)))

(defn render-scene
  "Render meshes of specified scene"
  {:malli/schema [:=> [:cat fn? [:map [:root node]] :any [:? [:cat fmat4 node]]] :nil]}
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

(defn- interpolate-frame
  "Interpolate between pose frames"
  {:malli/schema [:=> [:cat [:vector [:map [:time :double]]] :double :keyword fn?] :some]}
  [key-frames t k lerp]
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
                               (lerp (k frame-a) (k frame-b) weight)))))

(defn- nearest-quaternion
  "Return nearest rotation quaternion to q with same rotation as p"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  [p q]
  (let [positive-p-dist (q/norm2 (q/- q p))
        negative-p-dist (q/norm2 (q/+ q p))]
    (if (< positive-p-dist negative-p-dist) p (q/- p))))

(defn interpolate-position
  "Interpolate between scaling frames"
  {:malli/schema [:=> [:cat [:vector position-key] :double] fvec3]}
  [key-frames t]
  (interpolate-frame key-frames t :position
                     (fn [a b weight] (add (mult a weight) (mult b (- 1.0 weight))))))

(defn interpolate-rotation
  "Interpolate between rotation frames"
  {:malli/schema [:=> [:cat [:vector rotation-key] :double] quaternion]}
  [key-frames t]
  (interpolate-frame key-frames t :rotation
                     (fn [a b weight] (q/+ (q/scale a weight) (q/scale (nearest-quaternion b a) (- 1.0 weight))))))

(defn interpolate-scaling
  "Interpolate between scaling frames"
  {:malli/schema [:=> [:cat [:vector scaling-key] :double] fvec3]}
  [key-frames t]
  (interpolate-frame key-frames t :scaling
                     (fn [a b weight] (add (mult a weight) (mult b (- 1.0 weight))))))

(defn interpolate-transformation
  "Determine transformation matrix for a given channel and time"
  {:malli/schema [:=> [:cat channel :double] fmat4]}
  [channel t]
  (let [position (interpolate-position (:position-keys channel) t)
        rotation (interpolate-rotation (:rotation-keys channel) t)
        scaling (interpolate-scaling (:scaling-keys channel) t)]
    (transformation-matrix (mulm (quaternion->matrix rotation) (diagonal scaling)) position)))

(defn animations-frame
  "Create hash map with transforms for objects of model given a hash map of animation times"
  {:malli/schema [:=> [:cat [:map [:animations :map]] :map] [:map-of :string :some]]}
  [model animation-times]
  (let [animations (:animations model)]
    (or (apply merge
               (for [[animation-name animation-time] animation-times]
                    (let [animation (animations animation-name)]
                      (into {} (for [[object-name channel] (:channels animation)]
                                    [object-name (interpolate-transformation channel animation-time)])))))
        {})))

(defn- apply-transforms-node
  "Apply hash map of transforms to node and children"
  {:malli/schema [:=> [:cat :map [:map-of :string :some]] :map]}
  [node transforms]
  (assoc node
         :transform (or (transforms (:name node)) (:transform node))
         :children (mapv #(apply-transforms-node % transforms) (:children node))))

(defn apply-transforms
  "Apply hash map of transforms to model in order to animate it"
  {:malli/schema [:=> [:cat [:map [:root :map]] [:map-of :string :some]] [:map [:root :map]]]}
  [model transforms]
  (assoc model :root (apply-transforms-node (:root model) transforms)))

; (set! *warn-on-reflection* false)
(set! *unchecked-math* false)
