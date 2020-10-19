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
        running         (atom true)
        key-listener    (reify KeyListener
                          (keyPressed [this event]
                            (if (= (.getKeyCode event) KeyEvent/VK_ESCAPE)
                              (swap! running not)))
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
    (while @running
      (Thread/sleep 40))
    (.destroy window)))
