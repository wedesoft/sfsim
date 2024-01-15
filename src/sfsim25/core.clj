(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin tan sqrt log exp)]
            [fastmath.matrix :refer (inverse)]
            [fastmath.vector :refer (vec3 add mult mag dot)]
            [sfsim25.render :refer (make-window destroy-window clear destroy-texture make-vector-texture-2d make-vector-texture-4d
                                    onscreen-render texture-render-color-depth)]
            [sfsim25.atmosphere :refer (phase) :as atmosphere]
            [sfsim25.planet :as planet]
            [sfsim25.clouds :as clouds]
            [sfsim25.worley :refer (worley-size)]
            [sfsim25.matrix :refer (projection-matrix quaternion->matrix shadow-matrix-cascade split-list transformation-matrix)]
            [sfsim25.quaternion :as q]
            [sfsim25.util :refer (slurp-floats sqr)]
            [sfsim25.opacity :as opacity])
  (:import [org.lwjgl.opengl GL11]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(def width 1280)
(def height 720)

(def fov (to-radians 60.0))
(def radius 6378000.0)
(def tilesize 33)
(def color-tilesize 129)
(def max-height 35000.0)
(def threshold (atom 18.2))
(def anisotropic 0.25)
(def shadow-bias (exp -6.0))
(def cloud-bottom 2000.0)
(def cloud-top 5000.0)
(def cloud-multiplier 10.0)
(def cover-multiplier 26.0)
(def cap 0.007)
(def detail-scale 4000.0)
(def cloud-scale 100000.0)
(def series (take 4 (iterate #(* % 0.7) 1.0)))
(def sum-series (apply + series))
(def cloud-octaves (mapv #(/ % sum-series) series))
(def perlin-series (take 4 (iterate #(* % 0.7) 1.0)))
(def perlin-sum-series (apply + perlin-series))
(def perlin-octaves (mapv #(/ % perlin-sum-series) perlin-series))
(def mix 0.8)
(def opacity-base (atom 250.0))
(def cloud-step 400.0)
(def shadow-size 512)
(def mount-everest 8000)
(def depth (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
              (sqrt (- (sqr (+ radius mount-everest)) (sqr radius)))))
(def theta (to-radians 25))
(def r (+ radius cloud-bottom -750))
(def position (atom (vec3 (+ 3.0 radius) 0 0)))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def num-steps 3)
(def num-opacity-layers 7)
(def opacity-cutoff 0.01)
(def albedo 0.9)
(def specular 1000.0)
(def reflectivity 0.1)
(def dawn-start -0.2)
(def dawn-end 0.0)
(def water-color (vec3 0.09 0.11 0.34))
(def amplification 6.0)

(GLFW/glfwInit)

(def window (make-window "sfsim25" width height))
(GLFW/glfwShowWindow window)

(def shadow-data (opacity/make-shadow-data :sfsim25.opacity/num-opacity-layers num-opacity-layers
                                           :sfsim25.opacity/shadow-size shadow-size
                                           :sfsim25.opacity/num-steps num-steps
                                           :sfsim25.opacity/depth depth
                                           :sfsim25.opacity/shadow-bias shadow-bias))

(def cloud-data (clouds/make-cloud-data :cloud-octaves cloud-octaves
                                        :perlin-octaves perlin-octaves
                                        :cloud-bottom cloud-bottom
                                        :cloud-top cloud-top
                                        :detail-scale detail-scale
                                        :cloud-scale cloud-scale
                                        :cloud-multiplier cloud-multiplier
                                        :cover-multiplier cover-multiplier
                                        :cap cap
                                        :anisotropic anisotropic
                                        :opacity-cutoff opacity-cutoff))

(def planet-data (planet/make-planet-data :radius radius
                                          :max-height max-height
                                          :albedo albedo
                                          :reflectivity reflectivity
                                          :water-color water-color))

(def atmosphere-luts (atmosphere/load-atmosphere-luts))

; Program to render cascade of deep opacity maps
(def opacity-renderer
  (opacity/make-opacity-renderer :planet-data planet-data
                                 :shadow-data shadow-data
                                 :cloud-data cloud-data))

; Program to render shadow map of planet
(def planet-shadow-renderer
  (planet/make-planet-shadow-renderer :tilesize tilesize
                                      :shadow-data shadow-data))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer
  (planet/make-cloud-planet-renderer :tilesize tilesize
                                     :amplification amplification
                                     :atmosphere-luts atmosphere-luts
                                     :planet-data planet-data
                                     :shadow-data shadow-data
                                     :cloud-data cloud-data))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer
  (clouds/make-cloud-atmosphere-renderer :tilesize tilesize
                                         :amplification amplification
                                         :atmosphere-luts atmosphere-luts
                                         :planet-data planet-data
                                         :shadow-data shadow-data
                                         :cloud-data cloud-data))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer
  (planet/make-planet-renderer :width width
                               :height height
                               :tilesize tilesize
                               :color-tilesize color-tilesize
                               :dawn-start dawn-start
                               :dawn-end dawn-end
                               :specular specular
                               :amplification amplification
                               :atmosphere-luts atmosphere-luts
                               :planet-data planet-data
                               :shadow-data shadow-data))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer
  (atmosphere/make-atmosphere-renderer :specular specular
                                       :amplification amplification
                                       :atmosphere-luts atmosphere-luts
                                       :planet-data planet-data))

(def tile-tree (planet/make-tile-tree))

(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (when (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (when (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [t0 (atom (System/currentTimeMillis))
        n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (not (GLFW/glfwWindowShouldClose window))
           (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
           (planet/update-tile-tree planet-renderer tile-tree @position)
           (let [t1 (System/currentTimeMillis)
                 dt (- t1 @t0)
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0.0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0.0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0.0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 50 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -50 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! threshold + (* dt tr))
             (swap! opacity-base + (* dt to))
             (GL11/glFinish)
             (let [norm-pos     (mag @position)
                   dist         (- norm-pos radius cloud-top)
                   z-near       (max 1.0 (* 0.4 dist))
                   z-far        (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                   (sqrt (- (sqr norm-pos) (sqr radius))))
                   light-dir    (vec3 (cos @light) (sin @light) 0)
                   projection   (projection-matrix (aget w 0) (aget h 0) z-near (+ z-far 1) fov)
                   lod-offset   (/ (log (/ (tan (/ fov 2)) (/ (aget w 0) 2) (/ detail-scale worley-size))) (log 2))
                   extrinsics   (transformation-matrix (quaternion->matrix @orientation) @position)
                   matrix-cas   (shadow-matrix-cascade projection extrinsics light-dir depth mix z-near z-far num-steps)
                   splits       (split-list mix z-near z-far num-steps)
                   scatter-am   (+ (* anisotropic (phase {:sfsim25.atmosphere/scatter-g 0.76} -1.0)) (- 1 anisotropic))
                   cos-light    (/ (dot light-dir @position) (mag @position))
                   sin-light    (sqrt (- 1 (sqr cos-light)))
                   opacity-step (* (+ cos-light (* 10 sin-light)) @opacity-base)
                   opacities    (opacity/render-opacity-cascade opacity-renderer matrix-cas light-dir @threshold scatter-am
                                                                opacity-step)
                   shadows      (planet/render-shadow-cascade planet-shadow-renderer
                                                              :matrix-cascade matrix-cas
                                                              :tree (planet/get-current-tree tile-tree))
                   w2         (quot (aget w 0) 2)
                   h2         (quot (aget h 0) 2)
                   clouds     (texture-render-color-depth
                                w2 h2 true
                                (clear (vec3 0 0 0) 0.0)
                                ; Render clouds in front of planet
                                (planet/render-cloud-planet cloud-planet-renderer
                                                            :cloud-step cloud-step
                                                            :cloud-threshold @threshold
                                                            :lod-offset lod-offset
                                                            :projection projection
                                                            :origin @position
                                                            :transform (inverse extrinsics)
                                                            :light-direction light-dir
                                                            :opacity-step opacity-step
                                                            :splits splits
                                                            :matrix-cascade matrix-cas
                                                            :shadows shadows
                                                            :opacities opacities
                                                            :tree (planet/get-current-tree tile-tree))
                                ; Render clouds above the horizon
                                (clouds/render-cloud-atmosphere cloud-atmosphere-renderer
                                                                :cloud-step cloud-step
                                                                :cloud-threshold @threshold
                                                                :lod-offset lod-offset
                                                                :projection projection
                                                                :origin @position
                                                                :transform (inverse extrinsics)
                                                                :light-direction light-dir
                                                                :z-far z-far
                                                                :opacity-step opacity-step
                                                                :splits splits
                                                                :matrix-cascade matrix-cas
                                                                :shadows shadows
                                                                :opacities opacities))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0.0)
                                ; Render planet with cloud overlay
                                (planet/render-planet planet-renderer
                                                      :projection projection
                                                      :origin @position
                                                      :transform (inverse extrinsics)
                                                      :light-direction light-dir
                                                      :opacity-step opacity-step
                                                      :window-width (aget w 0)
                                                      :window-height (aget h 0)
                                                      :splits splits
                                                      :matrix-cascade matrix-cas
                                                      :clouds clouds
                                                      :shadows shadows
                                                      :opacities opacities
                                                      :tree (planet/get-current-tree tile-tree))
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer
                                                              :projection projection
                                                              :extrinsics extrinsics
                                                              :origin @position
                                                              :window-width (aget w 0)
                                                              :window-height (aget h 0)
                                                              :light-direction light-dir
                                                              :z-far z-far
                                                              :clouds clouds))
               (destroy-texture clouds)
               (opacity/destroy-opacity-cascade opacities)
               (planet/destroy-shadow-cascade shadows))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, dt %.3f"
                              @threshold @opacity-base (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (clouds/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
