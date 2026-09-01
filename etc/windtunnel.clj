(require '[sfsim.graphics :as graphics])
(import '[org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI]
        '[org.lwjgl.opengl GL GL11 GL15 GL20 GL30]
        '[org.lwjgl BufferUtils])

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(def width 1280)
(def height 720)
(def window (GLFW/glfwCreateWindow width height "Windtunnel" 0 0))
(def mouse-pos (atom [0.0 0.0]))
(def mouse-button (atom false))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwSwapInterval 1)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window xpos ypos]
      (reset! mouse-pos [xpos (- height ypos 1)]))))

(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
    (invoke
      [_this _window _button action _mods]
      (reset! mouse-button (= action GLFW/GLFW_PRESS)))))

(while (not (GLFW/glfwWindowShouldClose window))
  ; (GL20/glUniform1f (GL20/glGetUniformLocation program "iTime") (GLFW/glfwGetTime))
  ; (when @mouse-button
  ;   (GL20/glUniform2f (GL20/glGetUniformLocation program "iMouse") (@mouse-pos 0) (@mouse-pos 1))
  ;   (GL20/glUniform1f (GL20/glGetUniformLocation program "pressure") (pow 0.001 (/ (@mouse-pos 1) height))))
  ; (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0)
  (GLFW/glfwSwapBuffers window)
  (GLFW/glfwPollEvents))


(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
