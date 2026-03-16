;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-clock
    (:require [malli.dev.pretty :as pretty]
              [malli.instrument :as mi]
              [midje.sweet :refer :all]
              [sfsim.clock :refer :all])
    (:import
      (org.lwjgl.glfw
        GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(facts "Test timing code"
       (start-clock)
       (elapsed-time) => (roughly 0.0 1e-2)
       (Thread/sleep 10)
       (elapsed-time) => (roughly 0.01 1e-2)
       (Thread/sleep 10)
       (elapsed-time) => (roughly 0.01 1e-2)
       (elapsed-time 0.01) => (roughly 0.01 1e-2)
       (Thread/sleep 20)
       (elapsed-time 0.0 0.01) => (roughly 0.01 1e-2))

(GLFW/glfwTerminate)
