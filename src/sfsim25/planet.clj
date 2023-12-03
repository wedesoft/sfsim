(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [fastmath.matrix :refer (mulm eye)]
              [sfsim25.matrix :refer (transformation-matrix)]
              [sfsim25.cubemap :refer (cube-map-corners)]
              [sfsim25.quadtree :refer (is-leaf?)]
              [sfsim25.render :refer (uniform-int uniform-vector3 uniform-matrix4 use-textures render-patches)]
              [sfsim25.atmosphere :refer (transmittance-outer attenuation-track cloud-overlay)]
              [sfsim25.clouds :refer (overall-shadow)]
              [sfsim25.shaders :as shaders])
    (:import [fastmath.matrix Mat4x4]))

(defn make-cube-map-tile-vertices
  "Create vertex array object for drawing cube map tiles"
  [face level y x height-tilesize color-tilesize]
  (let [[a b c d] (cube-map-corners face level y x)
        h0        (/ 0.5 height-tilesize)
        h1        (- 1.0 h0)
        c0        (/ 0.5 color-tilesize)
        c1        (- 1.0 c0)]
    [(a 0) (a 1) (a 2) h0 h0 c0 c0
     (b 0) (b 1) (b 2) h1 h0 c1 c0
     (c 0) (c 1) (c 2) h0 h1 c0 c1
     (d 0) (d 1) (d 2) h1 h1 c1 c1]))

(def vertex-planet
  "Pass through vertices, height field coordinates, and color texture coordinates"
  (slurp "resources/shaders/planet/vertex.glsl"))

(def tess-control-planet
  "Tessellation control shader to control outer tessellation of quad using a uniform integer"
  (slurp "resources/shaders/planet/tess-control.glsl"))

(def tess-evaluation-planet
  "Tessellation evaluation shader to generate output points of tessellated quad"
  (slurp "resources/shaders/planet/tess-evaluation.glsl"))

(def geometry-planet
  "Geometry shader outputting triangles with color texture coordinates and 3D points"
  (slurp "resources/shaders/planet/geometry.glsl"))

(def surface-radiance-function
  "Shader function to determine ambient light scattered by the atmosphere"
  [shaders/surface-radiance-forward shaders/interpolate-2d (slurp "resources/shaders/planet/surface-radiance.glsl")])

(def ground-radiance
  "Shader function to compute light emitted from ground"
  [shaders/is-above-horizon transmittance-outer surface-radiance-function shaders/remap
   (slurp "resources/shaders/planet/ground-radiance.glsl")])

(defn fragment-planet
  "Fragment shader to render planetary surface"
  [num-steps]
  [shaders/ray-sphere ground-radiance attenuation-track cloud-overlay (overall-shadow num-steps)
   (slurp "resources/shaders/planet/fragment.glsl")])

(defn render-tile
  "Render a planetary tile using the specified texture keys and neighbour tessellation"
  [^long program ^clojure.lang.IPersistentMap tile ^Mat4x4 transform ^clojure.lang.PersistentVector texture-keys]
  (let [neighbours  (bit-or (if (:sfsim25.quadtree/up    tile) 1 0)
                            (if (:sfsim25.quadtree/left  tile) 2 0)
                            (if (:sfsim25.quadtree/down  tile) 4 0)
                            (if (:sfsim25.quadtree/right tile) 8 0))
        tile-center (:center tile)]
    (uniform-int program "neighbours" neighbours)
    (uniform-vector3 program "tile_center" tile-center)
    (uniform-matrix4 program "recenter_and_transform" (mulm transform (transformation-matrix (eye 3) tile-center)))
    (apply use-textures (map tile texture-keys))
    (render-patches (:vao tile))))

(defn render-tree
  "Call each tile in tree to be rendered"
  [^long program ^clojure.lang.IPersistentMap node ^Mat4x4 transform ^clojure.lang.PersistentVector texture-keys]
  (when-not (empty? node)
            (if (is-leaf? node)
              (render-tile program node transform texture-keys)
              (doseq [selector [:0 :1 :2 :3 :4 :5]]
                     (render-tree program (selector node) transform texture-keys)))))

(def ground-shaders
  "Shaders for determining ground radiance"
  [ground-radiance shaders/is-above-horizon transmittance-outer shaders/remap surface-radiance-function
   shaders/transmittance-forward shaders/interpolate-2d shaders/surface-radiance-forward shaders/height-to-index
   shaders/elevation-to-index shaders/convert-2d-index shaders/sun-elevation-to-index shaders/horizon-distance
   shaders/limit-quot])
