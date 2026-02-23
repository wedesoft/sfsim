;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.clock
    (:import
      (org.lwjgl.glfw
        GLFW)))


(def clock (atom nil))


(defn start-clock
  []
  (reset! clock (GLFW/glfwGetTime)))


(defn elapsed-time
  ([] (elapsed-time 0.0))
  ([min-dt] (elapsed-time min-dt 0.2))
  ([min-dt max-dt]
   (let [dt (- (GLFW/glfwGetTime) @clock)]
     (when (< dt min-dt)
       (Thread/sleep (long (* 1000.0 (- min-dt dt)))))
     (swap! clock + dt)
     (min (max min-dt dt) max-dt))))
