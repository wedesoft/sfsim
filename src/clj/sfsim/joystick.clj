;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.joystick
  "Module with functionality to read joystick input"
  (:import
    (org.lwjgl.glfw
      GLFW)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn joystick-present?
  "Check if joystick is present (make sure to call glfwInit and glfwPollEvents first)"
  [^long index]
  (GLFW/glfwJoystickPresent (+ GLFW/GLFW_JOYSTICK_1 index)))


(defn get-joystick-buttons
  "Get state of joystick buttons"
  [^long index]
  (let [buffer (GLFW/glfwGetJoystickButtons (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (byte-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(defn get-joystick-axes
  "Get state of joystick axes"
  [^long index]
  (let [buffer (GLFW/glfwGetJoystickAxes (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (float-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(defn get-joystick-hats
  "Get state of joystick hats"
  [^long index]
  (let [buffer (GLFW/glfwGetJoystickHats (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (byte-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
