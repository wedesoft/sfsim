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

(def ray-sphere-probe
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

(defn shader-test [shader probe]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program  (make-program :vertex [vertex-passthrough] :fragment [shader (apply probe args)])
                vao      (make-vertex-array-object program indices vertices [:point 3])
                tex      (texture-render 1 1 true (use-program program) (render-quads vao))
                img      (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ray-sphere-test (shader-test ray-sphere ray-sphere-probe))

(facts "Shader for intersection of ray with sphere"
       (ray-sphere-test 0 0 0 2 2 -1 0 0 1) => (matrix [0.0 0.0 0.0])
       (ray-sphere-test 0 0 0 0 0 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (ray-sphere-test 2 2 0 2 2 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (ray-sphere-test 0 0 0 0 0 -2 0 0 2) => (matrix [0.5 1.0 0.0])
       (ray-sphere-test 0 0 0 0 0  0 0 0 1) => (matrix [0.0 1.0 0.0])
       (ray-sphere-test 0 0 0 0 0  2 0 0 1) => (matrix [0.0 0.0 0.0]))

(def elevation-to-index-probe
  (template/fn [size elevation horizon-angle power] "#version 410 core
out lowp vec3 fragColor;
float elevation_to_index(int size, float elevation, float horizon_angle, float power);
void main()
{
  float result = elevation_to_index(<%= size %>, <%= elevation %>, <%= horizon-angle %>, <%= power %>);
  fragColor = vec3(result, 0, 0);
}"))

(def elevation-to-index-test (shader-test elevation-to-index elevation-to-index-probe))

(facts "Shader for converting elevation to index"
       (mget (elevation-to-index-test 17 0.0 0.0 1.0) 0)               => (roughly (/ 0.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.14159) 0.0 1.0) 0)   => (roughly (/ 8.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.14160) 0.0 1.0) 0)   => (roughly (/ 9.5 17))
       (mget (elevation-to-index-test 17 3.14159 0.0 1.0) 0)           => (roughly (/ 16.5 17))
       (mget (elevation-to-index-test 17 (* 0.375 3.14159) 0.0 1.0) 0) => (roughly (/ 6.5 17))
       (mget (elevation-to-index-test 17 (* 0.375 3.14159) 0.0 2.0) 0) => (roughly (/ 4.5 17))
       (mget (elevation-to-index-test 17 (* 0.625 3.14159) 0.0 1.0) 0) => (roughly (/ 11.25 17))
       (mget (elevation-to-index-test 17 (* 0.625 3.14159) 0.0 2.0) 0) => (roughly (/ 13.0 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.34159) 0.1 1.0) 0)   => (roughly (/ 8.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.34160) 0.1 1.0) 0)   => (roughly (/ 9.5 17)))

(def horizon-angle-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
float horizon_angle(vec3 point, float radius);
void main()
{
  float result = horizon_angle(vec3(<%= x %>, <%= y %>, <%= z %>), 6378000);
  fragColor = vec3(result, 0, 0);
}"))

(def horizon-angle-test (shader-test horizon-angle horizon-angle-probe))

(facts "Angle of sphere's horizon angle below horizontal plane depending on height"
       (mget (horizon-angle-test 6378000 0 0) 0)       => (roughly 0.0)
       (mget (horizon-angle-test (* 2 6378000) 0 0) 0) => (roughly (/ Math/PI 3))
       (mget (horizon-angle-test 6377999 0 0) 0)       => (roughly 0.0))
