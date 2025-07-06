;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(import '[org.lwjgl.glfw GLFW])

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow 320 240 "joystick test" 0 0))
(GLFW/glfwShowWindow window)

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwWaitEventsTimeout 0.04)  ; use glfwPollEvents for non-blocking input
       (let [present (GLFW/glfwJoystickPresent GLFW/GLFW_JOYSTICK_1)
             buffer1 (GLFW/glfwGetJoystickButtons GLFW/GLFW_JOYSTICK_1)
             buttons (byte-array (.limit buffer1))
             buffer2 (GLFW/glfwGetJoystickAxes GLFW/GLFW_JOYSTICK_1)
             axes    (float-array (.limit buffer2))
             buffer3 (GLFW/glfwGetJoystickHats GLFW/GLFW_JOYSTICK_1)
             hats    (byte-array (.limit buffer3))]
         (.get buffer1 buttons)
         (.get buffer2 axes)
         (.get buffer3 hats)
         (print (format "\rjoystick: %d, buttons: %s, axes: %s, hats: %s" (if present 1 0) (str (seq buttons)) (str (seq axes)) (str (seq hats))))
         (flush)))

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
