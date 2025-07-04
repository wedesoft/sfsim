(import '[org.lwjgl.glfw GLFW])

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow 320 240 "joystick test" 0 0))
(GLFW/glfwShowWindow window)

(while (not (GLFW/glfwWindowShouldClose window))
        (GLFW/glfwPollEvents)
       (print "\ropen")
       (flush))

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
