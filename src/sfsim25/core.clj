(ns sfsim25.core
  (:import [com.jogamp.opengl GLProfile GLCapabilities]
           [com.jogamp.newt.opengl GLWindow]
           [com.jogamp.newt.event KeyListener KeyEvent WindowAdapter])
  (:gen-class))

(defn -main
  "Main program opening a window"
  [& args]
  (let [gl-capabilities (-> GLProfile/GL3 GLProfile/get GLCapabilities.)
        window          (GLWindow/create gl-capabilities)
        finished        (ref false)
        key-listener    (reify KeyListener
                          (keyPressed [this event]
                            (if (= (.getKeyCode event) KeyEvent/VK_ESCAPE)
                              (dosync (ref-set finished true))))
                          (keyReleased [this event]))
        window-listener (proxy [WindowAdapter] []
                          (windowDestroyed [event]
                            (System/exit 0)))]
    (doto window
      (.setTitle "sfsim25")
      (.setFullscreen true)
      (.setVisible true)
      (.addKeyListener key-listener)
      (.addWindowListener window-listener))
    (while (not @finished))
    (.destroy window)))
