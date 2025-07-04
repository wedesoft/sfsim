(import '[org.lwjgl.glfw GLFW])

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow 320 240 "joystick test" 0 0))
(GLFW/glfwShowWindow window)

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwWaitEventsTimeout 0.04)  ; use glfwPollEvents for non-blocking input
       (let [present (GLFW/glfwJoystickPresent GLFW/GLFW_JOYSTICK_1)
             buffer  (GLFW/glfwGetJoystickButtons GLFW/GLFW_JOYSTICK_1)
             buttons (byte-array (.limit buffer))]
         (.get buffer buttons)
         (print (format "\rjoystick: %d, buttons: %s" (if present 1 0) (str (seq buttons))))
         (flush)))

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
