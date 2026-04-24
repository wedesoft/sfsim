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
