(ns sfsim25.core
  (:import [com.jogamp.opengl GLProfile GLCapabilities]
           [com.jogamp.newt.opengl GLWindow]
           [com.jogamp.newt.event KeyListener KeyEvent])
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
                              (dosync (ref-set finished true)))))]
    (doto window
      (.setTitle "sfsim25")
      (.setSize 640 480)
      (.setVisible true)
      (.addKeyListener key-listener))
    (while (not @finished))
    (.destroy window)))
