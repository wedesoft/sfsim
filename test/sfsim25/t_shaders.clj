(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [comb.template :as template]
            [clojure.core.matrix :refer :all]
            [sfsim25.shaders :refer :all]
            [sfsim25.render :refer :all]
            [sfsim25.util :refer :all])
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Pbuffer PixelFormat]))

(def vertex-passthrough "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-probe
  (template/fn [cx cy cz ox oy oz dx dy dz] "#version 410 core
out lowp vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
void main()
{
  vec2 result = ray_sphere(vec3(<%= cx %>, <%= cy %>, <%= cz %>),
                           1,
                           vec3(<%= ox %>, <%= oy %>, <%= oz %>),
                           vec3(<%= dx %>, <%= dy %>, <%= dz %>));
  fragColor = vec3(result.x, result.y, 0);
}"))

(defn shader-test [& args]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :vertex [vertex-passthrough] :fragment [ray-sphere (apply fragment-probe args)])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            tex      (texture-render 1 1 (use-program program) (render-quads vao))
            img      (texture->vectors tex 1 1)]
        (deliver result (get-vector img 0 0))
        (destroy-texture tex)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(facts "Shader for intersection of ray with sphere"
       (shader-test 0 0 0 2 2 -1 0 0 1) => (matrix [0.0 0.0 0.0])
       (shader-test 0 0 0 0 0 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (shader-test 2 2 0 2 2 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (shader-test 0 0 0 0 0 -2 0 0 2) => (matrix [0.5 1.0 0.0])
       (shader-test 0 0 0 0 0  0 0 0 1) => (matrix [0.0 1.0 0.0])
       (shader-test 0 0 0 0 0  2 0 0 1) => (matrix [0.0 0.0 0.0]))
