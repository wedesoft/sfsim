;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.joystick
  "Module with functionality to read joystick input"
  (:import
    (org.lwjgl.glfw
      GLFW)))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn joystick-present?
  "Check if joystick is present (make sure to call glfwInit and glfwPollEvents first)"
  {:malli/schema [:=> [:cat] :boolean]}
  [index]
  (GLFW/glfwJoystickPresent (+ GLFW/GLFW_JOYSTICK_1 index)))


(defn get-joystick-buttons
  "Get state of joystick buttons"
  {:malli/schema [:=> [:cat] [:vector :int]]}
  [index]
  (let [buffer (GLFW/glfwGetJoystickButtons (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (byte-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(defn get-joystick-axes
  "Get state of joystick axes"
  {:malli/schema [:=> [:cat] [:vector :double]]}
  [index]
  (let [buffer (GLFW/glfwGetJoystickAxes (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (float-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(defn get-joystick-hats
  "Get state of joystick hats"
  {:malli/schema [:=> [:cat] [:vector :int]]}
  [index]
  (let [buffer (GLFW/glfwGetJoystickHats (+ GLFW/GLFW_JOYSTICK_1 index))
        array  (byte-array (.limit buffer))]
    (.get buffer array)
    (vec array)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
