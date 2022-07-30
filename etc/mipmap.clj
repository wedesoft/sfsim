(require '[clojure.core.matrix :refer (matrix)]
         '[sfsim25.render :refer :all])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat GL11 GL12 GL30]
        '[org.lwjgl.input Keyboard])

(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)
(Keyboard/create)

(def vertex-texture "#version 410 core
in highp vec3 point;
in mediump vec2 uv;
out lowp vec3 color;
out mediump vec2 uv_fragment;
void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}")

(def fragment-texture-2d "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
uniform sampler2D tex;
uniform float lod;
void main()
{
  fragColor = textureLod(tex, uv_fragment, lod).rgb;
}")

(def data (for [i (range 4) j (range 4)] (if (zero? (mod (+ i j) 2)) 1 0)))
(def tex (make-float-texture-2d {:width 4 :height 4 :data (float-array data)}))
(with-2d-texture (:texture tex) (GL30/glGenerateMipmap GL11/GL_TEXTURE_2D))

(def indices [0 1 3 2])
(def vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0])
(def program (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d]))
(def vao (make-vertex-array-object program indices vertices [:point 3 :uv 2]))

(def lod (atom 0.0))
(def keystates (atom {}))

(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (when (@keystates Keyboard/KEY_Q) (reset! lod (max 0.0 (- @lod 0.0005))))
       (when (@keystates Keyboard/KEY_A) (reset! lod (min 4.0 (+ @lod 0.0005))))
       (onscreen-render 640 480
                        (clear (matrix [0.0 0.0 0.0]))
                        (use-program program)
                        (uniform-sampler program :tex 0)
                        (uniform-float program :lod @lod)
                        (use-textures tex)
                        (render-quads vao))
       (print "\r" (format "%5.3f    " @lod)))


(destroy-texture tex)
(destroy-vertex-array-object vao)
(destroy-program program)
(Keyboard/destroy)
(Display/destroy)
