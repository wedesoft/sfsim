(require '[sfsim25.atmosphere :refer :all])
(require '[sfsim25.interpolate :refer :all])
(require '[sfsim25.render :refer :all])
(require '[sfsim25.util :refer (sqr get-vector3 slurp-floats)])
(require '[sfsim25.matrix :refer (pack-matrices)])
(require '[sfsim25.shaders :as shaders])
(require '[clojure.core.matrix :refer (matrix mget)])
(require '[clojure.math :refer (sin cos PI sqrt)])
(require '[comb.template :as template])
(require '[sfsim25.conftest :refer (roughly-matrix shader-test vertex-passthrough)])

(def radius 6378000)
(def max-height 35000)
(def ray-steps 10)
(def size1 9)
(def size2 9)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000})
(def scatter [mie rayleigh])
(def transmittance-earth (partial transmittance earth scatter ray-steps))
(def transmittance-space-earth (transmittance-space earth [size1 size2]))
(def point-scatter-earth (partial point-scatter-base earth scatter ray-steps (matrix [1 1 1])))
(def ray-scatter-earth (partial ray-scatter earth scatter ray-steps point-scatter-earth))
(def T (interpolate-function transmittance-earth transmittance-space-earth))
(def ray-scatter-space-earth (ray-scatter-space earth [size1 size2 size2 size2]))
(def S (interpolate-function ray-scatter-earth ray-scatter-space-earth))

(T (matrix [radius 0 0]) (matrix [-1 0 0]) false)
(T (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(transmittance-earth (matrix [radius 0 0]) (matrix [-1 0 0]) false)
(transmittance-earth (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)

(def forward (:sfsim25.interpolate/forward transmittance-space-earth))
(def backward (:sfsim25.interpolate/backward transmittance-space-earth))
(forward (matrix [radius 0 0]) (matrix [-1 0 0]) false)
(forward (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(apply T (backward 0.0 4.0))
(apply transmittance-earth (backward 0 4))
(apply transmittance-earth (backward 0.0 4.0))

(transmittance-earth (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
; [0.816777986276515,0.6339574577660779,0.33261999049530494]
(T (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
; [0.7870684887916732,0.5993094473355886,0.31630420500679357]
(ray-scatter-earth (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
; [0.04053243467757836,0.049369759514462486,0.04919703354249489]
(S (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
; [0.05914855088446305,0.05950909164504497,0.04613921998145621]

(map
  (fn [angle]
      (let [direction (matrix [(cos angle) (sin angle) 0])]
        (println (transmittance-earth (matrix [(+ radius max-height) 0 0]) direction true)
                 (forward (matrix [(+ radius max-height) 0 0]) direction true)
                 (T (matrix [(+ radius max-height) 0 0]) direction true))))
     (range (/ (- PI) 2) (/ (+ PI) 2) 0.2))


(def radius 6378000.0)
(def height 100000.0)
(def planet {:sfsim25.sphere/radius radius :sfsim25.atmosphere/height height})
(def space (transmittance-space earth [15 17]))
(def forward (:sfsim25.interpolate/forward space))
(def backward (:sfsim25.interpolate/backward space))

(def elevation-to-index-probe
  (template/fn [x y z dx dy dz above-horizon]
"#version 410 core
out lowp vec3 fragColor;
float elevation_to_index(vec3 point, vec3 direction, bool above_horizon);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  float result = elevation_to_index(point, direction, <%= above-horizon %>);
  fragColor = vec3(result, 0, 0);
}"))

(def elevation-to-index-test
  (shader-test
    (fn [program radius max-height]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    elevation-to-index-probe shaders/elevation-to-index shaders/horizon-distance shaders/limit-quot))

(elevation-to-index-test [radius max-height] [radius 0 0 1 0 0 true])

(defn elevation-to-index2 [planet size point direction above-horizon]
  (mget (elevation-to-index-test [(:sfsim25.sphere/radius planet) (:sfsim25.atmosphere/height planet)]
                                 [(mget point 0) (mget point 1) (mget point 2)
                                  (mget direction 0) (mget direction 1) (mget direction 2)
                                  above-horizon]) 0))

(elevation-to-index planet 2 (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
(elevation-to-index2 planet 2 (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)

(def transmittance-forward-probe
  (template/fn [x y z dx dy dz above]
"#version 410 core
out lowp vec3 fragColor;
vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  fragColor.rg = transmittance_forward(point, direction, <%= above %>);
  fragColor.b = 0;
}"))

(def transmittance-forward-test
  (shader-test
    (fn [program radius max-height]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    transmittance-forward-probe shaders/transmittance-forward shaders/height-to-index shaders/horizon-distance
    shaders/elevation-to-index shaders/limit-quot))

(defn forward2 [point direction above-horizon]
  (let [v (transmittance-forward-test [radius max-height]
                                      [(mget point 0) (mget point 1) (mget point 2)
                                       (mget direction 0) (mget direction 1) (mget direction 2)
                                       above-horizon])]
    [(* (dec size1) (mget v 0)) (* (dec size2) (mget v 1))]))


(def radius 6378000)
(def max-height 100000)
(def ray-steps 10)
(def size 7)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000})
(def scatter [mie rayleigh])
(def transmittance-earth (partial transmittance earth scatter ray-steps))
(def transmittance-space-earth (transmittance-space earth [size size]))
(def T (pack-matrices (make-lookup-table (interpolate-function transmittance-earth transmittance-space-earth) transmittance-space-earth)))

(def transmittance-probe
  (template/fn [px py pz dx dy dz] "#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_outer(vec3 point, vec3 direction);
void main()
{
  vec3 point = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  fragColor = transmittance_outer(point, direction);
}"))

(defn transmittance-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                transmittance (make-vector-texture-2d {:width size :height size :data T})
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render-color
                                1 1 true
                                (use-program program)
                                (uniform-sampler program :transmittance 0)
                                (apply setup program uniforms)
                                (use-textures transmittance)
                                (render-quads vao))
                img           (texture->vectors3 tex 1 1)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-texture transmittance)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def transmittance-outer-test
  (transmittance-shader-test
    (fn [program height-size elevation-size radius max-height]
        (uniform-int program :height_size height-size)
        (uniform-int program :elevation_size elevation-size)
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    transmittance-probe transmittance-outer shaders/transmittance-forward shaders/height-to-index
    shaders/elevation-to-index shaders/interpolate-2d shaders/convert-2d-index shaders/is-above-horizon
    shaders/horizon-angle shaders/horizon-distance shaders/limit-quot))

(transmittance-outer-test [17 17 radius max-height] [0 (+ radius 1000) 0 0 (sin 0.2) (- (cos 0.2))])
(transmittance-earth (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)

(transmittance-outer-test [17 17 radius max-height] [(+ radius max-height) 0 0 1 0 0])
(transmittance-earth (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(forward (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(backward 6.0 3.0)
(apply transmittance-earth (backward 3.0 6.0))  ; Indices are swapped?
(apply transmittance-earth (backward 6.0 3.0))
(take 3 (drop (+ (* 6 7 3) (* 3 3)) T))
(count T)

(def radius 6378000.0)
(def max-height 35000.0)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height})
(def height-size 64)
(def elevation-size 255)
(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def table (mapv (fn [row] (map matrix (partition 3 row))) (partition (* 3 elevation-size) data)))
(def transmittance-space-earth (transmittance-space earth [height-size elevation-size]))
(def forward (:sfsim25.interpolate/forward transmittance-space-earth))
(def backward (:sfsim25.interpolate/backward transmittance-space-earth))
(def T (interpolation-table table transmittance-space-earth))
(T (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(take 100 (map matrix (partition 3 data)))
(def ray-scatter-space-earth (ray-scatter-space earth [height-size elevation-size light-elevation-size heading-size]))


(def radius 6378000.0)
(def max-height 35000.0)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height})
(def height-size 32)
(def elevation-size 128)
(def light-elevation-size 32)
(def heading-size 8)
(def ray-scatter-shape [height-size elevation-size light-elevation-size heading-size])
(def ray-scatter-space-planet (ray-scatter-space earth ray-scatter-shape))
(def forward (:sfsim25.interpolate/forward ray-scatter-space-planet))
(def backward (:sfsim25.interpolate/backward ray-scatter-space-planet))
(def S (partial ray-scatter earth scatter ray-steps point-scatter-base-planet))
(S (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 1 0]) true)
(def transmittance-shape [height-size elevation-size])
(def transmittance-space-earth (transmittance-space earth transmittance-shape))
(def forward (:sfsim25.interpolate/forward transmittance-space-earth))
(def backward (:sfsim25.interpolate/backward transmittance-space-earth))
(height-to-index earth (matrix [6377999.999549146,-16.87508805500576,73.93459155883768]))
(forward (matrix [6377999.999549146,-16.87508805500576,73.93459155883768]) (matrix [-0.19999999999999998,-0.9797958971132712,0.0]) false)
