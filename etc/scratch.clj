(import '[org.lwjgl BufferUtils]
        '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.opengl GL GL11])

(def width 640)
(def height 480)
(def title "scratch")

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow width height title 0 0))
(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)
(GL11/glGetString GL11/GL_VERSION)
(GL11/glClearColor 1.0 0.0 0.0 0.0)
(GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT  GL11/GL_DEPTH_BUFFER_BIT))
(GLFW/glfwSwapBuffers window)
(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)
