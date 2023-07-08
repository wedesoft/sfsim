(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [clojure.java.io :as io]
              [sfsim25.cubemap :refer (cube-map-corners)]))

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
  (slurp (io/resource "shaders/planet/vertex.glsl")))

(def tess-control-planet
  "Tessellation control shader to control outer tessellation of quad using a uniform integer"
  (slurp (io/resource "shaders/planet/tess-control.glsl")))

(def tess-evaluation-planet
  "Tessellation evaluation shader to generate output points of tessellated quad"
  (slurp (io/resource "shaders/planet/tess-evaluation.glsl")))

(def geometry-planet
  "Geometry shader outputting triangles with color texture coordinates and 3D points"
  (slurp (io/resource "shaders/planet/geometry.glsl")))

(def surface-radiance-function
  "Shader function to determine ambient light scattered by the atmosphere"
  (slurp (io/resource "shaders/planet/surface-radiance.glsl")))

(def ground-radiance
  "Shader function to compute light emitted from ground"
  (slurp (io/resource "shaders/planet/ground-radiance.glsl")))

(def fragment-planet
  "Fragment shader to render planetary surface"
  (slurp (io/resource "shaders/planet/fragment.glsl")))
