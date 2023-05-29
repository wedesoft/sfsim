(ns sfsim25.core
  "Space flight simulator main program."
  (:import [org.lwjgl.opengl GL GL11]
           [org.lwjgl.glfw GLFW])
  (:gen-class))

(defn -main
  "Space flight simulator main function"
  [& args]
  (let [running (atom true)]
    (GLFW/glfwInit)
    (GLFW/glfwDefaultWindowHints)
    (let [window (GLFW/glfwCreateWindow 320 240 "scratch" 0 0)]
      (GLFW/glfwMakeContextCurrent window)
      (GLFW/glfwShowWindow window)
      (GL/createCapabilities)
      (while (not (GLFW/glfwWindowShouldClose window))
             (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
             (GLFW/glfwSwapBuffers window)
             (GLFW/glfwPollEvents))
      (GLFW/glfwDestroyWindow window)
      (GLFW/glfwTerminate))))
