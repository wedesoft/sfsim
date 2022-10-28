(ns sfsim25.core
  "Space flight simulator main program."
  (:import [org.lwjgl.opengl Display GL11]
           [org.lwjgl.input Keyboard])
  (:gen-class))

(defn -main
  "Space flight simulator main function"
  [& args]
  (let [running (atom true)]
    (Display/setTitle "sfsim25")
    (Display/setFullscreen true)
    (Display/create)
    (while (and @running (not (Display/isCloseRequested)))
      (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
      (Display/update)
      (Thread/sleep 20)
      (while (Keyboard/next)
        (when (Keyboard/getEventKeyState)
          (if (== (Keyboard/getEventKey) Keyboard/KEY_ESCAPE)
            (reset! running false)))))
    (Display/destroy)))
