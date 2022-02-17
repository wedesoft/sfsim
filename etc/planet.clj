(require '[clojure.core.matrix :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* false)

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(Display/destroy)

(set! *unchecked-math* false)
