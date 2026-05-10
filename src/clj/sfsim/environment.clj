;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.environment)


(defprotocol Environment
  "OpenAI Gym-like protocol to define reinforcement learning environment"

  (environment-update
    [this action]
    "Updates the environment state based on the given action and returns the new state.")

  (environment-observation
    [this]
    "Returns an observation of the current state.")

  (environment-done?
    [this]
    "Returns true if the environment has reached a terminal state, otherwise false.")

  (environment-truncate?
    [this]
    "Returns true if the episode should be truncated, otherwise false.")

  (environment-reward
    [this action]
    "Returns the reward obtained by taking the given action in the current state of the environment."))
