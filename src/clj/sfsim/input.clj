;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.input
    (:require
      [clojure.math :refer (signum)]
      [clojure.set :refer (map-invert)]
      [sfsim.config :refer (read-user-config)]
      [sfsim.util :refer (clamp dissoc-in byte-buffer->byte-array float-buffer->float-array)])
    (:import
      [clojure.lang
       PersistentQueue]
      [org.lwjgl.glfw
       GLFW
       GLFWCharCallbackI
       GLFWKeyCallbackI]
      [org.lwjgl.nuklear
       Nuklear]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-event-buffer
  "Create empty event buffer"
  []
  PersistentQueue/EMPTY)


(defn add-char-event
  "Add character input event to event buffer"
  [event-buffer codepoint]
  (conj event-buffer {::event ::char ::codepoint codepoint}))


(defn add-key-event
  "Add keyboard event to event buffer"
  [event-buffer k action mods]
  (conj event-buffer {::event ::key ::k k ::action action ::mods mods}))


(defn add-mouse-button-event
  "Add mouse button event to event buffer"
  [event-buffer button x y action mods]
  (conj event-buffer {::event ::mouse-button ::button button ::x x ::y y ::action action ::mods mods}))


(defn add-mouse-move-event
  "Add mouse move event to event buffer"
  [event-buffer x y]
  (conj event-buffer {::event ::mouse-move ::x x ::y y}))


(defn char-callback
  "GLFW callback function for character input events"
  [event-buffer]
  (reify GLFWCharCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
         (invoke
           [_this _window codepoint]
           (swap! event-buffer #(add-char-event % codepoint)))))


(defn key-callback
  "GLFW callback function for keyboard events"
  [event-buffer]
  (reify GLFWKeyCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
         (invoke
           [_this _window k _scancode action mods]
           (swap! event-buffer #(add-key-event % k action mods)))))


(defn dead-zone-continuous
  "Dead zone function for joystick axis controlling aerofoil"
  ^double [^double epsilon ^double value]
  (let [|value| (abs value)]
    (if (> |value| epsilon)
      (* (signum value) (/ (- |value| epsilon) (- 1.0 epsilon)))
      0.0)))


(defn dead-zone-three-state
  "Dead zone function for joystick axis controlling RCS thruster"
  ^double [^double epsilon ^double value]
  (let [|value| (abs value)]
    (if (> |value| epsilon)
      (signum value)
      0.0)))


(defn dead-margins
  "Dead margins function for throttle stick"
  ^double [^double epsilon ^double value]
  (let [|value| (abs value)]
    (if (>= |value| (- 1.0 epsilon))
      (signum value)
      (/ value (- 1.0 epsilon)))))


(defn add-joystick-axis-state
  "Add joystick axis state to event buffer"
  [event-buffer axis-state device axes]
  (reduce
    (fn [event-buffer [axis value]]
        (let [previous (or (get-in @axis-state [device axis]) 0.0)
              moved    (>= (abs (- ^double value ^double previous)) 0.5)]
          (when moved
            (swap! axis-state assoc-in [device axis] value))
          (conj event-buffer {::event ::joystick-axis ::device device ::axis axis ::value value ::moved moved})))
    event-buffer
    (map-indexed vector axes)))


(defn add-joystick-button-state
  "Add joystick button state to event buffer"
  [event-buffer button-state device buttons]
  (reduce
    (fn [event-buffer [button value]]
        (let [already-pressed (get-in @button-state [device button])]
          (if (not (zero? ^long value))
            (let [action (if already-pressed GLFW/GLFW_REPEAT GLFW/GLFW_PRESS)]
              (swap! button-state assoc-in [device button] true)
              (conj event-buffer {::event ::joystick-button ::device device ::button button ::action action}))
            (if already-pressed
              (do
                (swap! button-state dissoc-in [device button])
                (conj event-buffer {::event ::joystick-button ::device device ::button button ::action GLFW/GLFW_RELEASE}))
              event-buffer))))
    event-buffer
    (map-indexed vector buttons)))


(def joystick-buttons-state (atom {}))
(def joystick-axis-state (atom {}))


(defn joystick-poll
  "Get joystick state of a single joystick and add it to the event buffer"
  [event-buffer joystick-id]
  (if (GLFW/glfwJoystickPresent joystick-id)
    (let [device         (GLFW/glfwGetJoystickName joystick-id)
          axes-buffer    (GLFW/glfwGetJoystickAxes joystick-id)
          axes           (float-buffer->float-array axes-buffer)
          buttons-buffer (GLFW/glfwGetJoystickButtons joystick-id)
          buttons        (byte-buffer->byte-array buttons-buffer)]
      (-> event-buffer
          (add-joystick-axis-state joystick-axis-state device axes)
          (add-joystick-button-state joystick-buttons-state device buttons)))
    event-buffer))


(defn joysticks-poll
  "Get joystick states of all connected joysticks and add them to the event buffer"
  [event-buffer]
  (reduce joystick-poll event-buffer (range GLFW/GLFW_JOYSTICK_1 (inc GLFW/GLFW_JOYSTICK_LAST))))


(defn joystick-list
  "Get device names of connected joysticks"
  []
  (filter some? (map GLFW/glfwGetJoystickName (range GLFW/GLFW_JOYSTICK_1 (inc GLFW/GLFW_JOYSTICK_LAST)))))


(defn get-joystick-sensor-for-device
  "Get joystick axis or button index for a given device"
  [mappings device sensor-type id]
  (let [sensors-inverted (map-invert (get-in mappings [::joysticks ::devices device sensor-type]))]
    (sensors-inverted id)))


(defn get-joystick-sensor-for-mapping
  "Get joystick axis or button index for a given mapping"
  ([mappings sensor-type id]
   (get-joystick-sensor-for-mapping mappings (joystick-list) sensor-type id))
  ([mappings devices sensor-type id]
   (some identity
         (map (fn [device]
                  (when-let [axis (get-joystick-sensor-for-device mappings device sensor-type id)]
                            [device axis]))
              devices))))


(defprotocol InputHandlerProtocol
  (process-char [this codepoint])
  (process-key [this k action mods])
  (process-mouse-button [this button x y action mods])
  (process-mouse-move [this x y])
  (process-joystick-axis [this device axis value moved])
  (process-joystick-button [this device button action]))


(defn keypress?
  "Check if key is pressed"
  ^Boolean [^long action]
  (boolean (#{GLFW/GLFW_PRESS GLFW/GLFW_REPEAT} action)))


(defn shift?
  "Check if shift key is pressed"
  ^Boolean [^long mods]
  (not (zero? (bit-and mods GLFW/GLFW_MOD_SHIFT))))


(defmulti process-event
  "Dispatch event to different methods of handler depending on event type"
  (fn [event _handler] (::event event)))


(defmethod process-event ::char
  [event handler]
  (process-char handler (::codepoint event)))


(defmethod process-event ::key
  [event handler]
  (process-key handler (::k event) (::action event) (::mods event)))


(defmethod process-event ::mouse-button
  [event handler]
  (process-mouse-button handler (::button event) (::x event) (::y event) (::action event) (::mods event)))


(defmethod process-event ::mouse-move
  [event handler]
  (process-mouse-move handler (::x event) (::y event)))


(defmethod process-event ::joystick-axis
  [{::keys [device axis value moved]} handler]
  (process-joystick-axis handler device axis value moved))


(defmethod process-event ::joystick-button
  [event handler]
  (process-joystick-button handler (::device event) (::button event) (::action event)))


(defn process-events
  "Take events from the event buffer and process them"
  [event-buffer handler]
  (let [event (peek event-buffer)]
    (if event
      (if (false? (process-event event handler))
        (pop event-buffer)
        (recur (pop event-buffer) handler))
      event-buffer)))


(defn make-initial-state
  "Create initial state of game and space craft."
  []
  (atom {::menu                   false
         ::focus                  0
         ::fullscreen             false
         ::pause                  true
         ::brake                  false
         ::parking-brake          false
         ::gear-down              true
         ::aileron                0.0
         ::elevator               0.0
         ::rudder                 0.0
         ::throttle               0.0
         ::air-brake              false
         ::rcs                    false
         ::rcs-roll               0.0
         ::rcs-pitch              0.0
         ::rcs-yaw                0.0
         ::camera-rotate-x        0.0
         ::camera-rotate-y        0.0
         ::camera-rotate-z        0.0
         ::camera-distance-change 0.0
         }))


(def default-mappings
  {::keyboard
   {GLFW/GLFW_KEY_ESCAPE    ::menu
    GLFW/GLFW_KEY_ENTER     ::fullscreen
    GLFW/GLFW_KEY_P         ::pause
    GLFW/GLFW_KEY_G         ::gear
    GLFW/GLFW_KEY_B         ::brake
    GLFW/GLFW_KEY_F         ::throttle-decrease
    GLFW/GLFW_KEY_R         ::throttle-increase
    GLFW/GLFW_KEY_SLASH     ::air-brake
    GLFW/GLFW_KEY_BACKSLASH ::rcs
    GLFW/GLFW_KEY_A         ::aileron-left
    GLFW/GLFW_KEY_KP_5      ::aileron-center
    GLFW/GLFW_KEY_D         ::aileron-right
    GLFW/GLFW_KEY_W         ::elevator-down
    GLFW/GLFW_KEY_S         ::elevator-up
    GLFW/GLFW_KEY_Q         ::rudder-left
    GLFW/GLFW_KEY_E         ::rudder-right
    GLFW/GLFW_KEY_KP_2      ::camera-rotate-x-positive
    GLFW/GLFW_KEY_KP_8      ::camera-rotate-x-negative
    GLFW/GLFW_KEY_KP_6      ::camera-rotate-y-positive
    GLFW/GLFW_KEY_KP_4      ::camera-rotate-y-negative
    GLFW/GLFW_KEY_KP_1      ::camera-rotate-z-positive
    GLFW/GLFW_KEY_KP_3      ::camera-rotate-z-negative
    GLFW/GLFW_KEY_COMMA     ::camera-distance-change-positive
    GLFW/GLFW_KEY_PERIOD    ::camera-distance-change-negative
    }
   ::joysticks
   (read-user-config "joysticks.edn"
                     {::dead-zone 0.1})})


(defn menu-key
  "Key handling when menu is shown"
  [k state gui action mods]
  (let [press (keypress? action)
        shift (shift? mods)]
    (condp = k
      GLFW/GLFW_KEY_DELETE      (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_DEL press)
      GLFW/GLFW_KEY_ENTER       (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_ENTER press)
      GLFW/GLFW_KEY_BACKSPACE   (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_BACKSPACE press)
      GLFW/GLFW_KEY_UP          (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_UP press)
      GLFW/GLFW_KEY_DOWN        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_DOWN press)
      GLFW/GLFW_KEY_LEFT        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_LEFT press)
      GLFW/GLFW_KEY_RIGHT       (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_RIGHT press)
      GLFW/GLFW_KEY_HOME        (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_TEXT_START press)
      GLFW/GLFW_KEY_END         (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_TEXT_END press)
      GLFW/GLFW_KEY_LEFT_SHIFT  (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_SHIFT press)
      GLFW/GLFW_KEY_RIGHT_SHIFT (Nuklear/nk_input_key (:sfsim.gui/context gui) Nuklear/NK_KEY_SHIFT press)
      GLFW/GLFW_KEY_TAB         (when press (swap! state (fn [s] (assoc s ::focus-new ((if shift dec inc) (::focus s))))))
      GLFW/GLFW_KEY_ESCAPE      (when press (swap! state update ::menu not) false)
      true)))


(defmulti simulator-key
  "Simulation key handling when menu is hidden"
  (fn [id _state _action _mods] id))


(defmethod simulator-key nil
  [_id _state _action _mods])


(defmethod simulator-key ::menu
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::menu not)
    false))


(defmethod simulator-key ::fullscreen
  [_id state action mods]
  (when (and (= action GLFW/GLFW_PRESS) (= mods GLFW/GLFW_MOD_ALT))
    (swap! state update ::fullscreen not)))


(defmethod simulator-key ::pause
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::pause not)))


(defmethod simulator-key ::gear
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::gear-down not)))


(defn increment-clamp
  ([state k increment]
   (increment-clamp state k increment -1.0 1.0))
  ([state k increment lower upper]
   (swap! state update k #(-> ^double % (+ ^double increment) (clamp lower upper)))))


(defmethod simulator-key ::brake
  [_id state action mods]
  (if (= mods GLFW/GLFW_MOD_SHIFT)
    (when (= action GLFW/GLFW_PRESS)
      (swap! state update ::parking-brake not))
    (do
      (swap! state assoc ::parking-brake false)
      (swap! state assoc ::brake (not= action GLFW/GLFW_RELEASE)))))


(defmethod simulator-key ::throttle-decrease
  [_id state action _mods]
  (when (keypress? action)
    (increment-clamp state ::throttle -0.0625 0.0 1.0)))


(defmethod simulator-key ::throttle-increase
  [_id state action _mods]
  (when (keypress? action)
    (increment-clamp state ::throttle 0.0625 0.0 1.0)))


(defmethod simulator-key ::air-brake
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::air-brake not)))


(defmethod simulator-key ::rcs
  [_id state action _mods]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::rcs not)))


(defmethod simulator-key ::aileron-left
  [_id state action _mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-roll 1.0)
      (increment-clamp state ::aileron 0.0625))
    (swap! state assoc ::rcs-roll 0.0)))


(defmethod simulator-key ::aileron-right
  [_id state action _mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-roll -1.0)
      (increment-clamp state ::aileron -0.0625))
    (swap! state assoc ::rcs-roll 0.0)))


(defmethod simulator-key ::aileron-center
  [_id state action _mods]
  (when (keypress? action)
    (swap! state assoc ::aileron 0.0)))


(defmethod simulator-key ::rudder-left
  [_id state action mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-yaw 1.0)
      (if (= mods GLFW/GLFW_MOD_CONTROL)
        (swap! state assoc ::rudder 0.0)
        (increment-clamp state ::rudder 0.0625)))
    (swap! state assoc ::rcs-yaw 0.0)))


(defmethod simulator-key ::rudder-right
  [_id state action _mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-yaw -1.0)
      (increment-clamp state ::rudder -0.0625))
    (swap! state assoc ::rcs-yaw 0.0)))


(defmethod simulator-key ::elevator-down
  [_id state action _mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-pitch 1.0)
      (increment-clamp state ::elevator 0.0625))
    (swap! state assoc ::rcs-pitch 0.0)))


(defmethod simulator-key ::elevator-up
  [_id state action _mods]
  (if (keypress? action)
    (if (@state ::rcs)
      (swap! state assoc ::rcs-pitch -1.0)
      (increment-clamp state ::elevator -0.0625))
    (swap! state assoc ::rcs-pitch 0.0)))


(defmethod simulator-key ::camera-rotate-x-positive
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-x (if (keypress? action) 0.5 0.0)))


(defmethod simulator-key ::camera-rotate-x-negative
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-x (if (keypress? action) -0.5 0.0)))


(defmethod simulator-key ::camera-rotate-y-positive
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-y (if (keypress? action) 0.5 0.0)))


(defmethod simulator-key ::camera-rotate-y-negative
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-y (if (keypress? action) -0.5 0.0)))


(defmethod simulator-key ::camera-rotate-z-positive
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-z (if (keypress? action) 0.5 0.0)))


(defmethod simulator-key ::camera-rotate-z-negative
  [_id state action _mods]
  (swap! state assoc ::camera-rotate-z (if (keypress? action) -0.5 0.0)))


(defmethod simulator-key ::camera-distance-change-positive
  [_id state action _mods]
  (swap! state assoc ::camera-distance-change (if (keypress? action) 1.0 0.0)))


(defmethod simulator-key ::camera-distance-change-negative
  [_id state action _mods]
  (swap! state assoc ::camera-distance-change (if (keypress? action) -1.0 0.0)))


(defn menu-mouse-button
  [_state gui button x y action _mods]
  (let [nkbutton (condp = button
                   GLFW/GLFW_MOUSE_BUTTON_RIGHT Nuklear/NK_BUTTON_RIGHT
                   GLFW/GLFW_MOUSE_BUTTON_MIDDLE Nuklear/NK_BUTTON_MIDDLE
                   Nuklear/NK_BUTTON_LEFT)]
    (Nuklear/nk_input_button (:sfsim.gui/context gui) nkbutton x y (= action GLFW/GLFW_PRESS))))


(defn menu-mouse-move
  [_state gui x y]
  (Nuklear/nk_input_motion (:sfsim.gui/context gui) (long x) (long y)))


(defn simulator-joystick-with-dead-zone
  "Apply filtered joystick axis value to state"
  [aerofoil rcs-thruster epsilon state value]
  (if (@state ::rcs)
    (swap! state assoc rcs-thruster (dead-zone-three-state epsilon value))
    (swap! state assoc aerofoil (dead-zone-continuous epsilon value))))


(defmulti simulator-joystick-axis
  "Handle mapped joystick axis events"
  (fn [id _epsilon _state _value] id))


(defmethod simulator-joystick-axis nil
  [_id _epsilon _state _value])


(defmethod simulator-joystick-axis ::aileron-inverted
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::aileron ::rcs-roll epsilon state value))


(defmethod simulator-joystick-axis ::aileron
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::aileron ::rcs-roll epsilon state (- ^double value)))


(defmethod simulator-joystick-axis ::elevator-inverted
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::elevator ::rcs-pitch epsilon state value))


(defmethod simulator-joystick-axis ::elevator
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::elevator ::rcs-pitch epsilon state (- ^double value)))


(defmethod simulator-joystick-axis ::rudder-inverted
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::rudder ::rcs-yaw epsilon state value))


(defmethod simulator-joystick-axis ::rudder
  [_id epsilon state value]
  (simulator-joystick-with-dead-zone ::rudder ::rcs-yaw epsilon state (- ^double value)))


(defmethod simulator-joystick-axis ::throttle
  [_id epsilon state value]
  (swap! state assoc ::throttle (* 0.5 (- 1.0 (dead-margins epsilon value)))))


(defmethod simulator-joystick-axis ::throttle-increment
  [_id epsilon state value]
  (increment-clamp state ::throttle (* ^double (dead-zone-continuous epsilon value) -0.0625) 0.0 1.0))


(defmulti simulator-joystick-button
  "Handle mapped joystick button events"
  (fn [id _state _action] id))


(defmethod simulator-joystick-button nil
  [_id _state _action])


(defmethod simulator-joystick-button ::gear
  [_id state action]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::gear-down not)))


(defmethod simulator-joystick-button ::brake
  [_id state action]
  (swap! state assoc ::brake (keypress? action))
  (when (keypress? action)
    (swap! state assoc ::parking-brake false)))


(defmethod simulator-joystick-button ::parking-brake
  [_id state action]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state assoc ::parking-brake true)))


(defmethod simulator-joystick-button ::air-brake
  [_id state action]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state update ::air-brake not)))


(defn menu-joystick-axis
  [state device axis _value moved]
  (when moved
    (swap! state assoc ::last-joystick-axis [device axis])))


(defn menu-joystick-button
  [state device button action]
  (when (= action GLFW/GLFW_PRESS)
    (swap! state assoc ::last-joystick-button [device button])))


(defrecord InputHandler [state gui mappings]
  InputHandlerProtocol
  (process-char [_this codepoint]
    (when (::menu @state)
      (Nuklear/nk_input_unicode (:sfsim.gui/context gui) codepoint)))
  (process-key [_this k action mods]
    (let [keyboard-mappings (::keyboard @mappings)]
      (if (::menu @state)
        (-> k (menu-key state gui action mods))
        (-> k keyboard-mappings (simulator-key state action mods)))))
  (process-mouse-button [_this button x y action mods]
    (menu-mouse-button state gui button x y action mods))
  (process-mouse-move [_this x y]
    (menu-mouse-move state gui x y))
  (process-joystick-axis [_this device axis value moved]
    (if (::menu @state)
      (menu-joystick-axis state device axis value moved)
      (let [joystick (some-> @mappings ::joysticks ::devices (get device))]
        (simulator-joystick-axis (some-> joystick ::axes (get axis)) (some-> @mappings ::joysticks ::dead-zone) state value))))
  (process-joystick-button [_this device button action]
    (if (@state ::menu)
      (menu-joystick-button state device button action)
      (let [joystick (some-> @mappings ::joysticks ::devices (get device))]
        (simulator-joystick-button (some-> joystick ::buttons (get button)) state action)))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
