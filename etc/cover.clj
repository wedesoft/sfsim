;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(require '[clojure.math :refer (sin cos PI sqrt exp log pow)]
         '[fastmath.vector :refer (vec3)]
         '[fastmath.matrix :refer (mulm)]
         '[sfsim.render :refer :all]
         '[sfsim.texture :refer :all]
         '[sfsim.matrix :refer :all]
         '[sfsim.worley :refer :all]
         '[sfsim.shaders :as shaders]
         '[sfsim.clouds :refer :all]
         '[sfsim.util :refer :all])

(import '[org.lwjgl.opengl GL]
        '[org.lwjgl.glfw GLFW GLFWKeyCallback])

(set! *unchecked-math* :warn-on-boxed)

(def width 640)
(def height 640)

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow width height "scratch" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def data (slurp-floats "data/clouds/worley-north.raw"))
(def worley-north (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "data/clouds/worley-south.raw"))
(def worley-south (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def worley-cover (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data data}))

(def cloud-cover-tex (atom nil))

(def vertex-shader
"#version 450 core
in vec3 point;
out VS_OUT
{
  vec3 point;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-shader
"#version 450 core
uniform samplerCube cubemap;
uniform mat3 rotation;
uniform float threshold;
uniform float multiplier;
in VS_OUT
{
  vec3 point;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec3 fragColor;
void main()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = rotation * vec3(fs_in.point.xy, -1 + intersection.x);
    float value = texture(cubemap, p).r;
    value = (value - threshold) * multiplier;
    fragColor = vec3(value, value, 1.0);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def program (make-program :sfsim.render/vertex [vertex-shader] :sfsim.render/fragment [fragment-shader shaders/ray-sphere]))
(def indices [0 1 3 2])
(def vertices [-1 -1 0, 1 -1 0, -1 1 0, 1  1 0])
(def vao (make-vertex-array-object program indices vertices ["point" 3]))

(def alpha (atom 0))
(def beta (atom 0))
(def threshold (atom 0.4))
(def multiplier (atom 4.0))
(def curl-scale-exp (atom (log 4)))
(def cloud-scale-exp (atom (log 2)))
(def prevailing (atom 0.1))
(def whirl (atom 1.0))

(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (if (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (if (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(def t0 (atom (System/currentTimeMillis)))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
             tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
             ta (if (@keystates GLFW/GLFW_KEY_W) 0.001 (if (@keystates GLFW/GLFW_KEY_S) -0.001 0))
             cs (if (@keystates GLFW/GLFW_KEY_E) 0.001 (if (@keystates GLFW/GLFW_KEY_D) -0.001 0))
             os (if (@keystates GLFW/GLFW_KEY_R) 0.001 (if (@keystates GLFW/GLFW_KEY_F) -0.001 0))
             pw (if (@keystates GLFW/GLFW_KEY_T) 0.001 (if (@keystates GLFW/GLFW_KEY_G) -0.001 0))
             ps (if (@keystates GLFW/GLFW_KEY_Y) 0.001 (if (@keystates GLFW/GLFW_KEY_H) -0.001 0))]
         (swap! alpha + (* dt ra))
         (swap! beta + (* dt rb))
         (swap! threshold + (* dt tr))
         (swap! multiplier + (* dt ta))
         (swap! whirl + (* dt pw))
         (swap! prevailing + (* dt ps))
         (swap! curl-scale-exp + (* dt cs))
         (swap! cloud-scale-exp + (* dt os))
         (when (or (nil? @cloud-cover-tex) (not (zero? pw)) (not (zero? ps)) (not (zero? cs)) (not (zero? os)))
           (when-not (nil? @cloud-cover-tex)
                     (destroy-texture @cloud-cover-tex))
           (reset! cloud-cover-tex
             (cloud-cover-cubemap :sfsim.clouds/size 512
                                  :sfsim.clouds/worley-size worley-size
                                  :sfsim.clouds/worley-south worley-south
                                  :sfsim.clouds/worley-north worley-north
                                  :sfsim.clouds/worley-cover worley-cover
                                  :sfsim.clouds/flow-octaves [0.5 0.25 0.125]
                                  :sfsim.clouds/cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                                  :sfsim.clouds/whirl @whirl
                                  :sfsim.clouds/prevailing @prevailing
                                  :sfsim.clouds/curl-scale (exp @curl-scale-exp)
                                  :sfsim.clouds/cover-scale (exp @cloud-scale-exp)
                                  :sfsim.clouds/num-iterations 50
                                  :sfsim.clouds/flow-scale (* 1.5e-3 (exp @curl-scale-exp)))))
         (let [mat (mulm (rotation-y @beta) (rotation-x @alpha))]
           (onscreen-render window
                            (clear (vec3 0 0 0))
                            (use-program program)
                            (uniform-sampler program "cubemap" 0)
                            (uniform-matrix3 program "rotation" mat)
                            (uniform-float program "threshold" @threshold)
                            (uniform-float program "multiplier" @multiplier)
                            (use-textures {0 @cloud-cover-tex})
                            (render-quads vao)))
         (GLFW/glfwPollEvents)
         (print (format "\rthreshold = %.3f, multiplier = %.3f, curlscale = %.3f, cloudscale = %.3f, whirl = %.3f, prevailing = %.3f"
                        @threshold @multiplier (exp @curl-scale-exp) (exp @cloud-scale-exp) @whirl @prevailing))
         (flush)
         (swap! t0 + dt)))

(destroy-program program)
(destroy-texture @cloud-cover-tex)
(destroy-texture worley-cover)
(destroy-texture worley-south)
(destroy-texture worley-north)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)

(set! *unchecked-math* false)

(System/exit 0)
