(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [clojure.core.matrix :refer :all]
              [sfsim25.cubemap :refer :all]))

(defn make-cube-map-tile-vertices
  "Create vertex array object for drawing cube map tiles"
  [face level y x height-tile-size color-tile-size]
  (let [[a b c d] (cube-map-corners face level y x)
        h0        (/ 0.5 height-tile-size)
        h1        (- 1.0 h0)
        c0        (/ 0.5 color-tile-size)
        c1        (- 1.0 c0)]
    [(mget a 0) (mget a 1) (mget a 2) h0 h0 c0 c0
     (mget b 0) (mget b 1) (mget b 2) h1 h0 c1 c0
     (mget c 0) (mget c 1) (mget c 2) h0 h1 c0 c1
     (mget d 0) (mget d 1) (mget d 2) h1 h1 c1 c1]))

(def vertex-planet
  "Pass through vertices, height field coordinates, and color texture coordinates"
  (slurp "resources/shaders/vertex-planet.glsl"))

(def tess-control-planet
  "Tessellation control shader to control outer tessellation of quad using a uniform integer"
  (slurp "resources/shaders/tess-control-planet.glsl"))

(def tess-evaluation-planet
  "Tessellation evaluation shader to generate output points of tessellated quad"
  (slurp "resources/shaders/tess-evaluation-planet.glsl"))

(def geometry-planet
  "Geometry shader outputting triangles with color texture coordinates and 3D points"
  (slurp "resources/shaders/geometry-planet.glsl"))

(def ground-radiance
  "Shader function to compute light emitted from ground"
  (slurp "resources/shaders/ground_radiance.glsl"))
