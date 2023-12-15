(ns sfsim25.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (to-radians cos sin tan sqrt log exp)]
            [fastmath.matrix :refer (inverse)]
            [fastmath.vector :refer (vec3 add mult mag dot)]
            [sfsim25.render :refer (make-window destroy-window clear destroy-texture generate-mipmap make-float-cubemap
                                    make-float-texture-2d make-float-texture-3d make-vector-texture-2d onscreen-render
                                    texture-render-color-depth)]
            [sfsim25.atmosphere :refer (phase) :as atmosphere]
            [sfsim25.planet :as planet]
            [sfsim25.clouds :as clouds]
            [sfsim25.worley :refer (worley-size)]
            [sfsim25.matrix :refer (projection-matrix quaternion->matrix shadow-matrix-cascade split-mixed
                                    transformation-matrix)]
            [sfsim25.quaternion :as q]
            [sfsim25.util :refer (slurp-floats sqr)]
            [sfsim25.opacity :as opacity])
  (:import [org.lwjgl.opengl GL11]
           [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

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
(def cloud-bottom 2000)
(def cloud-top 5000)
(def cloud-multiplier 10.0)
(def cover-multiplier 26.0)
(def cap 0.007)
(def detail-scale 4000)
(def cloud-scale 100000)
(def series (take 4 (iterate #(* % 0.7) 1.0)))
(def sum-series (apply + series))
(def cloud-octaves (mapv #(/ % sum-series) series))
(def perlin-series (take 4 (iterate #(* % 0.7) 1.0)))
(def perlin-sum-series (apply + perlin-series))
(def perlin-octaves (mapv #(/ % perlin-sum-series) perlin-series))
(def mix 0.8)
(def opacity-step (atom 250.0))
(def step (atom 300.0))
(def shadow-size 512)
(def cover-size 512)
(def noise-size 64)
(def mount-everest 8000)
(def depth (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
              (sqrt (- (sqr (+ radius mount-everest)) (sqr radius)))))
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def surface-height-size 16)
(def surface-sun-elevation-size 63)
(def theta (to-radians 25))
(def r (+ radius cloud-bottom -750))
(def position (atom (vec3 (+ 3.0 radius) 0 0)))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def num-steps 3)
(def num-opacity-layers 7)
(def opacity-cutoff 0.01)
(def albedo 0.9)
(def specular 1000)
(def reflectivity 0.1)
(def dawn-start -0.2)
(def dawn-end 0.0)
(def water-color (vec3 0.09 0.11 0.34))
(def amplification 6)

(GLFW/glfwInit)

(def window (make-window "sfsim25" width height))
(GLFW/glfwShowWindow window)

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def worley-tex (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap worley-tex)
(def worley {:width worley-size :height worley-size :depth worley-size :texture worley-tex})

(def perlin-worley-data (float-array (map #(+ (* 0.3 %1) (* 0.7 %2))
                                          (slurp-floats "data/clouds/perlin.raw")
                                          (slurp-floats "data/clouds/worley-cover.raw"))))
(def perlin-worley-tex (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data perlin-worley-data}))
(def perlin-worley {:width worley-size :height worley-size :texture perlin-worley-tex})

(def noise-data (slurp-floats "data/bluenoise.raw"))
(def bluenoise-tex (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data noise-data}))
(def bluenoise {:width noise-size :height noise-size :texture bluenoise-tex})

(def cover-data (map (fn [i] {:width cover-size :height cover-size :data (slurp-floats (str "data/clouds/cover" i ".raw"))}) (range 6)))
(def cloud-cover-tex (make-float-cubemap :linear :clamp cover-data))
(def cloud-cover {:width cover-size :height cover-size :texture cloud-cover-tex})

(def transmittance-data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def transmittance-tex (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data transmittance-data}))
(def transmittance {:width transmittance-elevation-size :height transmittance-height-size :texture transmittance-tex})

(def scatter-data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def scatter-tex (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data scatter-data}))
(def scatter {:width elevation-size :height heading-size })
; TODO

(def mie-data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def mie-tex (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data mie-data}))
; TODO

(def surface-radiance-data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def surface-radiance-tex (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data surface-radiance-data}))
(def surface-radiance {:width surface-sun-elevation-size :height surface-height-size :texture surface-radiance-tex})

; Program to render cascade of deep opacity maps
(def opacity-renderer
  (opacity/make-opacity-renderer :num-opacity-layers num-opacity-layers
                                 :cloud-octaves cloud-octaves
                                 :perlin-octaves perlin-octaves
                                 :shadow-size shadow-size
                                 :radius radius
                                 :cloud-bottom cloud-bottom
                                 :cloud-top cloud-top
                                 :cloud-multiplier cloud-multiplier
                                 :cover-multiplier cover-multiplier
                                 :cap cap
                                 :detail-scale detail-scale
                                 :cloud-scale cloud-scale
                                 :worley worley
                                 :perlin-worley perlin-worley
                                 :cloud-cover cloud-cover))

; Program to render shadow map of planet
(def planet-shadow-renderer
  (planet/make-planet-shadow-renderer :tilesize tilesize
                                      :shadow-size shadow-size))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer
  (planet/make-cloud-planet-renderer :num-steps num-steps
                                     :perlin-octaves perlin-octaves
                                     :cloud-octaves cloud-octaves
                                     :radius radius
                                     :max-height max-height
                                     :cloud-bottom cloud-bottom
                                     :cloud-top cloud-top
                                     :cloud-scale cloud-scale
                                     :detail-scale detail-scale
                                     :depth depth
                                     :tilesize tilesize
                                     :height-size height-size
                                     :elevation-size elevation-size
                                     :light-elevation-size light-elevation-size
                                     :heading-size heading-size
                                     :surface-height-size surface-height-size
                                     :albedo albedo
                                     :reflectivity reflectivity
                                     :specular specular
                                     :cloud-multiplier cloud-multiplier
                                     :cover-multiplier cover-multiplier
                                     :cap cap
                                     :anisotropic anisotropic
                                     :radius radius
                                     :max-height max-height
                                     :water-color water-color
                                     :amplification amplification
                                     :opacity-cutoff opacity-cutoff
                                     :num-opacity-layers num-opacity-layers
                                     :shadow-size shadow-size
                                     :transmittance transmittance
                                     :scatter-tex scatter-tex
                                     :mie-tex mie-tex
                                     :worley worley
                                     :perlin-worley perlin-worley
                                     :bluenoise bluenoise
                                     :cloud-cover cloud-cover))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer
  (clouds/make-cloud-atmosphere-renderer :num-steps num-steps
                                         :perlin-octaves perlin-octaves
                                         :cloud-octaves cloud-octaves
                                         :num-steps num-steps
                                         :perlin-octaves perlin-octaves
                                         :cloud-octaves cloud-octaves
                                         :radius radius
                                         :max-height max-height
                                         :cloud-bottom cloud-bottom
                                         :cloud-top cloud-top
                                         :cloud-scale cloud-scale
                                         :detail-scale detail-scale
                                         :depth depth
                                         :tilesize tilesize
                                         :height-size height-size
                                         :elevation-size elevation-size
                                         :light-elevation-size light-elevation-size
                                         :heading-size heading-size
                                         :surface-height-size surface-height-size
                                         :albedo albedo
                                         :reflectivity reflectivity
                                         :specular specular
                                         :cloud-multiplier cloud-multiplier
                                         :cover-multiplier cover-multiplier
                                         :cap cap
                                         :anisotropic anisotropic
                                         :radius radius
                                         :max-height max-height
                                         :water-color water-color
                                         :amplification amplification
                                         :opacity-cutoff opacity-cutoff
                                         :num-opacity-layers num-opacity-layers
                                         :shadow-size shadow-size
                                         :transmittance transmittance
                                         :scatter-tex scatter-tex
                                         :mie-tex mie-tex
                                         :worley worley
                                         :perlin-worley perlin-worley
                                         :bluenoise bluenoise
                                         :cloud-cover cloud-cover))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer
  (planet/make-planet-renderer :width width
                               :height height
                               :num-steps num-steps
                               :tilesize tilesize
                               :color-tilesize color-tilesize
                               :height-size height-size
                               :elevation-size elevation-size
                               :light-elevation-size light-elevation-size
                               :heading-size heading-size
                               :surface-height-size surface-height-size
                               :albedo albedo
                               :dawn-start dawn-start
                               :dawn-end dawn-end
                               :reflectivity reflectivity
                               :specular specular
                               :radius radius
                               :max-height max-height
                               :water-color water-color
                               :amplification amplification
                               :opacity-cutoff opacity-cutoff
                               :num-opacity-layers num-opacity-layers
                               :shadow-size shadow-size
                               :radius radius
                               :max-height max-height
                               :transmittance transmittance
                               :scatter-tex scatter-tex
                               :mie-tex mie-tex
                               :surface-radiance surface-radiance))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer
  (atmosphere/make-atmosphere-renderer :num-steps num-steps
                                       :height-size height-size
                                       :elevation-size elevation-size
                                       :light-elevation-size light-elevation-size
                                       :heading-size heading-size
                                       :surface-height-size surface-height-size
                                       :albedo albedo
                                       :reflectivity 0.1
                                       :opacity-cutoff opacity-cutoff
                                       :num-opacity-layers num-opacity-layers
                                       :shadow-size shadow-size
                                       :radius radius
                                       :max-height max-height
                                       :specular specular
                                       :amplification amplification
                                       :transmittance transmittance
                                       :scatter-tex scatter-tex
                                       :mie-tex mie-tex
                                       :surface-radiance surface-radiance))

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
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 50 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -50 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))
                 ts (if (@keystates GLFW/GLFW_KEY_Y) 0.05 (if (@keystates GLFW/GLFW_KEY_H) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! threshold + (* dt tr))
             (swap! opacity-step + (* dt to))
             (swap! step + (* dt ts))
             (GL11/glFinish)
             (let [norm-pos   (mag @position)
                   dist       (- norm-pos radius cloud-top)
                   z-near     (max 1.0 (* 0.4 dist))
                   z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                 (sqrt (- (sqr norm-pos) (sqr radius))))
                   light-dir  (vec3 (cos @light) (sin @light) 0)
                   projection (projection-matrix (aget w 0) (aget h 0) z-near (+ z-far 1) fov)
                   lod-offset (/ (log (/ (tan (/ fov 2)) (/ (aget w 0) 2) (/ detail-scale (:width worley)))) (log 2))
                   extrinsics (transformation-matrix (quaternion->matrix @orientation) @position)
                   matrix-cas (shadow-matrix-cascade projection extrinsics light-dir depth mix z-near z-far num-steps)
                   splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
                   scatter-am (+ (* anisotropic (phase 0.76 -1)) (- 1 anisotropic))
                   cos-light  (/ (dot light-dir @position) (mag @position))
                   sin-light  (sqrt (- 1 (sqr cos-light)))
                   opac-step  (* (+ cos-light (* 10 sin-light)) @opacity-step)
                   opacities  (opacity/render-opacity-cascade opacity-renderer matrix-cas light-dir @threshold scatter-am
                                                              opac-step)
                   shadows    (planet/render-shadow-cascade planet-shadow-renderer
                                                            :matrix-cascade matrix-cas
                                                            :tree (planet/get-current-tree tile-tree))
                   w2         (quot (aget w 0) 2)
                   h2         (quot (aget h 0) 2)
                   clouds     (texture-render-color-depth
                                w2 h2 true
                                (clear (vec3 0 0 0) 0)
                                ; Render clouds in front of planet
                                (planet/render-cloud-planet cloud-planet-renderer
                                                            :cloud-step @step
                                                            :cloud-threshold @threshold
                                                            :lod-offset lod-offset
                                                            :projection projection
                                                            :origin @position
                                                            :transform (inverse extrinsics)
                                                            :light-direction light-dir
                                                            :opacity-step opac-step
                                                            :splits splits
                                                            :matrix-cascade matrix-cas
                                                            :shadows shadows
                                                            :opacities opacities
                                                            :tree (planet/get-current-tree tile-tree))
                                ; Render clouds above the horizon
                                (clouds/render-cloud-atmosphere cloud-atmosphere-renderer
                                                                :cloud-step @step
                                                                :cloud-threshold @threshold
                                                                :lod-offset lod-offset
                                                                :projection projection
                                                                :origin @position
                                                                :transform (inverse extrinsics)
                                                                :light-direction light-dir
                                                                :z-far z-far
                                                                :opacity-step opac-step
                                                                :splits splits
                                                                :matrix-cascade matrix-cas
                                                                :shadows shadows
                                                                :opacities opacities))]
               (onscreen-render window
                                (clear (vec3 0 1 0) 0)
                                ; Render planet with cloud overlay
                                (planet/render-planet planet-renderer
                                                      :projection projection
                                                      :origin @position
                                                      :transform (inverse extrinsics)
                                                      :light-direction light-dir
                                                      :opacity-step opac-step
                                                      :window-width (aget w 0)
                                                      :window-height (aget h 0)
                                                      :shadow-bias shadow-bias
                                                      :splits splits
                                                      :matrix-cascade matrix-cas
                                                      :clouds clouds
                                                      :shadows shadows
                                                      :opacities opacities
                                                      :tree (planet/get-current-tree tile-tree))
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer
                                                              :splits splits
                                                              :matrix-cascade matrix-cas
                                                              :projection projection
                                                              :extrinsics extrinsics
                                                              :origin @position
                                                              :opacity-step opac-step
                                                              :window-width (aget w 0)
                                                              :window-height (aget h 0)
                                                              :light-direction light-dir
                                                              :z-far z-far
                                                              :clouds clouds
                                                              :opacities opacities))
               (destroy-texture clouds)
               (opacity/destroy-opacity-cascade opacities)
               (planet/destroy-shadow-cascade shadows))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, step (y/h) %.0f, dt %.3f"
                              @threshold @opacity-step @step (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (destroy-texture (:texture surface-radiance))
  (destroy-texture mie-tex)
  (destroy-texture scatter-tex)
  (destroy-texture (:texture transmittance))
  (destroy-texture cloud-cover-tex)
  (destroy-texture (:texture bluenoise))
  (destroy-texture (:texture perlin-worley))
  (destroy-texture (:texture worley))
  (clouds/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
