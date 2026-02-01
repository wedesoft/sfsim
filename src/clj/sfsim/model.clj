;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.model
  "Import glTF scenes into Clojure"
  (:require
    [clojure.math :refer (floor)]
    [comb.template :as template]
    [fastmath.matrix :refer (mat4x4 mulm mulv eye diagonal inverse)]
    [fastmath.vector :refer (vec3 vec4 mult add)]
    [malli.core :as m]
    [sfsim.atmosphere :refer (attenuation-point setup-atmosphere-uniforms make-atmosphere-geometry-renderer
                              destroy-atmosphere-geometry-renderer render-atmosphere-geometry cloud-overlay)]
    [sfsim.clouds :refer (lod-offset overall-shading overall-shading-parameters render-cloud-geometry)]
    [sfsim.plume :refer (model-data model-vars)]
    [sfsim.image :refer (image)]
    [sfsim.matrix :refer (transformation-matrix quaternion->matrix shadow-patch-matrices shadow-patch vec3->vec4 vec4->vec3 fvec3
                          fmat4 rotation-matrix get-translation)]
    [sfsim.planet :refer (surface-radiance-function shadow-vars make-planet-geometry-renderer destroy-planet-geometry-renderer
                          render-planet-geometry)]
    [sfsim.quaternion :refer (->Quaternion quaternion) :as q]
    [sfsim.render :refer (make-vertex-array-object destroy-vertex-array-object render-triangles vertex-array-object
                          make-program destroy-program use-program uniform-int uniform-float uniform-matrix4
                          uniform-vector3 uniform-sampler use-textures setup-shadow-and-opacity-maps with-stencil-op-ref-and-mask
                          setup-shadow-matrices render-vars make-render-vars texture-render-depth clear with-stencils) :as render]
    [sfsim.shaders :refer (phong shrink-shadow-index percentage-closer-filtering shadow-lookup)]
    [sfsim.texture :refer (make-rgba-texture destroy-texture texture-2d generate-mipmap)]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.util :refer (N0 N third)])
  (:import
    (java.nio
      DirectByteBuffer)
    (org.lwjgl.assimp
      AIAnimation
      AIColor4D
      AIFace
      AIMaterial
      AIMatrix4x4
      AIMesh
      AINode
      AINodeAnim
      AIQuatKey
      AIScene
      AIString
      AITexture
      AIVector3D
      AIVector3D$Buffer
      AIVectorKey
      Assimp)
    (fastmath.vector
      Vec4)
    (org.lwjgl.stb
      STBImage)
    (org.lwjgl.opengl
      GL11)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn- decode-matrix
  "Convert AIMatrix4x4 to mat4x4"
  {:malli/schema [:=> [:cat :some] fmat4]}
  [m]
  (mat4x4 (.a1 ^AIMatrix4x4 m) (.a2 ^AIMatrix4x4 m) (.a3 ^AIMatrix4x4 m) (.a4 ^AIMatrix4x4 m)
          (.b1 ^AIMatrix4x4 m) (.b2 ^AIMatrix4x4 m) (.b3 ^AIMatrix4x4 m) (.b4 ^AIMatrix4x4 m)
          (.c1 ^AIMatrix4x4 m) (.c2 ^AIMatrix4x4 m) (.c3 ^AIMatrix4x4 m) (.c4 ^AIMatrix4x4 m)
          (.d1 ^AIMatrix4x4 m) (.d2 ^AIMatrix4x4 m) (.d3 ^AIMatrix4x4 m) (.d4 ^AIMatrix4x4 m)))


(def node
  (m/schema [:schema {:registry {::node [:map [::name :string]
                                         [::transform fmat4]
                                         [::mesh-indices [:vector N0]]
                                         [::children [:vector [:ref ::node]]]]}}
             [:ref ::node]]))


(defn- decode-node
  "Fetch data of a node"
  {:malli/schema [:=> [:cat :some] node]}
  [node]
  (let [meshes       (.mMeshes ^AINode node)
        num-meshes   (.mNumMeshes ^AINode node)
        children     (.mChildren ^AINode node)
        num-children (.mNumChildren ^AINode node)]
    {::name         (.dataString (.mName ^AINode node))
     ::transform    (decode-matrix (.mTransformation ^AINode node))
     ::mesh-indices (mapv #(.get meshes ^long %) (range num-meshes))
     ::children     (mapv #(decode-node (AINode/create ^long (.get children ^long %))) (range num-children))}))


(defn- decode-face
  "Get indices from face"
  {:malli/schema [:=> [:cat :some] [:sequential N0]]}
  [face]
  (let [indices (.mIndices ^AIFace face)]
    (mapv #(.get indices ^long %) (range (.mNumIndices ^AIFace face)))))


(defn- decode-indices
  "Get vertex indices of faces from mesh"
  {:malli/schema [:=> [:cat :some] [:vector N0]]}
  [mesh]
  (let [faces (.mFaces ^AIMesh mesh)]
    (vec (mapcat #(decode-face (.get faces ^long %)) (range (.mNumFaces ^AIMesh mesh))))))


(defn- decode-vector2
  "Get x and inverse y from 2D vector"
  [v]
  [(.x ^AIVector3D v) (- 1.0 (.y ^AIVector3D v))])


(defn- decode-vector3
  "Get x, y, and z from vector"
  [v]
  [(.x ^AIVector3D v) (.y ^AIVector3D v) (.z ^AIVector3D v)])


(defn- decode-vertices
  "Get vertex data from mesh"
  [mesh has-color-texture has-normal-texture]
  (let [vertices   (.mVertices ^AIMesh mesh)
        tangents   (.mTangents ^AIMesh mesh)
        bitangents (.mBitangents ^AIMesh mesh)
        normals    (.mNormals ^AIMesh mesh)
        texcoords  (AIVector3D$Buffer. ^long (.get (.mTextureCoords ^AIMesh mesh) 0) (.mNumVertices ^AIMesh mesh))]
    (vec
      (mapcat
        (fn decode-vertex
          [^long i]
          (concat
            (decode-vector3 (.get vertices i))
            (if has-normal-texture (decode-vector3 (.get tangents i)) [])
            (if has-normal-texture (decode-vector3 (.get bitangents i)) [])
            (decode-vector3 (.get normals i))
            (if (or has-color-texture has-normal-texture) (decode-vector2 (.get texcoords i)) [])))
        (range (.mNumVertices ^AIMesh mesh))))))


(defn- decode-color
  "Get RGB color of material"
  [material property]
  (let [color (AIColor4D/create)]
    (Assimp/aiGetMaterialColor ^AIMaterial material ^String property Assimp/aiTextureType_NONE 0 color)
    (vec3 (.r color) (.g color) (.b color))))


(set! *warn-on-reflection* false)


(defn- decode-texture-index
  "Get texture index of material"
  [material property]
  (when (not (zero? (Assimp/aiGetMaterialTextureCount material property)))
    (let [path (AIString/calloc)]
      (Assimp/aiGetMaterialTexture ^AIMaterial material ^String property 0 path nil nil nil nil nil nil)
      (Integer/parseInt (subs (.dataString path) 1)))))


(set! *warn-on-reflection* true)


(def material
  (m/schema [:map [::diffuse fvec3]
             [::color-texture-index [:maybe :int]]
             [::normal-texture-index [:maybe :int]]
             [::colors [:maybe texture-2d]]
             [::normals [:maybe texture-2d]]]))


(defn- decode-material
  "Fetch material data for material with given index"
  {:malli/schema [:=> [:cat :some N0] material]}
  [scene i]
  (let [material (AIMaterial/create ^long (.get (.mMaterials ^AIScene scene) ^long i))]
    {::diffuse              (decode-color material Assimp/AI_MATKEY_COLOR_DIFFUSE)
     ::color-texture-index  (decode-texture-index material Assimp/aiTextureType_DIFFUSE)
     ::normal-texture-index (decode-texture-index material Assimp/aiTextureType_NORMALS)}))


(def mesh
  (m/schema [:map [::indices [:vector N0]]
             [::vertices [:vector number?]]
             [::attributes [:vector [:or :string N0]]]
             [::material-index N0]]))


(defn- decode-mesh
  "Fetch vertex and index data for mesh with given index"
  {:malli/schema [:=> [:cat :some [:vector material] N0] :map]}
  [scene materials i]
  (let [buffer              (.mMeshes ^AIScene scene)
        mesh                (AIMesh/create ^long (.get buffer ^long i))
        material-index      (.mMaterialIndex mesh)
        material            (nth materials material-index)
        has-color-texture   (::color-texture-index material)
        has-normal-texture  (::normal-texture-index material)]
    {::indices             (decode-indices mesh)
     ::vertices            (decode-vertices mesh has-color-texture has-normal-texture)
     ::attributes          (if has-normal-texture
                             ["vertex" 3 "tangent" 3 "bitangent" 3 "normal" 3 "texcoord" 2]
                             (if has-color-texture
                               ["vertex" 3 "normal" 3 "texcoord" 2]
                               ["vertex" 3 "normal" 3]))
     ::material-index      material-index}))


(defn- decode-texture
  "Read texture with specified index from memory"
  {:malli/schema [:=> [:cat :some N0] image]}
  [scene i]
  (let [texture  (AITexture/create ^long (.get (.mTextures ^AIScene scene) ^long i))
        width    (int-array 1)
        height   (int-array 1)
        channels (int-array 1)
        buffer   (STBImage/stbi_load_from_memory (.pcDataCompressed texture) width height channels 4)
        data     (byte-array (.limit buffer))]
    (.get ^DirectByteBuffer buffer ^bytes data)
    (.flip ^DirectByteBuffer buffer)
    (STBImage/stbi_image_free ^DirectByteBuffer buffer)
    #:sfsim.image{:width (aget width 0) :height (aget height 0) :channels (aget channels 0) :data data}))


(def position-key (m/schema [:map [::time :double] [::position fvec3]]))


(defn- decode-position-key
  "Read a position key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] position-key]}
  [channel ^double ticks-per-second ^long i]
  (let [position-key (.get (.mPositionKeys ^AINodeAnim channel) ^long i)
        position     (.mValue ^AIVectorKey position-key)]
    {::time (/ (.mTime ^AIVectorKey position-key) ticks-per-second)
     ::position (vec3 (.x position) (.y position) (.z position))}))


(def rotation-key (m/schema [:map [::time :double] [::rotation quaternion]]))


(defn- decode-rotation-key
  "Read a rotation key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] rotation-key]}
  [channel ^double ticks-per-second i]
  (let [rotation-key (.get (.mRotationKeys ^AINodeAnim channel) ^long i)
        rotation     (.mValue ^AIQuatKey rotation-key)]
    {::time (/ (.mTime ^AIQuatKey rotation-key) ticks-per-second)
     ::rotation (->Quaternion (.w rotation) (.x rotation) (.y rotation) (.z rotation))}))


(def scaling-key (m/schema [:map [::time :double] [::scaling fvec3]]))


(defn- decode-scaling-key
  "Read a scaling key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] scaling-key]}
  [channel ^double ticks-per-second ^long i]
  (let [scaling-key (.get (.mScalingKeys ^AINodeAnim channel) ^long i)
        scaling     (.mValue ^AIVectorKey scaling-key)]
    {::time (/ (.mTime ^AIVectorKey scaling-key) ticks-per-second)
     ::scaling (vec3 (.x scaling) (.y scaling) (.z scaling))}))


(def channel
  (m/schema [:map [::position-keys [:vector position-key]]
             [::rotation-keys [:vector rotation-key]]
             [::scaling-keys [:vector scaling-key]]]))


(defn- decode-channel
  "Read channel of an animation"
  {:malli/schema [:=> [:cat :some :double N0] [:tuple :string channel]]}
  [animation ^double ticks-per-second ^long i]
  (let [channel (AINodeAnim/create ^long (.get (.mChannels ^AIAnimation animation) ^long i))]
    [(.dataString (.mNodeName channel))
     {::position-keys (mapv #(decode-position-key channel ticks-per-second %) (range (.mNumPositionKeys channel)))
      ::rotation-keys (mapv #(decode-rotation-key channel ticks-per-second %) (range (.mNumRotationKeys channel)))
      ::scaling-keys (mapv #(decode-scaling-key channel ticks-per-second %) (range (.mNumScalingKeys channel)))}]))


(defn- decode-animation
  "Read animation data of scene"
  {:malli/schema [:=> [:cat :some N0] [:tuple :string [:map [::duration :double] [::channels [:map-of :string channel]]]]]}
  [scene i]
  (let [animation        (AIAnimation/create ^long (.get (.mAnimations ^AIScene scene) ^long i))
        ticks-per-second (.mTicksPerSecond animation)]
    [(.dataString (.mName animation))
     {::duration (/ (.mDuration animation) ticks-per-second)
      ::channels (into {} (mapv #(decode-channel animation ticks-per-second %) (range (.mNumChannels animation))))}]))


(defn read-gltf
  "Import a glTF file"
  {:malli/schema [:=> [:cat :string] [:map [::root node]]]}
  [filename]
  (let [scene      (Assimp/aiImportFile ^String filename (bit-or Assimp/aiProcess_Triangulate Assimp/aiProcess_CalcTangentSpace))
        materials  (mapv #(decode-material scene %) (range (.mNumMaterials scene)))
        meshes     (mapv #(decode-mesh scene materials %) (range (.mNumMeshes scene)))
        textures   (mapv #(decode-texture scene %) (range (.mNumTextures scene)))
        animations (into {} (mapv #(decode-animation scene %) (range (.mNumAnimations scene))))
        result     {::root (decode-node (.mRootNode scene))
                    ::materials materials
                    ::meshes meshes
                    ::textures textures
                    ::animations animations}]
    (Assimp/aiReleaseImport scene)
    result))


(defn- get-node-path-subtree
  "Get path in sub-tree of model to node with given name"
  {:malli/schema [:=> [:cat [:map-of :keyword :some] :string] [:maybe [:vector :some]]]}
  [node path-prefix node-name]
  (if (= (::name node) node-name)
    path-prefix
    (let [children (::children node)
          indices  (range (count children))]
      (some identity
            (map (fn [child index] (get-node-path-subtree child (conj (conj path-prefix ::children) index) node-name))
                 children
                 indices)))))


(defn get-node-path
  "Get path in model to node with given name"
  {:malli/schema [:=> [:cat [:map [::root :some]] :string] [:maybe [:vector :some]]]}
  [scene node-name]
  (get-node-path-subtree (::root scene) [::root] node-name))


(defn- get-node-transform-subtree
  "Get sub-transform of a node"
  {:malli/schema [:=> [:cat [:map-of :keyword :some] :string] [:maybe fmat4]]}
  [node node-name]
  (if (= (::name node) node-name)
    (::transform node)
    (let [sub-transform (some identity (map (fn [child] (get-node-transform-subtree child node-name)) (::children node)))]
      (and sub-transform (mulm (::transform node) sub-transform)))))


(defn get-node-transform
  "Get global transform of a node"
  {:malli/schema [:=> [:cat [:map [::root :some]] :string] [:maybe fmat4]]}
  [scene node-name]
  (get-node-transform-subtree (::root scene) node-name))


(defn- load-mesh-into-opengl
  "Load index and vertex data into OpenGL buffer"
  {:malli/schema [:=> [:cat fn? mesh material] [:map [:vao vertex-array-object]]]}
  [program-selection mesh material]
  (assoc mesh ::vao (make-vertex-array-object (program-selection material) (::indices mesh) (::vertices mesh)
                                              (::attributes mesh))))


(defn- load-meshes-into-opengl
  "Load meshes into OpenGL buffers"
  {:malli/schema [:=> [:cat [:map [:root node]] fn?] [:map [:root node] [:meshes [:vector mesh]]]]}
  [scene program-selection]
  (let [material (fn lookup-material-for-mesh [mesh] (nth (::materials scene) (::material-index mesh)))]
    (update scene ::meshes
            (fn load-meshes-into-opengl [meshes] (mapv #(load-mesh-into-opengl program-selection % (material %)) meshes)))))


(defn- load-textures-into-opengl
  "Load images into OpenGL textures"
  {:malli/schema [:=> [:cat [:map [::root node]]] [:map [::root node] [::textures [:vector texture-2d]]]]}
  [scene]
  (update scene ::textures
          (fn load-textures-into-opengl [textures]
              (mapv (fn  [image] (generate-mipmap (make-rgba-texture :sfsim.texture/linear :sfsim.texture/repeat image)))
                    textures))))


(defn- propagate-texture
  "Add color and normal textures to material"
  {:malli/schema [:=> [:cat material [:vector texture-2d]] material]}
  [material textures]
  (merge material {::colors  (some->> material ::color-texture-index (nth textures))
                   ::normals (some->> material ::normal-texture-index (nth textures))}))


(defn- propagate-textures
  "Add OpenGL textures to materials"
  {:malli/schema [:=> [:cat [:map [:root node]]] [:map [::root node] [::materials [:vector material]]]]}
  [scene]
  (update scene ::materials (fn propagate-textures [materials] (mapv #(propagate-texture % (::textures scene)) materials))))


(defn- propagate-materials
  "Add material information to meshes"
  {:malli/schema [:=> [:cat [:map [::root node]]] [:map [::root node] [::meshes [:vector mesh]]]]}
  [scene]
  (update scene ::meshes
          (fn propagate-materials [meshes] (mapv #(assoc % ::material (nth (::materials scene) (::material-index %))) meshes))))


(defn load-scene-into-opengl
  "Load indices and vertices into OpenGL buffers"
  {:malli/schema [:=> [:cat fn? [:map [::root node]]] [:map [::root node]]]}
  [program-selection scene]
  (-> scene
      load-textures-into-opengl
      (load-meshes-into-opengl program-selection)
      propagate-textures
      propagate-materials))


(defn destroy-scene
  "Destroy vertex array objects of scene"
  {:malli/schema [:=> [:cat [:map [::root node]]] :nil]}
  [scene]
  (doseq [mesh (::meshes scene)] (destroy-vertex-array-object (::vao mesh)))
  (doseq [texture (::textures scene)] (destroy-texture texture)))


(def mesh-vars
  (m/schema [:map [::program :int]
             [::transform fmat4]
             [::internal-transform fmat4]
             [::texture-offset :int]
             [::scene-shadow-matrices [:vector fmat4]]]))


(defn render-scene
  "Render meshes of specified scene"
  {:malli/schema [:=> [:cat [:=> [:cat material] :nil] :int :map [:vector fmat4] [:map [::root node]] ifn?
                       [:? [:cat fmat4 fmat4 node]]] :nil]}
  ([program-selection texture-offset render-vars scene-shadow-matrices scene callback]
   (render-scene program-selection texture-offset render-vars scene-shadow-matrices scene callback (::transform (::root scene))
                 (eye 4) (::root scene)))
  ([program-selection texture-offset render-vars scene-shadow-matrices scene callback transform internal-transform node]
   (doseq [child-node (::children node)]
     (render-scene program-selection texture-offset render-vars scene-shadow-matrices scene callback
                   (mulm transform (::transform child-node)) (mulm internal-transform (::transform child-node))
                   child-node))
   (doseq [mesh-index (::mesh-indices node)]
     (let [mesh                (nth (::meshes scene) mesh-index)
           material            (assoc (::material mesh) ::num-scene-shadows (count scene-shadow-matrices))
           program             (program-selection material)]
       (callback material (assoc render-vars
                                 ::program program
                                 ::texture-offset texture-offset
                                 ::transform transform
                                 ::internal-transform internal-transform
                                 ::scene-shadow-matrices scene-shadow-matrices))
       (render-triangles (::vao mesh))))))


(defn- interpolate-frame
  "Interpolate between pose frames"
  {:malli/schema [:=> [:cat [:vector [:map [::time :double]]] :double :keyword fn?] :some]}
  [key-frames ^double t k lerp]
  (let [n       (count key-frames)
        t0      (::time (first key-frames))
        t1      (::time (last key-frames))
        delta-t (if (<= n 1) 1.0 (/ ^double (- ^double t1 ^double t0) (dec n)))
        index   (int (floor (/ (- t ^double t0) ^double delta-t)))]
    (cond (<  index 0)       (k (first key-frames))
          (>= index (dec n)) (k (last key-frames))
          :else              (let [frame-a  (nth key-frames index)
                                   frame-b  (nth key-frames (inc index))
                                   weight   (/ ^double (- ^double (::time frame-b) ^double t) ^double delta-t)]
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
  (interpolate-frame key-frames t ::position
                     (fn weight-positions [a b weight]
                         (add (mult a weight) (mult b (- 1.0 ^double weight))))))


(defn interpolate-rotation
  "Interpolate between rotation frames"
  {:malli/schema [:=> [:cat [:vector rotation-key] :double] quaternion]}
  [key-frames t]
  (interpolate-frame key-frames t ::rotation
                     (fn weight-rotations [a b weight]
                         (q/+ (q/scale a weight) (q/scale (nearest-quaternion b a) (- 1.0 ^double weight))))))


(defn interpolate-scaling
  "Interpolate between scaling frames"
  {:malli/schema [:=> [:cat [:vector scaling-key] :double] fvec3]}
  [key-frames t]
  (interpolate-frame key-frames t ::scaling
                     (fn weight-scales [a b weight]
                         (add (mult a weight) (mult b (- 1.0 ^double weight))))))


(defn interpolate-transformation
  "Determine transformation matrix for a given channel and time"
  {:malli/schema [:=> [:cat channel :double] fmat4]}
  [channel t]
  (let [position (interpolate-position (::position-keys channel) t)
        rotation (interpolate-rotation (::rotation-keys channel) t)
        scaling (interpolate-scaling (::scaling-keys channel) t)]
    (transformation-matrix (mulm (quaternion->matrix rotation) (diagonal scaling)) position)))


(defn animations-frame
  "Create hash map with transforms for objects of scene given a hash map of animation times"
  {:malli/schema [:=> [:cat [:map [::animations :map]] [:map-of :string :double]] [:map-of :string :some]]}
  [scene animation-times]
  (let [animations (::animations scene)]
    (or (apply merge
               (for [[animation-name animation-time] animation-times]
                    (let [animation (animations animation-name)]
                      (into {} (for [[object-name channel] (::channels animation)]
                                    [object-name (interpolate-transformation channel animation-time)])))))
        {})))


(defn- apply-transforms-node
  "Apply hash map of transforms to node and children"
  {:malli/schema [:=> [:cat :map [:map-of :string :some]] :map]}
  [node transforms]
  (assoc node
         ::transform (or (transforms (::name node)) (::transform node))
         ::children (mapv #(apply-transforms-node % transforms) (::children node))))


(defn apply-transforms
  "Apply hash map of transforms to scene in order to animate it"
  {:malli/schema [:=> [:cat [:map [::root :map]] [:map-of :string :some]] [:map [::root :map]]]}
  [scene transforms]
  (assoc scene ::root (apply-transforms-node (::root scene) transforms)))


(defn material-type
  "Determine information for dispatching to correct render method"
  [{::keys [color-texture-index normal-texture-index]}]
  [(boolean color-texture-index) (boolean normal-texture-index)])


(defn material-and-shadow-type
  "Determine information for selecting correct shader program"
  [{::keys [color-texture-index normal-texture-index num-scene-shadows]}]
  [(boolean color-texture-index) (boolean normal-texture-index) (or num-scene-shadows 0)])


(def vertex-scene (template/fn [textured bump num-scene-shadows] (slurp "resources/shaders/model/vertex.glsl")))


(defn fragment-scene
  "Fragment shader for rendering scene in atmosphere"
  {:malli/schema [:=> [:cat :boolean :boolean N N0] render/shaders]}
  [textured bump num-steps num-scene-shadows]
  [(overall-shading num-steps (overall-shading-parameters num-scene-shadows))
   (percentage-closer-filtering "average_scene_shadow" "scene_shadow_lookup" "scene_shadow_size" [["sampler2DShadow" "shadow_map"]])
   (shadow-lookup "scene_shadow_lookup" "scene_shadow_size") phong attenuation-point surface-radiance-function cloud-overlay
   (template/eval (slurp "resources/shaders/model/fragment.glsl")
                  {:textured textured :bump bump :num-scene-shadows num-scene-shadows})])


(def scene-renderer
  (m/schema [:map [::programs [:map-of [:tuple :boolean :boolean :int] :int]]
             [::texture-offset :int]]))


(def data
  (m/schema [:map [:sfsim.opacity/data [:map [:sfsim.opacity/num-steps N]
                                             [:sfsim.opacity/scene-shadow-counts [:vector N0]]]]
                  [:sfsim.clouds/data  [:map [:sfsim.clouds/perlin-octaves [:vector :double]]
                                             [:sfsim.clouds/cloud-octaves [:vector :double]]]]
                  [:sfsim.model/data [:map [::object-radius :double]]]]))


(defn setup-scene-samplers
  "Set up uniform samplers for scene rendering program"
  {:malli/schema [:=> [:cat :int :int :int :boolean :boolean] :nil]}
  [program texture-offset num-scene-shadows textured bump]
  (if textured
    (do
      (uniform-sampler program "colors" (+ ^long texture-offset ^long num-scene-shadows))
      (when bump (uniform-sampler program "normals" (+ ^long texture-offset ^long num-scene-shadows 1))))
    (when bump (uniform-sampler program "normals" (+ ^long texture-offset ^long num-scene-shadows)))))


(defn setup-scene-static-uniforms
  "Set up static uniforms of scene rendering program"
  {:malli/schema [:=> [:cat :int :int :int :boolean :boolean data] :nil]}
  [program texture-offset num-scene-shadows textured bump data]
  (let [render-config   (:sfsim.render/config data)
        planet-config   (:sfsim.planet/config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        cloud-data      (:sfsim.clouds/data data)
        shadow-data     (:sfsim.opacity/data data)]
    (use-program program)
    (setup-atmosphere-uniforms program atmosphere-luts 0 true)
    (uniform-sampler program "clouds" 4)
    (uniform-sampler program "dist" 5)
    (uniform-int program "shadow_size" (:sfsim.opacity/shadow-size shadow-data))
    (uniform-int program "scene_shadow_size" (:sfsim.opacity/scene-shadow-size shadow-data))
    (uniform-float program "shadow_bias" (:sfsim.opacity/shadow-bias shadow-data))
    (doseq [i (range num-scene-shadows)]
      (uniform-sampler program (str "scene_shadow_map_" (inc ^long i)) (+ ^long i 6)))
    (setup-shadow-and-opacity-maps program shadow-data (+ 6 ^long num-scene-shadows))
    (setup-scene-samplers program texture-offset num-scene-shadows textured bump)
    (uniform-int program "cloud_subsampling" (:sfsim.render/cloud-subsampling render-config))
    (uniform-float program "depth_sigma" (:sfsim.clouds/depth-sigma cloud-data))
    (uniform-float program "min_depth_exponent" (:sfsim.clouds/min-depth-exponent cloud-data))
    (uniform-float program "specular" (:sfsim.render/specular render-config))
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    (uniform-float program "albedo" (:sfsim.planet/albedo planet-config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))))


(defn make-scene-program
  "Create and setup scene rendering program"
  {:malli/schema [:=> [:cat :boolean :boolean N0 N0 data] :int]}
  [textured bump texture-offset num-scene-shadows data]
  (let [num-steps             (-> data :sfsim.opacity/data :sfsim.opacity/num-steps)
        fragment-shader       (fragment-scene textured bump num-steps num-scene-shadows)
        program               (make-program :sfsim.render/vertex [(vertex-scene textured bump num-scene-shadows)]
                                            :sfsim.render/fragment fragment-shader)]
    (setup-scene-static-uniforms program texture-offset num-scene-shadows textured bump data)
    program))


(defn make-scene-renderer
  "Create set of programs for rendering different materials"
  {:malli/schema [:=> [:cat data] scene-renderer]}
  [data]
  (let [num-steps            (-> data :sfsim.opacity/data :sfsim.opacity/num-steps)
        scene-shadow-counts  (-> data :sfsim.opacity/data :sfsim.opacity/scene-shadow-counts)
        model-data           (::data data)
        cloud-data           (:sfsim.clouds/data data)
        render-config        (:sfsim.render/config data)
        atmosphere-luts      (:sfsim.atmosphere/luts data)
        texture-offset       (+ 6 (* 2 ^long num-steps))
        variations           (for [textured [false true] bump [false true] num-scene-shadows scene-shadow-counts]
                               [textured bump num-scene-shadows])
        programs             (mapv #(make-scene-program (first %) (second %) texture-offset (third %) data) variations)]
    {::programs              (zipmap variations programs)
     ::texture-offset        texture-offset
     ::data                  model-data
     :sfsim.clouds/data      cloud-data
     :sfsim.render/config    render-config
     :sfsim.atmosphere/luts  atmosphere-luts}))


(defn- remove-empty-children
  "Recursively remove empty children"
  {:malli/schema [:=> [:cat node] node]}
  [node]
  (let [children (vec (remove nil? (map remove-empty-children (::children node))))]
    (if (and (empty? children) (empty? (::mesh-indices node)))
      nil
      (assoc node ::children children))))


(defn remove-empty-meshes
  "Remove empty meshes from scene"
  {:malli/schema [:=> [:cat [:map [::root node]]] [:map [::root node]]]}
  [scene]
  (update scene ::root remove-empty-children))


(defn- extract-empty
  "Convert empty node to vector"
  [node]
  (let [v (mulv (::transform node) (vec4 0 0 0 1))]
    (vec3 (.x ^Vec4 v) (.y ^Vec4 v) (.z ^Vec4 v))))


(defn- extract-hull
  "Get empty coordinate systems from empty meshes"
  [node]
  (if (and (empty? (::mesh-indices node)) (every? #(empty? (::children %)) (::children node)) (> (count (::children node)) 3))
    (assoc (select-keys node [::transform ::name]) ::children (mapv extract-empty (::children node)))
    nil))


(defn- extract-hulls
  "Convert empty meshes to convex hulls"
  [root]
  (let [children (map extract-hull (::children root))]
    (assoc (select-keys root [::transform ::name]) ::children (vec (remove nil? children)))))


(defn empty-meshes-to-points
  "Convert empty meshes to points of for convex hulls"
  [scene]
  (extract-hulls (::root scene)))


(defn load-scene
  "Load glTF scene and load it into OpenGL"
  {:malli/schema [:=> [:cat scene-renderer [:map [::root node]]] [:map [::root node]]]}
  [scene-renderer model]
  (let [gltf-object   (remove-empty-meshes model)
        opengl-object (load-scene-into-opengl (comp (::programs scene-renderer) material-and-shadow-type) gltf-object)]
    opengl-object))


(def gltf-to-aerodynamic (rotation-matrix aerodynamics/gltf-to-aerodynamic))


(defn setup-camera-world-and-shadow-matrices
  {:malli/schema [:=> [:cat :int fmat4 fmat4 fmat4 [:vector fmat4]] :nil]}
  [program transform internal-transform camera-to-world scene-shadow-matrices]
  (let [object-to-camera (mulm (inverse camera-to-world) transform)]
    (use-program program)
    (uniform-matrix4 program "object_to_world" transform)
    (uniform-matrix4 program "object_to_scene" (mulm gltf-to-aerodynamic internal-transform))
    (uniform-matrix4 program "object_to_camera" object-to-camera)
    (doseq [i (range (count scene-shadow-matrices))]
           (uniform-matrix4 program
                            (str "object_to_shadow_map_" (inc ^long i))
                            (mulm (nth scene-shadow-matrices i) internal-transform)))))


(defmulti render-mesh (fn [material _render-vars] (material-type material)))
(m/=> render-mesh [:=> [:cat material mesh-vars] :nil])


(defmethod render-mesh [false false]
  [{::keys [diffuse]} {::keys [program transform internal-transform scene-shadow-matrices] :as render-vars}]
  (setup-camera-world-and-shadow-matrices program transform internal-transform (:sfsim.render/camera-to-world render-vars)
                                          scene-shadow-matrices)
  (uniform-vector3 program "diffuse_color" diffuse))


(defmethod render-mesh [true false]
  [{::keys [colors]} {::keys [program texture-offset transform internal-transform scene-shadow-matrices] :as render-vars}]
  (setup-camera-world-and-shadow-matrices program transform internal-transform (:sfsim.render/camera-to-world render-vars)
                                          scene-shadow-matrices)
  (use-textures {texture-offset colors}))


(defmethod render-mesh [false true]
  [{::keys [diffuse normals]} {::keys [program texture-offset transform internal-transform scene-shadow-matrices] :as render-vars}]
  (setup-camera-world-and-shadow-matrices program transform internal-transform (:sfsim.render/camera-to-world render-vars)
                                          scene-shadow-matrices)
  (uniform-vector3 program "diffuse_color" diffuse)
  (use-textures {texture-offset normals}))


(defmethod render-mesh [true true]
  [{::keys [colors normals]} {::keys [program texture-offset transform internal-transform scene-shadow-matrices] :as render-vars}]
  (setup-camera-world-and-shadow-matrices program transform internal-transform (:sfsim.render/camera-to-world render-vars)
                                          scene-shadow-matrices)
  (use-textures {texture-offset colors (inc ^long texture-offset) normals}))


(def scene-shadow (m/schema [:map [::matrices shadow-patch] [::shadows texture-2d]]))


(defn render-scenes
  "Render a list of scenes"
  {:malli/schema [:=> [:cat scene-renderer render-vars model-vars shadow-vars [:vector scene-shadow]
                            [:map [:sfsim.clouds/distance texture-2d]] texture-2d [:vector [:map [::root node]]]]
                      :nil]}
  [scene-renderer render-vars model-vars shadow-vars scene-shadows geometry clouds scenes]
  (let [render-config      (:sfsim.render/config scene-renderer)
        cloud-data         (:sfsim.clouds/data scene-renderer)
        atmosphere-luts    (:sfsim.atmosphere/luts scene-renderer)
        camera-to-world    (:sfsim.render/camera-to-world render-vars)
        texture-offset     (::texture-offset scene-renderer)
        dist               (:sfsim.clouds/distance geometry)
        num-scene-shadows  (count scene-shadows)
        world-to-camera    (inverse camera-to-world)]
    (doseq [program (vals (::programs scene-renderer))]
      (use-program program)
      (uniform-float program "lod_offset" (lod-offset render-config cloud-data render-vars))
      (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
      (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
      (uniform-matrix4 program "world_to_camera" world-to-camera)
      (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
      (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
      (uniform-float program "opacity_cutoff" (:sfsim.opacity/opacity-cutoff shadow-vars))
      (uniform-int program "overlay_width" (:sfsim.render/overlay-width render-vars))
      (uniform-int program "overlay_height" (:sfsim.render/overlay-height render-vars))
      (setup-shadow-matrices program shadow-vars))
    (use-textures {0 (:sfsim.atmosphere/transmittance atmosphere-luts) 1 (:sfsim.atmosphere/scatter atmosphere-luts)
                   2 (:sfsim.atmosphere/mie atmosphere-luts) 3 (:sfsim.atmosphere/surface-radiance atmosphere-luts)
                   4 clouds 5 dist})
    (doseq [i (range num-scene-shadows)]
      (use-textures {(+ ^long i 6) (::shadows (nth scene-shadows i))}))
    (use-textures (zipmap (drop (+ 6 num-scene-shadows) (range))
                          (concat (:sfsim.opacity/shadows shadow-vars) (:sfsim.opacity/opacities shadow-vars))))
    (doseq [scene scenes]
      (render-scene (comp (::programs scene-renderer) material-and-shadow-type)
                    (+ ^long texture-offset num-scene-shadows)
                    render-vars
                    (mapv (fn [s] (:sfsim.matrix/object-to-shadow-map (::matrices s))) scene-shadows)
                    scene render-mesh))))


(defn destroy-scene-renderer
  {:malli/schema [:=> [:cat scene-renderer] :nil]}
  [{::keys [programs]}]
  (doseq [program (vals programs)] (destroy-program program)))


(defn make-model-vars
  "Create hashmap with model configuration and variables"
  {:malli/schema [:=> [:cat :double :double :double] model-vars]}
  [time_ pressure throttle]
  {::time time_ ::pressure pressure ::throttle throttle})


(defn make-scene-render-vars
  "Create hashmap with render variables for rendering a scene outside the atmosphere"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/fov :double]] N N fvec3 quaternion fvec3 fvec3 quaternion
                       model-data model-vars] render-vars]}
  [render-config window-width window-height camera-position camera-orientation light-direction object-position object-orientation
   model-data model-vars]
  (let [min-z-near           (:sfsim.render/min-z-near render-config)
        camera-to-world      (transformation-matrix (quaternion->matrix camera-orientation) camera-position)
        world-to-camera      (inverse camera-to-world)
        object-camera-vector (mulv world-to-camera (vec3->vec4 object-position 1.0))
        object-depth         (- ^double (object-camera-vector 2))
        object-radius        (::object-radius model-data)
        time_                (::time model-vars)
        pressure             (::pressure model-vars)
        z-near               (max ^double (- ^double object-depth ^double object-radius) ^double min-z-near)
        z-far                (+ ^double z-near ^double object-radius ^double object-radius)]
    (make-render-vars render-config window-width window-height camera-position camera-orientation light-direction
                      object-position object-orientation z-near z-far time_ pressure)))


(defn vertex-shadow-scene
  "Vertex shader for rendering scene shadow maps"
  [textured bump]
  [shrink-shadow-index (template/eval (slurp "resources/shaders/model/vertex-shadow.glsl") {:textured textured :bump bump})])


(def fragment-shadow-scene (slurp "resources/shaders/model/fragment-shadow.glsl"))


(defn make-scene-shadow-program
  {:malli/schema [:=> [:cat :boolean :boolean] :int]}
  [textured bump]
  (make-program :sfsim.render/vertex [(vertex-shadow-scene textured bump)]
                :sfsim.render/fragment [fragment-shadow-scene]))


(def scene-shadow-renderer
  (m/schema [:map [::programs [:map-of [:tuple :boolean :boolean] :int]]
             [::size N]
             [::object-radius :double]]))


(defn make-scene-shadow-renderer
  "Create renderer for rendering scene-shadows"
  {:malli/schema [:=> [:cat N :double] scene-shadow-renderer]}
  [size object-radius]
  (let [variations (for [textured [false true] bump [false true]] [textured bump])
        programs   (mapv #(make-scene-shadow-program (first %) (second %)) variations)]
    {::programs      (zipmap variations programs)
     ::size          size
     ::object-radius object-radius}))


(defn render-depth
  {:malli/schema [:=> [:cat material mesh-vars] :nil]}
  [_material {::keys [program transform] :as render-vars}]
  (use-program program)
  (uniform-matrix4 program "object_to_light" (mulm (:sfsim.matrix/object-to-shadow-ndc render-vars) transform)))


(defn render-shadow-map
  "Render shadow map for an object"
  {:malli/schema [:=> [:cat scene-shadow-renderer :map [:map [::root node]]] texture-2d]}
  [renderer shadow-vars scene]
  (let [size           (::size renderer)
        centered-scene (assoc-in scene [::root ::transform] (eye 4))]
    (doseq [program (vals (::programs renderer))]
      (use-program program)
      (uniform-int program "shadow_size" size))
    (texture-render-depth size size
                          (clear)
                          (render-scene (comp (::programs renderer) material-type) 0 shadow-vars [] centered-scene
                                        render-depth))))


(defn scene-shadow-map
  "Determine shadow matrices and render shadow map for object"
  {:malli/schema [:=> [:cat scene-shadow-renderer fvec3 [:map [::root node]]] scene-shadow]}
  [renderer light-direction scene]
  (let [object-to-world (get-in scene [:sfsim.model/root :sfsim.model/transform])
        object-radius   (::object-radius renderer)
        shadow-matrices (shadow-patch-matrices object-to-world light-direction object-radius)
        shadow-map      (render-shadow-map renderer shadow-matrices scene)]
    {::matrices shadow-matrices
     ::shadows  shadow-map}))


(defn destroy-scene-shadow-map
  "Delete scene shadow map texture"
  {:malli/schema [:=> [:cat scene-shadow] :nil]}
  [{::keys [shadows]}]
  (destroy-texture shadows))


(defn destroy-scene-shadow-renderer
  "Destroy shadow renderer"
  [{::keys [programs]}]
  (doseq [program (vals programs)] (destroy-program program)))


(def vertex-geometry-scene
  (template/fn [textured bump] (slurp "resources/shaders/model/vertex-geometry.glsl")))


(def fragment-geometry-scene
  (slurp "resources/shaders/model/fragment-geometry.glsl"))


(defn make-scene-geometry-program
  "Create program to render scene points and distances"
  [textured bump]
  (make-program :sfsim.render/vertex [(vertex-geometry-scene textured bump)]
                :sfsim.render/fragment [fragment-geometry-scene]))


(defn make-scene-geometry-renderer
  "Create renderer to render scene points and distances"
  []
  (let [variations (for [textured [false true] bump [false true]] [textured bump])
        programs   (mapv #(make-scene-geometry-program (first %) (second %)) variations)]
    {::programs (zipmap variations programs)}))


(defn render-geometry-mesh
  "Render function to render points and distances for a mesh (part of scene)"
  [_material {:sfsim.model/keys [program transform] :as render-vars}]
  (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
    (use-program program)
    (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))))


(defn render-scene-geometry
  "Render geometry (points and distances) for a scene"
  [geometry-renderer render-vars scene]
  (let [projection       (:sfsim.render/overlay-projection render-vars)]
    (doseq [program (vals (::programs geometry-renderer))]
           (use-program program)
           (uniform-matrix4 program "projection" projection))
    (render-scene (comp (:sfsim.model/programs geometry-renderer) material-type) 0
                  render-vars [] scene render-geometry-mesh)))


(defn destroy-scene-geometry-renderer
  "Destroy scene geometry renderer"
  [{::keys [programs]}]
  (doseq [program (vals programs)] (destroy-program program)))


(defn make-joined-geometry-renderer
  "Joined geometry renderer for rendering scene, planet, and atmosphere geometry information"
  [data]
  (let [scene-renderer (make-scene-geometry-renderer)
        planet-renderer (make-planet-geometry-renderer data)
        atmosphere-renderer (make-atmosphere-geometry-renderer)]
    {::scene-renderer scene-renderer
     ::planet-renderer planet-renderer
     ::atmosphere-renderer atmosphere-renderer}))


(defn destroy-joined-geometry-renderer
  "Destroy joined geometry renderer"
  [{::keys [scene-renderer planet-renderer atmosphere-renderer]}]
  (destroy-scene-geometry-renderer scene-renderer)
  (destroy-planet-geometry-renderer planet-renderer)
  (destroy-atmosphere-geometry-renderer atmosphere-renderer))


(defn render-joined-geometry
  "Render joined geometry of scene, planet, and atmosphere"
  [{::keys [scene-renderer planet-renderer atmosphere-renderer]} scene-render-vars planet-render-vars model tree]
  (let [model-covers-planet? (< ^double (:sfsim.render/z-near scene-render-vars) ^double (:sfsim.render/z-near planet-render-vars))]
    (render-cloud-geometry (:sfsim.render/overlay-width planet-render-vars) (:sfsim.render/overlay-height planet-render-vars)
                           (with-stencils  ; 0x4: model, 0x2: planet, 0x1: atmosphere
                             (when model
                               (with-stencil-op-ref-and-mask GL11/GL_ALWAYS 0x4 0x4
                                 (render-scene-geometry scene-renderer
                                                        (if model-covers-planet? scene-render-vars planet-render-vars) model)))
                             (when tree
                               (with-stencil-op-ref-and-mask GL11/GL_GREATER 0x2 (if model-covers-planet? 0x6 0x2)
                                 (render-planet-geometry planet-renderer planet-render-vars tree)))
                             (with-stencil-op-ref-and-mask GL11/GL_GREATER 0x1 0x7
                               (render-atmosphere-geometry atmosphere-renderer planet-render-vars))))))


(defn- build-bsp-tree
  "Recursively build binary space partitioning (BSP) tree"
  [bsp-node]
  (let [node-name (::name bsp-node)
        transform (::transform bsp-node)
        children  (::children bsp-node)]
    (if (seq children)
      (let [child-groups (group-by #(pos? ^double (nth (get-translation (::transform %)) 1)) children) ]  ; Blender Z is glTF Y
        {::name node-name
         ::transform transform
         ::back-children (mapv build-bsp-tree (get child-groups false))
         ::front-children (mapv build-bsp-tree (get child-groups true))})
      {::name node-name
       ::transform transform})))


(defn get-bsp-tree
  "Get binary space partitioning (BSP) tree from model"
  [scene bsp-root-name]
  (let [bsp-root-path    (get-node-path scene bsp-root-name)
        bsp-root         (get-in scene bsp-root-path)]
    (build-bsp-tree bsp-root)))


(defn bsp-render-order
  "Get BSP render order given camera origin"
  [bsp-node origin]
  (if (and (contains? bsp-node ::back-children) (contains? bsp-node ::front-children))
    (let [transformed-origin     (vec4->vec3 (mulv (inverse (::transform bsp-node)) (vec3->vec4 origin 1.0)))
          ordered-front-children (mapcat #(bsp-render-order % transformed-origin) (::front-children bsp-node))
          ordered-back-children  (mapcat #(bsp-render-order % transformed-origin) (::back-children bsp-node))]
      (if (pos? ^double (nth transformed-origin 1))  ; Blender Z is glTF Y
        (concat ordered-front-children ordered-back-children)
        (concat ordered-back-children ordered-front-children)))
    [(::name bsp-node)]))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
