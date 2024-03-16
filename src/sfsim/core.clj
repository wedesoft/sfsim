(ns sfsim.core
  "Space flight simulator main program."
  (:require [clojure.math :refer (cos sin to-radians exp)]
            [fastmath.matrix :refer (eye inverse)]
            [fastmath.vector :refer (vec3 add mult)]
            [sfsim.texture :refer (destroy-texture)]
            [sfsim.render :refer (make-window destroy-window clear onscreen-render texture-render-color-depth make-render-vars
                                  use-program uniform-matrix4 uniform-vector3 uniform-float uniform-int use-textures
                                  setup-shadow-and-opacity-maps)]
            [sfsim.atmosphere :as atmosphere]
            [sfsim.matrix :refer (transformation-matrix)]
            [sfsim.planet :as planet]
            [sfsim.clouds :as clouds]
            [sfsim.model :as model]
            [sfsim.quaternion :as q]
            [sfsim.opacity :as opacity]
            [sfsim.config :as config])
  (:import [org.lwjgl.glfw GLFW GLFWKeyCallback])
  (:gen-class))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; (require '[nrepl.server :refer [start-server stop-server]])
; (defonce server (start-server :port 7888))

; (require '[malli.dev :as dev])
; (require '[malli.dev.pretty :as pretty])
; (dev/start! {:report (pretty/thrower)})

(def opacity-base (atom 250.0))
(def position (atom (vec3 (+ 3.0 6378000.0) 0 0)))
(def orientation (atom (q/rotation (to-radians 270) (vec3 0 0 1))))
(def light (atom 0.0))
(def speed (atom 50.0))

(GLFW/glfwInit)

(def window (make-window "sfsim" 1280 720))
(GLFW/glfwShowWindow window)

(def cloud-data (clouds/make-cloud-data config/cloud-config))
(def atmosphere-luts (atmosphere/make-atmosphere-luts config/max-height))
(def shadow-data (opacity/make-shadow-data config/shadow-config config/planet-config cloud-data))
(def data {:sfsim.render/config config/render-config
           :sfsim.planet/config config/planet-config
           :sfsim.opacity/data shadow-data
           :sfsim.clouds/data cloud-data
           :sfsim.atmosphere/luts atmosphere-luts})

; Program to render cascade of deep opacity maps
(def opacity-renderer (opacity/make-opacity-renderer data))

; Program to render shadow map of planet
(def planet-shadow-renderer (planet/make-planet-shadow-renderer data))

; Program to render clouds in front of planet (before rendering clouds above horizon)
(def cloud-planet-renderer (planet/make-cloud-planet-renderer data))

; Program to render clouds above the horizon (after rendering clouds in front of planet)
(def cloud-atmosphere-renderer (planet/make-cloud-atmosphere-renderer data))

; Program to render planet with cloud overlay (before rendering atmosphere)
(def planet-renderer (planet/make-planet-renderer data))

; Program to render atmosphere with cloud overlay (last rendering step)
(def atmosphere-renderer (atmosphere/make-atmosphere-renderer data))

(def cube (assoc-in (model/read-gltf "test/sfsim/fixtures/model/cube.gltf") [:sfsim.model/materials 0 :sfsim.model/diffuse] (vec3 1 1 1)))

(def model-renderer (model/make-model-renderer (:sfsim.opacity/num-steps config/shadow-config)
                                               (:sfsim.clouds/perlin-octaves config/cloud-config)
                                               (:sfsim.clouds/cloud-octaves config/cloud-config)))

(def cube-model (model/load-scene-into-opengl (comp model-renderer model/material-type) cube))

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

(def dist (atom 30.0))

(defn -main
  "Space flight simulator main function"
  [& _args]
  (let [t0 (atom (System/currentTimeMillis))
        n  (atom 0)
        w  (int-array 1)
        h  (int-array 1)]
    (while (not (GLFW/glfwWindowShouldClose window))
           (GLFW/glfwGetWindowSize ^long window ^ints w ^ints h)
           (planet/update-tile-tree planet-renderer tile-tree (aget w 0) @position)
           (let [t1 (System/currentTimeMillis)
                 dt (- t1 @t0)
                 ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0.0))
                 rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0.0))
                 rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0.0))
                 v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) @speed (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) (- @speed) 0))
                 l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
                 d  (if (@keystates GLFW/GLFW_KEY_Q) 0.05 (if (@keystates GLFW/GLFW_KEY_A) -0.05 0))
                 to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))]
             (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
             (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
             (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
             (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
             (swap! light + (* l 0.1 dt))
             (swap! opacity-base + (* dt to))
             (swap! dist * (exp d))
             (let [origin       (add @position (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* -1.0 @dist)))
                   object-position @position
                   render-vars  (make-render-vars config/planet-config cloud-data config/render-config (aget w 0) (aget h 0)
                                                  origin @orientation (vec3 (cos @light) (sin @light) 0) 1.0)
                   shadow-vars  (opacity/opacity-and-shadow-cascade opacity-renderer planet-shadow-renderer shadow-data cloud-data
                                                                    render-vars (planet/get-current-tree tile-tree) @opacity-base)
                   object-to-world (transformation-matrix (eye 3) object-position)
                   camera-to-world (:sfsim.render/camera-to-world render-vars)
                   world-to-camera (inverse camera-to-world)
                   moved-model     (assoc-in cube-model [:sfsim.model/root :sfsim.model/transform] object-to-world)
                   w2           (quot (:sfsim.render/window-width render-vars) 2)
                   h2           (quot (:sfsim.render/window-height render-vars) 2)
                   clouds       (texture-render-color-depth
                                  w2 h2 true
                                  (clear (vec3 0 0 0) 0.0)
                                  ; Render clouds in front of planet
                                  (planet/render-cloud-planet cloud-planet-renderer render-vars shadow-vars
                                                              (planet/get-current-tree tile-tree))
                                  ; Render clouds above the horizon
                                  (planet/render-cloud-atmosphere cloud-atmosphere-renderer render-vars shadow-vars))]
               (let [program (:sfsim.model/program-colored-flat model-renderer)]
                 (use-program program)
                 (atmosphere/setup-atmosphere-uniforms program atmosphere-luts 0 true)
                 (clouds/setup-cloud-render-uniforms program cloud-data 4)
                 (clouds/setup-cloud-sampling-uniforms program cloud-data 7)
                 (setup-shadow-and-opacity-maps program shadow-data 8)
                 (uniform-float program "specular" (:sfsim.render/specular config/render-config))
                 (uniform-float program "radius" (:sfsim.planet/radius config/planet-config))
                 (uniform-float program "albedo" (:sfsim.planet/albedo config/planet-config))
                 (uniform-float program "amplification" (:sfsim.render/amplification config/render-config))
                 (uniform-float program "lod_offset" (clouds/lod-offset config/render-config cloud-data render-vars))
                 (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
                 (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
                 (uniform-matrix4 program "camera_to_world" camera-to-world)  ; TODO: remove?
                 (uniform-matrix4 program "world_to_camera" world-to-camera)
                 (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
                 (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
                 (uniform-int program "window_width" (:sfsim.render/window-width render-vars))  ; TODO: remove
                 (uniform-int program "window_height" (:sfsim.render/window-height render-vars))  ; TODO: remove
                 (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
                        (uniform-float program (str "split" idx) item))
                 (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
                        (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
                        (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item)))
                 (use-textures {0 (:sfsim.atmosphere/transmittance atmosphere-luts) 1 (:sfsim.atmosphere/scatter atmosphere-luts)
                                2 (:sfsim.atmosphere/mie atmosphere-luts) 3 (:sfsim.atmosphere/surface-radiance atmosphere-luts)
                                4 (:sfsim.clouds/worley cloud-data) 5 (:sfsim.clouds/perlin-worley cloud-data)
                                6 (:sfsim.clouds/cloud-cover cloud-data) 7 (:sfsim.clouds/bluenoise cloud-data)})
                 (use-textures (zipmap (drop 8 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                                (:sfsim.opacity/opacities shadow-vars)))))
               (onscreen-render window
                                (clear (vec3 0 1 0) 0.0)
                                ; Render cube model
                                (model/render-scene (comp model-renderer model/material-type) render-vars moved-model
                                                    model/render-mesh)
                                ; Render planet with cloud overlay
                                (planet/render-planet planet-renderer render-vars shadow-vars clouds
                                                      (planet/get-current-tree tile-tree))
                                ; Render atmosphere with cloud overlay
                                (atmosphere/render-atmosphere atmosphere-renderer render-vars clouds))
               (destroy-texture clouds)
               (opacity/destroy-opacity-and-shadow shadow-vars))
             (GLFW/glfwPollEvents)
             (swap! n inc)
             (when (zero? (mod @n 10))
               (print (format "\ro.-step (w/s) %.0f, dist (q/a) %.0f dt %.3f" @opacity-base @dist (* dt 0.001)))
               (flush))
             (swap! t0 + dt))))
  ; TODO: unload all planet tiles (vaos and textures)
  (model/unload-scene-from-opengl cube-model)
  (model/destroy-model-renderer model-renderer)
  (atmosphere/destroy-atmosphere-renderer atmosphere-renderer)
  (planet/destroy-planet-renderer planet-renderer)
  (planet/destroy-cloud-atmosphere-renderer cloud-atmosphere-renderer)
  (atmosphere/destroy-atmosphere-luts atmosphere-luts)
  (clouds/destroy-cloud-data cloud-data)
  (planet/destroy-cloud-planet-renderer cloud-planet-renderer)
  (planet/destroy-planet-shadow-renderer planet-shadow-renderer)
  (opacity/destroy-opacity-renderer opacity-renderer)
  (destroy-window window)
  (GLFW/glfwTerminate)
  (System/exit 0))
