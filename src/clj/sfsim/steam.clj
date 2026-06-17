;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.steam
    "Record Steam achievements"
    (:require
      [clojure.tools.logging :as log]
      [fastmath.vector :refer (mag)]
      [sfsim.config :as config]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.physics :as physics]
      )
    (:import
      (com.codedisaster.steamworks
        SteamLibraryLoaderLwjgl3
        SteamAPI
        SteamUserStats
        SteamUserStatsCallback)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def stats-callback
  "Call back for user statistics"
  (reify SteamUserStatsCallback
    (onUserStatsReceived [_this _gameId steamIDUser _result]
      (log/info "Stats received for user:" steamIDUser))
    (onUserStatsStored [_this _gameId result]
      (log/info "Stats stored! Result code:" result))
    (onUserAchievementStored [_this _gameId _isGroup achievementName _currentProgress _maxProgress]
      (log/info "Achievement saved to server:" achievementName))))


(defn get-user-stats
  "Get user statistics"
  []
  (let [result (SteamUserStats. stats-callback)]
    (.requestGlobalStats result 0)
    result))


(defn initialize
  "Initialize Steam API"
  []
  (let [steam-lib-loader    (SteamLibraryLoaderLwjgl3.)
        steam-libs-loaded   (SteamAPI/loadLibraries steam-lib-loader)
        steam-api-ready     (if steam-libs-loaded (SteamAPI/init) false)
        user-stats          (if steam-api-ready (get-user-stats) nil)]
    (log/info "Steam libraries" (if steam-libs-loaded "loaded successfully" "failed to load"))
    (log/info "Steam API" (if steam-api-ready "initialised" "not available"))
    user-stats))


(defn run-callbacks
  "Run Steam API callbacks"
  []
  (SteamAPI/runCallbacks))


(defn destroy
  "Destroy Steam API"
  []
  (log/info "shutting down Steam API")
  (SteamAPI/shutdown))


(defn achievement-unlocked?
  "Check if achievement is unlocked"
  [^SteamUserStats user-stats achievement-id]
  (.isAchieved user-stats achievement-id false))


(defn safe-unlock-achievement!
  "Unlock achievement if not already unlocked"
  [^SteamUserStats user-stats achievement-id]
  (try
    (when-not (achievement-unlocked? user-stats achievement-id)
              (if (.setAchievement user-stats achievement-id)
                (.storeStats user-stats)
                (log/warn "Failed to set achievement" achievement-id)))
    (catch Exception e
      (log/error "Error updating achievements:" (.getMessage e)))))


(defn debug-reset-all-achievements!
  "Reset all achievements"
  [^SteamUserStats user-stats]
  (.resetAllStats user-stats true)
  (.storeStats user-stats)
  (.requestGlobalStats user-stats 0))


(defn detect-achievements
  "Detect Steam achievements"
  [user-stats physics-state]
  (let [object-position (physics/get-position :sfsim.physics/surface physics-state)
        earth-radius    (:sfsim.planet/radius config/planet-config)
        leo             (:sfsim.planet/low-orbit config/planet-config)
        height          (- (mag object-position) ^double earth-radius)
        speed-of-sound  (atmosphere/speed-of-sound (atmosphere/temperature-at-height height))
        speed           (mag (physics/get-linear-speed :sfsim.physics/surface physics-state))]
    (when (> speed speed-of-sound)
      (safe-unlock-achievement! user-stats "SUPERSONIC"))
    (when (> height (:sfsim.planet/karman-line config/planet-config))
      (safe-unlock-achievement! user-stats "EDGEOFSPACE"))
   (when (and (>= (physics/periapsis config/planet-config physics-state) (+ ^double earth-radius ^double leo))
                            (or (>= (physics/eccentricity config/planet-config physics-state) 1.0)
                                (>= (physics/apoapsis config/planet-config physics-state) (+ ^double earth-radius ^double leo))))
      (safe-unlock-achievement! user-stats "EARTHORBIT"))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
