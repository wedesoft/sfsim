;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.plot-launch
    "Plot launch trajectory using current policy/actor"
    (:gen-class)
    (:require
      [clojure.math :refer (PI sqrt round)]
      [quil.core :as q]
      [quil.middleware :as m]
      [libpython-clj2.python :refer (py.) :as py]
      [fastmath.vector :refer (vec3 mag)]
      [sfsim.physics :refer (gravitational-constant)]
      [sfsim.util :refer (cube)]
      [sfsim.mlp :refer (tensor toitem tolist without-gradient)]
      [sfsim.launch :refer (LaunchActor config) :as launch]))


(defn actor []
  (let [result (LaunchActor 4 64 2)]
    (py. result load_state_dict (torch/load "actor.pt"))
    result))


(defn advance [actor {:keys [state]}]
  (let [observation (tensor (launch/observation state config))
        input       (without-gradient (py. actor deterministic_act observation))
        action      (launch/action (tolist input))]
    {:state (launch/update-state state action config) :action action}))


(defn take-while-plus-one [pred coll]
  (let [[matching remaining] (split-with pred coll)]
    (if (seq remaining)
      (concat matching [(first remaining)])
      matching)))


(defn setup []
  (let [actor      (actor)
        sample     {:state (launch/setup config :latitude 0.0 :longitude 0.0 :height 0.0) :action {:control (vec3 0 0 0)}}
        trajectory (take-while-plus-one
                     #(not (or (launch/done? (:state %) config) (launch/truncate? (:state %) config)))
                     (iterate #(advance actor %) sample))]
    (q/no-loop)
    (q/smooth)
    (q/background 0)
    {:trajectory (doall trajectory)}))


(defn draw [{:keys [trajectory]}]
  (q/with-translation [(/ (q/width) 2) (/ (q/height) 2)]
    (let [radius  (:radius config)
          orbit   (:orbit  config)
          ceiling (+ radius (* 2 orbit))
          scale   (/ (q/height) 2 ceiling)]
      (q/no-fill)
      (q/stroke 150 150 150)
      (q/ellipse 0 0 (* 2 radius scale) (* 2 radius scale))
      (doseq [[i sample] (map-indexed vector trajectory)]
             (let [position (-> sample :state :position)
                   x        (* (position 0) scale)
                   y        (* (position 1) scale)
                   control  (-> sample :action :control)
                   thrust   (mag control)
                   done     (launch/done? (:state sample) config)
                   truncate (launch/truncate? (:state sample) config)]
               (q/stroke (* thrust 255) (* (- 1 thrust) 255) 0)
               (cond
                 done     (q/ellipse x y 5 5)
                 truncate (do (q/line (- x 2) (- y 2) (+ x 2) (+ y 2)) (q/line (- x 2) (+ y 2) (+ x 2) (- y 2)))
                 :else (q/point x y))
               (when (zero? (mod i 10))
                 (let [dx    (* (control 0) 10)
                       dy    (* (control 1) 10)]
                   (q/line x y (+ x dx) (+ y dy)))))))))


(defn -main [& _args]
  (q/defsketch launch-view
    :title "Orbital launch attempt"
    :size [1024 1024]
    :setup setup
    :draw draw
    :middleware [m/fun-mode]
    :on-close (fn [& _] (System/exit 0))))
