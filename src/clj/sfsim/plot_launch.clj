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
      [sfsim.physics :refer (gravitational-constant)]
      [sfsim.util :refer (cube)]
      [sfsim.mlp :refer (tensor toitem tolist without-gradient)]
      [sfsim.launch :refer (LaunchActor config) :as launch]))


(defn actor []
  (let [result (LaunchActor 6 64 3)]
    (py. result load_state_dict (torch/load "actor.pt"))
    result))


(defn advance [actor state]
  (let [observation (tensor (launch/observation state config))
        input       (without-gradient (py. actor deterministic_act observation))]
    (launch/update-state state (launch/action (tolist input)) config)))


(defn setup []
  (let [actor      (actor)
        state      (launch/setup config :latitude 0.0 :longitude 0.0 :height 0.0)
        trajectory (take-while #(not (or (launch/done? % config) (launch/truncate? % config))) (iterate #(advance actor %) state))]
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
      (q/stroke 0 255 0)
      (doseq [position (map :position trajectory)]
             (let [x (* (position 0) scale)
                   y (* (position 1) scale)]
               (q/point x y))))))


(defn -main [& _args]
  (q/defsketch launch-view
    :title "Orbital launch attempt"
    :size [1024 1024]
    :setup setup
    :draw draw
    :middleware [m/fun-mode]
    :on-close (fn [& _] (System/exit 0))))
