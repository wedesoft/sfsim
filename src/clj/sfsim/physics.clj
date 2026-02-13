;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [clojure.math :refer (cos sin atan2 hypot to-radians)]
    [clojure.set :refer (union)]
    [fastmath.matrix :refer (mulv inverse)]
    [fastmath.vector :refer (vec3 mag normalize mult add sub cross)]
    [malli.core :as m]
    [sfsim.quaternion :as q]
    [sfsim.matrix :refer (matrix->quaternion)]
    [sfsim.util :refer (sqr clamp)]
    [sfsim.jolt :as jolt]
    [sfsim.astro :as astro]
    [sfsim.aerodynamics :as aerodynamics])
  (:import [fastmath.vector Vec3]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def add-schema (m/schema [:=> [:cat :some :some] :some]))
(def scale-schema (m/schema [:=> [:cat :some :double] :some]))


(def gravitational-constant 6.67430e-11)


(defn euler
  "Euler integration method"
  [y0 dt dy + *]
  (+ y0 (* (dy y0 dt) dt)))


(defn runge-kutta
  "Runge-Kutta integration method"
  {:malli/schema [:=> [:cat :some :double [:=> [:cat :some :double] :some] add-schema scale-schema] :some]}
  [y0 dt dy + *]
  (let [dt2 (/ ^double dt 2.0)
        k1  (dy y0                0.0)
        k2  (dy (+ y0 (* k1 dt2)) dt2)
        k3  (dy (+ y0 (* k2 dt2)) dt2)
        k4  (dy (+ y0 (* k3 dt)) dt)]
    (+ y0 (* (reduce + [k1 (* k2 2.0) (* k3 2.0) k4]) (/ ^double dt 6.0)))))


(defn matching-scheme
  "Use two custom speed changes to make semi-implicit Euler result match a ground truth after the integration step"
  [y0 dt y1 scale subtract]
  (if (zero? ^double dt)
    (let [delta-speed (subtract (::speed y1) (::speed y0))]
      [(scale delta-speed 0.5) (scale delta-speed 0.5)])
    (let [delta-speed0 (scale (subtract (subtract (::position y1) (::position y0)) (scale (::speed y0) dt)) (/ 1.0 ^double dt))
          delta-speed1 (subtract (subtract (::speed y1) (::speed y0)) delta-speed0)]
      [delta-speed0 delta-speed1])))


(defn gravitation
  "Determine gravitation from planetary object"
  [^Vec3 center ^double mass]
  (fn [position _speed]
      (let [radial-vector (sub position center)
            radius        (mag radial-vector)
            direction     (normalize radial-vector)
            gravity       (/ (* mass ^double gravitational-constant) (sqr radius))]
        (mult direction (- gravity)))))


(defn state-change
  "State change from position-dependent acceleration"
  [acceleration]
  (fn [{::keys [position speed]} _dt]
    {::position speed ::speed (acceleration position speed)}))


(defn state-add
  "Add two state hashmaps"
  [state1 state2]
  (merge-with add state1 state2))


(defn state-scale
  "Scale a state hashmap"
  [state scale]
  (into {} (for [[k v] state] [k (mult v scale)])))


(defn centrifugal-acceleration
  "Determine centrifugal acceleration in a rotating coordinate system"
  [omega position]
  (sub (cross omega (cross omega position))))


(defn coriolis-acceleration
  "Determine coriolis acceleration for a moving particle in a rotating coordinate system"
  [omega speed]
  (sub (mult (cross omega speed) 2.0)))


(defn make-physics-state
  "Create initial physics state"
  [body]
  {::body body
   ::start-julian-date astro/T0
   ::offset-seconds 0.0
   ::position (vec3 0 0 0)
   ::speed (vec3 0 0 0)
   ::domain ::surface
   ::display-speed 0.0
   ::throttle 0.0
   ::air-brake 0.0
   ::gear 1.0
   ::rcs-thrust (vec3 0 0 0)
   ::control-surfaces (vec3 0 0 0)
   ::brake 0.0
   ::vehicle nil})


(defn get-julian-date-ut
  "Get universal time Julian date"
  [state]
  (+ ^double (::start-julian-date state) (/ ^double (::offset-seconds state) 86400.0)))


(defn set-julian-date-ut
  "Set universal time Julian date"
  [state jd-ut]
  (-> state
      (assoc ::start-julian-date jd-ut)
      (assoc ::offset-seconds 0.0)))


(defn set-control-inputs
  "Apply inputs to space craft"
  [state inputs ^double dt]
  (-> state
      (assoc ::throttle (:sfsim.input/throttle inputs))
      (update ::air-brake (fn [^double x] (clamp (+ x (* (if (:sfsim.input/air-brake inputs) 2.0 -2.0) dt)) 0.0 1.0)))
      (update ::gear (fn [^double x] (clamp (+ x (* (if (:sfsim.input/gear-down inputs) 0.5 -0.5) dt)) 0.0 1.0)))
      (assoc ::rcs-thrust (mult (vec3 (:sfsim.input/rcs-roll inputs) (:sfsim.input/rcs-pitch inputs) (:sfsim.input/rcs-yaw inputs))
                                -1000000.0))
      (assoc ::control-surfaces (mult (vec3 (:sfsim.input/aileron inputs) (:sfsim.input/elevator inputs) (:sfsim.input/rudder inputs))
                                      (to-radians 20.0)))
      (assoc ::brake (if (:sfsim.input/brake inputs) 1.0 (if (:sfsim.input/parking-brake inputs) 0.1 0.0)))))


(defmulti set-pose
  "Set position and orientation of space craft"
  (fn [_state domain _position _orientation] domain))


(defmethod set-pose ::surface
  [state _domain position orientation]
  (jolt/set-translation (::body state) position)  ; Jolt handles position information to detect collisions with surface
  (jolt/set-orientation (::body state) orientation)  ; Jolt handles orientation information 
  (-> state
      (assoc ::domain ::surface)
      (assoc ::position (vec3 0 0 0))))  ; Position offset is zero 


(defmethod set-pose ::orbit
  [state _domain position orientation]
  (jolt/set-translation (::body state) (vec3 0 0 0))  ; Jolt only handles position changes
  (jolt/set-orientation (::body state) orientation)  ; Jolt handles orientation information 
  (-> state
      (assoc ::domain ::orbit)
      (assoc ::position position)))  ; Store double precision position 


(defmulti get-position
  "Get position of space craft"
  (fn [domain state] [(::domain state) domain]))


(defmethod get-position [::surface ::surface]
  [_domain state]
  (jolt/get-translation (::body state)))


(defmethod get-position [::surface ::orbit]
  [_domain state]
  (let [jd-ut         (get-julian-date-ut state)
        earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (mulv earth-to-icrs (jolt/get-translation (::body state)))))


(defmethod get-position [::orbit ::surface]
  [_domain state]
  (let [jd-ut         (get-julian-date-ut state)
        icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (mulv icrs-to-earth (::position state))))


(defmethod get-position [::orbit ::orbit]
  [_domain state]
  (::position state))


(defmulti get-orientation
  "Get orientation of space craft"
  (fn [domain state] [(::domain state) domain]))


(defmethod get-orientation [::surface ::surface]
  [_domain state]
  (jolt/get-orientation (::body state)))


(defmethod get-orientation [::surface ::orbit]
  [_domain state]
  (let [jd-ut             (get-julian-date-ut state)
        earth-to-icrs     (astro/earth-to-icrs jd-ut)
        earth-orientation (matrix->quaternion earth-to-icrs)]
    (q/* earth-orientation (jolt/get-orientation (::body state)))))


(defmethod get-orientation [::orbit ::surface]
  [_domain state]
  (let [jd-ut            (get-julian-date-ut state)
        icrs-to-earth    (inverse (astro/earth-to-icrs jd-ut))
        icrs-orientation (matrix->quaternion icrs-to-earth)]
    (q/* icrs-orientation (jolt/get-orientation (::body state)))))


(defmethod get-orientation [::orbit ::orbit]
  [_domain state]
  (jolt/get-orientation (::body state)))


(defn set-geographic
  "Set position by specifying longitude, latitude, and height"
  [state surface planet elevation longitude latitude height]
  (let [point       (vec3 (* (cos longitude) (cos latitude)) (* (sin longitude) (cos latitude)) (sin latitude))
        radius      (:sfsim.planet/radius planet)
        max-terrain (:sfsim.planet/max-height planet)
        terrain     (if (>= ^double height (+ ^double max-terrain ^double elevation))
                      ^double radius
                      (+ ^double (surface point) ^double elevation))]
    (set-pose state ::surface (mult point (max terrain (+ ^double radius ^double height)))
              (q/* (q/rotation longitude (vec3 0 0 1))
                   (q/rotation (+ ^double latitude (to-radians 90.0)) (vec3 0 -1 0))))))


(defn get-geographic
  "Get longitude, latitude, and height of space craft"
  [state planet]
  (let [position  (get-position :sfsim.physics/surface state)
        longitude (atan2 (.y ^Vec3 position) (.x ^Vec3 position))
        latitude  (atan2 (.z ^Vec3 position) (hypot (.x ^Vec3 position) (.y ^Vec3 position)))
        height    (- (mag position) ^double (:sfsim.planet/radius planet))]
    {:longitude longitude
     :latitude latitude
     :height height}))


(defmulti set-speed
  "Set speed vector of space craft"
  (fn [_state domain _linear-velocity _angular-velocity] domain))


(defmethod set-speed ::surface
  [state _domain linear-velocity angular-velocity]
  (jolt/set-linear-velocity (::body state) linear-velocity)
  (jolt/set-angular-velocity (::body state) angular-velocity)
  (assoc state ::speed (vec3 0 0 0)))


(defmethod set-speed ::orbit
  [state _domain linear-velocity angular-velocity]
  (jolt/set-linear-velocity (::body state) (vec3 0 0 0))
  (jolt/set-angular-velocity (::body state) angular-velocity)
  (assoc state ::speed linear-velocity))


(defmulti get-linear-speed
  "Get speed vector of space craft"
  (fn [domain state] [(::domain state) domain]))


(defmethod get-linear-speed [::surface ::surface]
  [_domain state]
  (jolt/get-linear-velocity (::body state)))


(defmethod get-linear-speed [::surface ::orbit]
  [_domain state]
  (let [jd-ut                  (get-julian-date-ut state)
        position               (jolt/get-translation (::body state))
        linear-velocity        (jolt/get-linear-velocity (::body state))
        earth-to-icrs          (astro/earth-to-icrs jd-ut)
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity position)]
    (mulv earth-to-icrs (add linear-velocity earth-local-speed))))


(defmethod get-linear-speed [::orbit ::surface]
  [_domain state]
  (let [jd-ut                  (get-julian-date-ut state)
        icrs-to-earth          (inverse (astro/earth-to-icrs jd-ut))
        linear-velocity        (mulv icrs-to-earth (::speed state))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity (get-position ::surface state))]
    (sub linear-velocity earth-local-speed)))


(defmethod get-linear-speed [::orbit ::orbit]
  [_domain state]
  (::speed state))


(defmulti get-angular-speed
  "Get angular velocity vector of space craft"
  (fn [domain state] [(::domain state) domain]))


(defmethod get-angular-speed [::surface ::surface]
  [_domain state]
  (jolt/get-angular-velocity (::body state)))


(defmethod get-angular-speed [::surface ::orbit]
  [_domain state]
  (let [jd-ut                  (get-julian-date-ut state)
        angular-velocity       (jolt/get-angular-velocity (::body state))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-to-icrs          (astro/earth-to-icrs jd-ut)]
    (mulv earth-to-icrs (add angular-velocity earth-angular-velocity))))


(defmethod get-angular-speed [::orbit ::surface]
  [_domain state]
  (let [jd-ut                  (get-julian-date-ut state)
        icrs-to-earth          (inverse (astro/earth-to-icrs jd-ut))
        angular-velocity       (mulv icrs-to-earth (jolt/get-angular-velocity (::body state)))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)]
    (sub angular-velocity earth-angular-velocity)))


(defmethod get-angular-speed [::orbit ::orbit]
  [_domain state]
  (jolt/get-angular-velocity (::body state)))


(defmulti set-domain
  "Switch reference system of space craft"
  (fn [state target] [(::domain state) target]))


(defmethod set-domain :default
  [state target]
  (assert (= target (::domain state)))
  state)


(defmethod set-domain [::surface ::orbit]
  [state _target]
  (let [position      (get-position ::orbit state)
        orientation   (get-orientation ::orbit state)
        linear-speed  (get-linear-speed ::orbit state)
        angular-speed (get-angular-speed ::orbit state)]
    (-> state
        (set-pose ::orbit position orientation)
        (set-speed ::orbit linear-speed angular-speed))))


(defmethod set-domain [::orbit ::surface]
  [state _target]
  (let [position         (get-position ::surface state)
        orientation      (get-orientation ::surface state)
        linear-velocity  (get-linear-speed ::surface state)
        angular-velocity (get-angular-speed ::surface state)]
    (-> state
        (set-pose ::surface position orientation)
        (set-speed ::surface linear-velocity angular-velocity))))


(defn update-domain
  "Switch domain of space craft depending on height"
  [state {:sfsim.planet/keys [radius space-boundary]}]
  (let [height (- (mag (get-position ::surface state)) ^double radius)]
    (set-domain state (if (>= height ^double space-boundary) ::orbit ::surface))))


(defmulti update-state
  "Perform simulation step"
  (fn [state _dt _acceleration] (::domain state)))


(defmethod update-state ::surface
  [state dt gravitation]
  (let [body         (::body state)
        mass         (jolt/get-mass body)
        initial      {::position (jolt/get-translation body) ::speed (jolt/get-linear-velocity body)}
        omega        (vec3 0 0 astro/earth-rotation-speed)
        acceleration (fn [position speed] (reduce add [(gravitation position speed)
                                                       (centrifugal-acceleration omega position)
                                                       (coriolis-acceleration omega speed)]))
        final        (runge-kutta initial dt (state-change acceleration) state-add state-scale)
        [dv1 dv2]    (matching-scheme initial dt final mult sub)]
    (jolt/set-gravity (vec3 0 0 0))
    (jolt/add-impulse body (mult dv1 mass))
    (jolt/update-system dt 1)
    (let [display-speed (mag (jolt/get-linear-velocity body))]
      (jolt/add-impulse body (mult dv2 mass))
      (-> state
          (update ::offset-seconds + dt)
          (assoc ::display-speed display-speed)))))


(defmethod update-state ::orbit
  [state dt gravitation]
  (let [body      (::body state)
        mass      (jolt/get-mass body)
        position  (::position state)
        speed     (::speed state)
        initial   {::position position ::speed speed}
        final     (runge-kutta initial dt (state-change gravitation) state-add state-scale)
        [dv1 dv2] (matching-scheme initial dt final mult sub)]
    (jolt/set-gravity (vec3 0 0 0))
    (jolt/add-impulse body (mult dv1 mass))
    (jolt/update-system dt 1)
    (jolt/add-impulse body (mult dv2 mass))
    (let [delta-position (add (mult speed dt) (jolt/get-translation body))
          delta-speed    (jolt/get-linear-velocity body)]
      (jolt/set-translation body (vec3 0 0 0))
      (jolt/set-linear-velocity body (vec3 0 0 0))
      (-> state
          (update ::offset-seconds + dt)
          (assoc ::display-speed (mag (add speed delta-speed)))
          (update ::position add delta-position)
          (update ::speed add delta-speed)))))


(defmulti add-force
  "Add force affecting space craft"
  (fn [domain state _force_] [domain (::domain state)]))


(defmethod add-force [::surface ::surface]
  [_domain state force_]
  (jolt/add-force (::body state) force_))


(defmethod add-force [::orbit ::surface]
  [_domain state force_]
  (let [jd-ut         (get-julian-date-ut state)
        icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (jolt/add-force (::body state) (mulv icrs-to-earth force_))))


(defmethod add-force [::surface ::orbit]
  [_domain state force_]
  (let [jd-ut         (get-julian-date-ut state)
        earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (jolt/add-force (::body state) (mulv earth-to-icrs force_))))


(defmethod add-force [::orbit ::orbit]
  [_domain state force_]
  (jolt/add-force (::body state) force_))


(defmulti add-torque
  "Add torque affecting space craft"
  (fn [domain state _torque_] [domain (::domain state)]))


(defmethod add-torque [::surface ::surface]
  [_domain state torque_]
  (jolt/add-torque (::body state) torque_))


(defmethod add-torque [::orbit ::surface]
  [_domain state torque_]
  (let [jd-ut         (get-julian-date-ut state)
        icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (jolt/add-torque (::body state) (mulv icrs-to-earth torque_))))


(defmethod add-torque [::surface ::orbit]
  [_domain state torque_]
  (let [jd-ut         (get-julian-date-ut state)
        earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (jolt/add-torque (::body state) (mulv earth-to-icrs torque_))))


(defmethod add-torque [::orbit ::orbit]
  [_domain state torque_]
  (jolt/add-torque (::body state) torque_))


(defn rcs-set
  "Get list of names for RCS thruster triplet given a location string"
  [location]
  [(str "RCS " location "1") (str "RCS " location "2") (str "RCS " location "3")])


(defn rcs-sets
  "Get set with names for RCS thruster triplets given a list of location strings"
  [& locations]
  (set (mapcat rcs-set locations)))


(defn active-rcs
  "Return set of names of active RCS thruster triplets"
  [state]
  (-> #{}
      (union (when (not (zero? ^double (::throttle state))) #{"Plume"}))
      (union (when (neg? ^double ((::rcs-thrust state) 0)) (rcs-sets "RD" "LU")))
      (union (when (pos? ^double ((::rcs-thrust state) 0)) (rcs-sets "LD" "RU")))
      (union (when (neg? ^double ((::rcs-thrust state) 1)) (rcs-sets "LD" "RD" "FU")))
      (union (when (pos? ^double ((::rcs-thrust state) 1)) (rcs-sets "LU" "RU" "LFD" "RFD")))
      (union (when (neg? ^double ((::rcs-thrust state) 2)) (rcs-sets "L" "RF")))
      (union (when (pos? ^double ((::rcs-thrust state) 2)) (rcs-sets "R" "LF")))))


(defn all-rcs
  "Get list of all thruster names"
  []
  (conj (mapcat rcs-set ["FF" "FU" "L" "LA" "LD" "LU" "R" "RA" "RD" "RU" "LF" "RF" "LFD" "RFD"]) "Plume"))


(defn set-thruster-forces
  "Set forces and torques of main thruster and RCS thrusters"
  [state thrust]
  (let [orientation (get-orientation ::orbit state)
        throttle    (::throttle state)
        rcs-thrust  (::rcs-thrust state)]
    (add-force ::orbit state (q/rotate-vector orientation (vec3 (* ^double throttle ^double thrust) 0 0)))
    (add-torque ::orbit state (q/rotate-vector orientation rcs-thrust))))


(defn set-aerodynamic-forces
  "Set forces and torques caused by aerodynamics"
  [state planet]
  (let [radius           (:sfsim.planet/radius planet)
        height           (- (mag (get-position ::surface state)) ^double radius)
        orientation      (get-orientation ::surface state)
        linear-velocity  (get-linear-speed ::surface state)
        angular-velocity (get-angular-speed ::surface state)
        loads  (aerodynamics/aerodynamic-loads height orientation linear-velocity angular-velocity
                                               (::control-surfaces state) (::gear state) (::air-brake state))]
    (add-force ::surface state (:sfsim.aerodynamics/forces loads))
    (add-torque ::surface state (:sfsim.aerodynamics/moments loads))))


(defn create-vehicle-constraint
  "Create vehicle constraint if it does not exist"
  [state wheels]
  (if (not (::vehicle state))
    (let [body     (::body state)
          position (get-position ::surface state)
          world-up (normalize position)]
      (assoc state ::vehicle (jolt/create-and-add-vehicle-constraint body world-up (vec3 0 0 -1) (vec3 1 0 0) wheels)))
    state))


(defn destroy-vehicle-constraint
  "Destroy vehicle constraint if it exists"
  [state]
  (if-let [vehicle (::vehicle state)]
          (do
            (jolt/remove-and-destroy-constraint vehicle)
            (assoc state ::vehicle nil))
          state))


(defn update-gear-status
  "Create or destroy vehicle constraint depending on whether gear is down or not"
  [state wheels]
  (let [gear (::gear state)]
    (if (= gear 1.0)
      (create-vehicle-constraint state wheels)
      (destroy-vehicle-constraint state))))


(defn update-brakes
  "Update vehicle constraint with brake settings"
  [state]
  (when-let [vehicle (::vehicle state)]
            (jolt/set-brake-input vehicle (::brake state))))


(defn set-wheel-angles
  "Set wheel rotation angles in radians"
  [state wheel-angles]
  (when-let [vehicle (::vehicle state)]
    (jolt/set-wheel-rotation-angle vehicle 0 (nth wheel-angles 0))
    (jolt/set-wheel-rotation-angle vehicle 1 (nth wheel-angles 1))
    (jolt/set-wheel-rotation-angle vehicle 2 (nth wheel-angles 2))))


(defn get-wheel-angles
  "Get wheel rotation angles in radians"
  [state]
  (if-let [vehicle (::vehicle state)]
          [(jolt/get-wheel-rotation-angle vehicle 0)
           (jolt/get-wheel-rotation-angle vehicle 1)
           (jolt/get-wheel-rotation-angle vehicle 2)]
          [0.0 0.0 0.0]))


(defn set-suspension
  "Set suspension lengths in meters"
  [state suspension]
  (when-let [vehicle (::vehicle state)]
    (jolt/set-suspension-length vehicle 0 (nth suspension 0))
    (jolt/set-suspension-length vehicle 1 (nth suspension 1))
    (jolt/set-suspension-length vehicle 2 (nth suspension 2))))


(defn get-suspension
  "Get suspension lengths in meters"
  [state]
  (if-let [vehicle (::vehicle state)]
          [(jolt/get-suspension-length vehicle 0)
           (jolt/get-suspension-length vehicle 1)
           (jolt/get-suspension-length vehicle 2)]
          [(+ 0.8 0.8128) (+ 0.8 0.8128) (+ 0.5 0.5419)]))


(defn save-state
  "Convert most of physics state to a serializable representation"
  [state]
  (let [domain (::domain state)]
    {::start-julian-date (::start-julian-date state)
     ::offset-seconds (::offset-seconds state)
     ::domain domain
     ::position (get-position domain state)
     ::orientation (get-orientation domain state)
     ::throttle (::throttle state)
     ::gear (::gear state)
     ::wheel-angles (get-wheel-angles state)
     ::suspension (get-suspension state)
     ::rcs-thrust (::rcs-thrust state)}))


(defn load-state
  "Load physics state from serializable representation"
  [state data wheels]
  (let [domain (::domain data)
        result (-> state
                   (assoc ::start-julian-date (::start-julian-date data))
                   (assoc ::offset-seconds (::offset-seconds data))
                   (set-domain domain)
                   (set-pose domain (apply vec3 (::position data)) (q/map->Quaternion (::orientation data)))
                   (assoc ::throttle (::throttle data))
                   (assoc ::gear (::gear data))
                   (assoc ::rcs-thrust (apply vec3 (::rcs-thrust data))))]
    (update-gear-status result wheels)
    (set-wheel-angles result (::wheel-angles data))
    (set-suspension result (::suspension data))
    result))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
