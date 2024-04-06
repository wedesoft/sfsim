(ns sfsim.model
    "Import glTF models into Clojure"
    (:require [clojure.math :refer (floor)]
              [comb.template :as template]
              [malli.core :as m]
              [fastmath.matrix :refer (mat4x4 mulm mulv eye diagonal inverse)]
              [fastmath.vector :refer (vec3 mult add)]
              [sfsim.matrix :refer (transformation-matrix quaternion->matrix shadow-patch-matrices shadow-patch vec3->vec4 fvec3
                                    fmat4)]
              [sfsim.quaternion :refer (->Quaternion quaternion) :as q]
              [sfsim.texture :refer (make-rgba-texture destroy-texture texture-2d)]
              [sfsim.render :refer (make-vertex-array-object destroy-vertex-array-object render-triangles vertex-array-object
                                    make-program destroy-program use-program uniform-int uniform-float uniform-matrix4
                                    uniform-vector3 uniform-sampler use-textures setup-shadow-and-opacity-maps
                                    setup-shadow-matrices render-vars make-render-vars texture-render-depth clear) :as render]
              [sfsim.clouds :refer (environmental-shading cloud-point setup-cloud-render-uniforms setup-cloud-sampling-uniforms
                                    lod-offset)]
              [sfsim.atmosphere :refer (attenuation-point setup-atmosphere-uniforms)]
              [sfsim.planet :refer (surface-radiance-function shadow-vars)]
              [sfsim.shaders :refer (phong shrink-shadow-index)]
              [sfsim.image :refer (image)]
              [sfsim.util :refer (N0 N)])
    (:import [org.lwjgl.assimp Assimp AIMesh AIMaterial AIColor4D AINode AITexture AIString AIVector3D$Buffer AIAnimation
              AINodeAnim AIMatrix4x4 AIFace AIVector3D AIScene AIVectorKey AIQuatKey]
             [org.lwjgl.stb STBImage]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn- decode-matrix
  "Convert AIMatrix4x4 to mat4x4"
  {:malli/schema [:=> [:cat :some] fmat4]}
  [m]
  (mat4x4 (.a1 ^AIMatrix4x4 m) (.a2 ^AIMatrix4x4 m) (.a3 ^AIMatrix4x4 m) (.a4 ^AIMatrix4x4 m)
          (.b1 ^AIMatrix4x4 m) (.b2 ^AIMatrix4x4 m) (.b3 ^AIMatrix4x4 m) (.b4 ^AIMatrix4x4 m)
          (.c1 ^AIMatrix4x4 m) (.c2 ^AIMatrix4x4 m) (.c3 ^AIMatrix4x4 m) (.c4 ^AIMatrix4x4 m)
          (.d1 ^AIMatrix4x4 m) (.d2 ^AIMatrix4x4 m) (.d3 ^AIMatrix4x4 m) (.d4 ^AIMatrix4x4 m)))

(def node (m/schema [:schema {:registry {::node [:map [::name :string]
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
    (map #(.get indices ^long %) (range (.mNumIndices ^AIFace face)))))

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
        (fn decode-vertex [^long i]
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

(def material (m/schema [:map [::diffuse fvec3]
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

(def mesh (m/schema [:map [::indices [:vector N0]]
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
    (.get buffer data)
    (.flip buffer)
    (STBImage/stbi_image_free buffer)
    #:sfsim.image{:width (aget width 0) :height (aget height 0) :channels (aget channels 0) :data data}))

(def position-key (m/schema [:map [::time :double] [::position fvec3]]))

(defn- decode-position-key
  "Read a position key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] position-key]}
  [channel ticks-per-second i]
  (let [position-key (.get (.mPositionKeys ^AINodeAnim channel) ^long i)
        position     (.mValue ^AIVectorKey position-key)]
    {::time (/ (.mTime ^AIVectorKey position-key) ticks-per-second)
     ::position (vec3 (.x position) (.y position) (.z position))}))

(def rotation-key (m/schema [:map [::time :double] [::rotation quaternion]]))

(defn- decode-rotation-key
  "Read a rotation key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] rotation-key]}
  [channel ticks-per-second i]
  (let [rotation-key (.get (.mRotationKeys ^AINodeAnim channel) ^long i)
        rotation     (.mValue ^AIQuatKey rotation-key)]
    {::time (/ (.mTime ^AIQuatKey rotation-key) ticks-per-second)
     ::rotation (->Quaternion (.w rotation) (.x rotation) (.y rotation) (.z rotation))}))

(def scaling-key (m/schema [:map [::time :double] [::scaling fvec3]]))

(defn- decode-scaling-key
  "Read a scaling key from an animation channel"
  {:malli/schema [:=> [:cat :some :double N0] scaling-key]}
  [channel ticks-per-second i]
  (let [scaling-key (.get (.mScalingKeys ^AINodeAnim channel) ^long i)
        scaling     (.mValue ^AIVectorKey scaling-key)]
    {::time (/ (.mTime ^AIVectorKey scaling-key) ticks-per-second)
     ::scaling (vec3 (.x scaling) (.y scaling) (.z scaling))}))

(def channel (m/schema [:map [::position-keys [:vector position-key]]
                             [::rotation-keys [:vector rotation-key]]
                             [::scaling-keys [:vector scaling-key]]]))

(defn- decode-channel
  "Read channel of an animation"
  {:malli/schema [:=> [:cat :some :double N0] [:tuple :string channel]]}
  [animation ticks-per-second i]
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
      ::channels (into {} (map #(decode-channel animation ticks-per-second %) (range (.mNumChannels animation))))}]))

(defn read-gltf
  "Import a glTF model file"
  {:malli/schema [:=> [:cat :string] [:map [::root node]]]}
  [filename]
  (let [scene      (Assimp/aiImportFile ^String filename (bit-or Assimp/aiProcess_Triangulate Assimp/aiProcess_CalcTangentSpace))
        materials  (mapv #(decode-material scene %) (range (.mNumMaterials scene)))
        meshes     (mapv #(decode-mesh scene materials %) (range (.mNumMeshes scene)))
        textures   (mapv #(decode-texture scene %) (range (.mNumTextures scene)))
        animations (into {} (map #(decode-animation scene %) (range (.mNumAnimations scene))))
        result     {::root (decode-node (.mRootNode scene))
                    ::materials materials
                    ::meshes meshes
                    ::textures textures
                    ::animations animations}]
    (Assimp/aiReleaseImport scene)
    result))

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
    (fn load-textures-into-opengl [textures] (mapv #(make-rgba-texture :sfsim.texture/linear :sfsim.texture/repeat %) textures))))

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

(def mesh-vars (m/schema [:map [::program :int]
                               [::transform fmat4]
                               [::texture-offset :int]]))

(defn render-scene
  "Render meshes of specified scene"
  {:malli/schema [:=> [:cat [:=> [:cat material] :nil] :int :map [:map [::root node]] :any [:? [:cat fmat4 node]]] :nil]}
  ([program-selection texture-offset render-vars scene callback]
   (render-scene program-selection texture-offset render-vars scene callback (eye 4) (::root scene)))
  ([program-selection texture-offset render-vars scene callback transform node]
   (let [transform (mulm transform (::transform node))]
     (doseq [child-node (::children node)]
            (render-scene program-selection texture-offset render-vars scene callback transform child-node))
     (doseq [mesh-index (::mesh-indices node)]
            (let [mesh                (nth (::meshes scene) mesh-index)
                  material            (::material mesh)
                  program             (program-selection material)]
              (callback material (assoc render-vars ::program program ::texture-offset texture-offset ::transform transform))
              (render-triangles (::vao mesh)))))))

(defn- interpolate-frame
  "Interpolate between pose frames"
  {:malli/schema [:=> [:cat [:vector [:map [::time :double]]] :double :keyword fn?] :some]}
  [key-frames t k lerp]
  (let [n       (count key-frames)
        t0      (::time (first key-frames))
        t1      (::time (last key-frames))
        delta-t (if (<= n 1) 1.0 (/ (- t1 t0) (dec n)))
        index   (int (floor (/ (- t t0) delta-t)))]
    (cond (<  index 0)       (k (first key-frames))
          (>= index (dec n)) (k (last key-frames))
          :else              (let [frame-a  (nth key-frames index)
                                   frame-b  (nth key-frames (inc index))
                                   weight   (/ (- (::time frame-b) t) delta-t)]
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
    (fn weight-positions [a b weight] (add (mult a weight) (mult b (- 1.0 weight))))))

(defn interpolate-rotation
  "Interpolate between rotation frames"
  {:malli/schema [:=> [:cat [:vector rotation-key] :double] quaternion]}
  [key-frames t]
  (interpolate-frame key-frames t ::rotation
    (fn weight-rotations [a b weight] (q/+ (q/scale a weight) (q/scale (nearest-quaternion b a) (- 1.0 weight))))))

(defn interpolate-scaling
  "Interpolate between scaling frames"
  {:malli/schema [:=> [:cat [:vector scaling-key] :double] fvec3]}
  [key-frames t]
  (interpolate-frame key-frames t ::scaling
    (fn weight-scales [a b weight] (add (mult a weight) (mult b (- 1.0 weight))))))

(defn interpolate-transformation
  "Determine transformation matrix for a given channel and time"
  {:malli/schema [:=> [:cat channel :double] fmat4]}
  [channel t]
  (let [position (interpolate-position (::position-keys channel) t)
        rotation (interpolate-rotation (::rotation-keys channel) t)
        scaling (interpolate-scaling (::scaling-keys channel) t)]
    (transformation-matrix (mulm (quaternion->matrix rotation) (diagonal scaling)) position)))

(defn animations-frame
  "Create hash map with transforms for objects of model given a hash map of animation times"
  {:malli/schema [:=> [:cat [:map [::animations :map]] :map] [:map-of :string :some]]}
  [model animation-times]
  (let [animations (::animations model)]
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
  "Apply hash map of transforms to model in order to animate it"
  {:malli/schema [:=> [:cat [:map [::root :map]] [:map-of :string :some]] [:map [::root :map]]]}
  [model transforms]
  (assoc model ::root (apply-transforms-node (::root model) transforms)))

(defn material-type
  "Determine information for dispatching to correct shader or render method"
  [{::keys [color-texture-index normal-texture-index]}]
  (if color-texture-index
    (if normal-texture-index
      ::program-textured-bump
      ::program-textured-flat)
    (if normal-texture-index
      ::program-colored-bump
      ::program-colored-flat)))

(def vertex-model (template/fn [textured bump] (slurp "resources/shaders/model/vertex.glsl")))

(defn fragment-model
  "Fragment shader for rendering model in atmosphere"
  {:malli/schema [:=> [:cat :boolean :boolean N [:vector :double] [:vector :double]] render/shaders]}
  [textured bump num-steps perlin-octaves cloud-octaves]
  [(environmental-shading num-steps) phong attenuation-point surface-radiance-function
   (cloud-point num-steps perlin-octaves cloud-octaves)
   (template/eval (slurp "resources/shaders/model/fragment.glsl") {:textured textured :bump bump})])

(defn make-model-program
  {:malli/schema [:=> [:cat :boolean :boolean N [:vector :double] [:vector :double]] :int]}
  [textured bump num-steps perlin-octaves cloud-octaves]
  (make-program :sfsim.render/vertex [(vertex-model textured bump)]
                :sfsim.render/fragment (fragment-model textured bump num-steps perlin-octaves cloud-octaves)))

(def model-renderer (m/schema [:map [::program-colored-flat  :int]
                                    [::program-textured-flat :int]
                                    [::program-colored-bump  :int]
                                    [::program-textured-bump :int]
                                    [::programs [:vector :int]]
                                    [::texture-offset :int]]))

(def data (m/schema [:map [:sfsim.opacity/data [:map [:sfsim.opacity/num-steps N]]]
                          [:sfsim.clouds/data  [:map [:sfsim.clouds/perlin-octaves [:vector :double]]
                                                     [:sfsim.clouds/cloud-octaves [:vector :double]]]]]))

(defn setup-model-samplers
  "Set up uniform samplers for model rendering program"
  {:malli/schema [:=> [:cat :int :int :boolean :boolean] :nil]}
  [program texture-offset textured bump]
  (if textured
    (do
      (uniform-sampler program "colors" texture-offset)
      (when bump (uniform-sampler program "normals" (inc texture-offset))))
    (when bump (uniform-sampler program "normals" texture-offset))))

(defn setup-model-static-uniforms
  "Set up static uniforms of model rendering program"
  {:malli/schema [:=> [:cat :int :int :boolean :boolean data] :nil]}
  [program texture-offset textured bump data]
  (let [render-config   (:sfsim.render/config data)
        planet-config   (:sfsim.planet/config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        cloud-data      (:sfsim.clouds/data data)
        shadow-data     (:sfsim.opacity/data data)]
    (use-program program)
    (setup-atmosphere-uniforms program atmosphere-luts 0 true)
    (setup-cloud-render-uniforms program cloud-data 4)
    (setup-cloud-sampling-uniforms program cloud-data 7)
    (setup-shadow-and-opacity-maps program shadow-data 8)
    (setup-model-samplers program texture-offset textured bump)
    (uniform-float program "specular" (:sfsim.render/specular render-config))
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    (uniform-float program "albedo" (:sfsim.planet/albedo planet-config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))))

(defn make-model-renderer
  "Create set of programs for rendering different materials"
  {:malli/schema [:=> [:cat data] model-renderer]}
  [data]
  (let [num-steps             (-> data :sfsim.opacity/data :sfsim.opacity/num-steps)
        perlin-octaves        (-> data :sfsim.clouds/data :sfsim.clouds/perlin-octaves)
        cloud-octaves         (-> data :sfsim.clouds/data :sfsim.clouds/cloud-octaves)
        cloud-data            (:sfsim.clouds/data data)
        render-config         (:sfsim.render/config data)
        atmosphere-luts       (:sfsim.atmosphere/luts data)
        texture-offset        (+ 8 (* 2 num-steps))
        program-colored-flat  (make-model-program false false num-steps perlin-octaves cloud-octaves)
        program-textured-flat (make-model-program true  false num-steps perlin-octaves cloud-octaves)
        program-colored-bump  (make-model-program false true  num-steps perlin-octaves cloud-octaves)
        program-textured-bump (make-model-program true  true  num-steps perlin-octaves cloud-octaves)
        programs              [program-colored-flat program-textured-flat program-colored-bump program-textured-bump]]
    (setup-model-static-uniforms program-colored-flat  texture-offset false false data)
    (setup-model-static-uniforms program-textured-flat texture-offset true  false data)
    (setup-model-static-uniforms program-colored-bump  texture-offset false true  data)
    (setup-model-static-uniforms program-textured-bump texture-offset true  true  data)
    {::program-colored-flat  program-colored-flat
     ::program-textured-flat program-textured-flat
     ::program-colored-bump  program-colored-bump
     ::program-textured-bump program-textured-bump
     ::programs              programs
     ::texture-offset        texture-offset
     :sfsim.clouds/data      cloud-data
     :sfsim.render/config    render-config
     :sfsim.atmosphere/luts  atmosphere-luts}))

(defn load-scene
  "Load glTF model and load it into OpenGL"
  {:malli/schema [:=> [:cat model-renderer :string] [:map [::root node]]]}
  [model-renderer filename]
  (let [gltf-object   (read-gltf filename)
        opengl-object (load-scene-into-opengl (comp model-renderer material-type) gltf-object)]
    opengl-object))

(defn setup-camera-and-world-matrix
  {:malli/schema [:=> [:cat :int fmat4 fmat4] :nil]}
  [program transform camera-to-world]
  (use-program program)
  (uniform-matrix4 program "object_to_world" transform)
  (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform)))

(defmulti render-mesh (fn [material _render-vars] (material-type material)))
(m/=> render-mesh [:=> [:cat material mesh-vars] :nil])

(defmethod render-mesh ::program-colored-flat
  [{::keys [diffuse]} {::keys [program transform] :as render-vars}]
  (setup-camera-and-world-matrix program transform (:sfsim.render/camera-to-world render-vars))
  (uniform-vector3 program "diffuse_color" diffuse))

(defmethod render-mesh ::program-textured-flat
  [{::keys [colors]} {::keys [program texture-offset transform] :as render-vars}]
  (setup-camera-and-world-matrix program transform (:sfsim.render/camera-to-world render-vars))
  (use-textures {texture-offset colors}))

(defmethod render-mesh ::program-colored-bump
  [{::keys [diffuse normals]} {::keys [program texture-offset transform] :as render-vars}]
  (setup-camera-and-world-matrix program transform (:sfsim.render/camera-to-world render-vars))
  (uniform-vector3 program "diffuse_color" diffuse)
  (use-textures {texture-offset normals}))

(defmethod render-mesh ::program-textured-bump
  [{::keys [colors normals]} {::keys [program texture-offset transform] :as render-vars}]
  (setup-camera-and-world-matrix program transform (:sfsim.render/camera-to-world render-vars))
  (use-textures {texture-offset colors (inc texture-offset) normals}))

(defn render-scenes
  "Render a list of models"
  {:malli/schema [:=> [:cat model-renderer render-vars shadow-vars [:vector [:map [::root node]]]] :nil]}
  [model-renderer render-vars shadow-vars models]
  (let [render-config   (:sfsim.render/config model-renderer)
        cloud-data      (:sfsim.clouds/data model-renderer)
        atmosphere-luts (:sfsim.atmosphere/luts model-renderer)
        camera-to-world (:sfsim.render/camera-to-world render-vars)
        texture-offset  (::texture-offset model-renderer)
        world-to-camera (inverse camera-to-world)]
    (doseq [program (::programs model-renderer)]
           (use-program program)
           (uniform-float program "lod_offset" (lod-offset render-config cloud-data render-vars))
           (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
           (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
           (uniform-matrix4 program "world_to_camera" world-to-camera)
           (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
           (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
           (setup-shadow-matrices program shadow-vars))
    (use-textures {0 (:sfsim.atmosphere/transmittance atmosphere-luts) 1 (:sfsim.atmosphere/scatter atmosphere-luts)
                   2 (:sfsim.atmosphere/mie atmosphere-luts) 3 (:sfsim.atmosphere/surface-radiance atmosphere-luts)
                   4 (:sfsim.clouds/worley cloud-data) 5 (:sfsim.clouds/perlin-worley cloud-data)
                   6 (:sfsim.clouds/cloud-cover cloud-data) 7 (:sfsim.clouds/bluenoise cloud-data)})
    (use-textures (zipmap (drop 8 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                   (:sfsim.opacity/opacities shadow-vars))))
    (doseq [model models]
           (render-scene (comp model-renderer material-type) texture-offset render-vars model render-mesh))))

(defn destroy-model-renderer
  {:malli/schema [:=> [:cat model-renderer] :nil]}
  [{::keys [programs]}]
  (doseq [program programs] (destroy-program program)))

(defn make-scene-render-vars
  "Create hashmap with render variables for rendering a model outside the atmosphere"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/fov :double]] N N fvec3 quaternion fvec3 fvec3 :double] render-vars]}
  [render-config window-width window-height position orientation light-direction object-position object-radius]
  (let [min-z-near           (:sfsim.render/min-z-near render-config)
        rotation             (quaternion->matrix orientation)
        camera-to-world      (transformation-matrix rotation position)
        world-to-camera      (inverse camera-to-world)
        object-camera-vector (mulv world-to-camera (vec3->vec4 object-position 1.0))
        object-depth         (- (object-camera-vector 2))
        z-near               (max (- object-depth object-radius) min-z-near)
        z-far                (+ z-near object-radius object-radius)]
    (make-render-vars render-config window-width window-height position orientation light-direction z-near z-far)))

(defn vertex-shadow-model
  "Vertex shader for rendering model shadow maps"
  [textured bump]
  [shrink-shadow-index (template/eval (slurp "resources/shaders/model/vertex-shadow.glsl") {:textured textured :bump bump})])

(def fragment-shadow-model (slurp "resources/shaders/model/fragment-shadow.glsl"))

(defn make-model-shadow-program
  {:malli/schema [:=> [:cat :boolean :boolean] :int]}
  [textured bump]
  (make-program :sfsim.render/vertex [(vertex-shadow-model textured bump)]
                :sfsim.render/fragment [fragment-shadow-model]))

(def model-shadow-renderer (m/schema [:map [::program-colored-flat  :int]
                                           [::program-textured-flat :int]
                                           [::program-colored-bump  :int]
                                           [::program-textured-bump :int]
                                           [::programs [:vector :int]]
                                           [::size N]
                                           [::object-radius :double]]))

(defn make-model-shadow-renderer
  "Create renderer for rendering model-shadows"
  {:malli/schema [:=> [:cat N :double] model-shadow-renderer]}
  [size object-radius]
  (let [program-colored-flat  (make-model-shadow-program false false)
        program-textured-flat (make-model-shadow-program true  false)
        program-colored-bump  (make-model-shadow-program false true )
        program-textured-bump (make-model-shadow-program true  true )
        programs              [program-colored-flat program-textured-flat program-colored-bump program-textured-bump]]
    {::program-colored-flat  program-colored-flat
     ::program-textured-flat program-textured-flat
     ::program-colored-bump  program-colored-bump
     ::program-textured-bump program-textured-bump
     ::programs              programs
     ::size                  size
     ::object-radius         object-radius}))

(defn render-depth
  {:malli/schema [:=> [:cat material mesh-vars] :nil]}
  [_material {::keys [program transform] :as render-vars}]
  (use-program program)
  (uniform-matrix4 program "object_to_light" (mulm (:sfsim.matrix/shadow-ndc-matrix render-vars) transform)))

(defn render-shadow-map
  "Render shadow map for an object"
  {:malli/schema [:=> [:cat model-shadow-renderer :map [:map [::root node]]] texture-2d]}
  [renderer shadow-vars model]
  (let [size           (::size renderer)
        centered-model (assoc-in model [::root ::transform] (eye 4))]
    (doseq [program (::programs renderer)]
           (use-program program)
           (uniform-int program "shadow_size" size))
    (texture-render-depth size size
                          (clear)
                          (render-scene (comp renderer material-type) 0 shadow-vars centered-model render-depth))))

(def model-shadow (m/schema [:map [::matrices shadow-patch] [::shadows texture-2d]]))

(defn model-shadow-map
  "Determine shadow matrices and render shadow map for object"
  {:malli/schema [:=> [:cat model-shadow-renderer fvec3 [:map [::root node]]] model-shadow]}
  [renderer light-vector scene]
  (let [object-to-world (get-in scene [:sfsim.model/root :sfsim.model/transform])
        object-radius   (::object-radius renderer)
        shadow-matrices (shadow-patch-matrices object-to-world light-vector object-radius)
        shadow-map      (render-shadow-map renderer shadow-matrices scene)]
    {::matrices shadow-matrices
     ::shadows  shadow-map}))

(defn destroy-model-shadow-map
  "Delete model shadow map texture"
  {:malli/schema [:=> [:cat model-shadow] :nil]}
  [{::keys [shadows]}]
  (destroy-texture shadows))

(defn destroy-model-shadow-renderer
  [{::keys [programs]}]
  (doseq [program programs] (destroy-program program)))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
