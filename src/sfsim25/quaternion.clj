(ns sfsim25.quaternion)

(deftype Quaternion [^double a ^double b ^double c ^double d]
  Object
  (equals [this other] (and (instance? Quaternion other) (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other))))
  (toString [this] (str "(quaternion " a \space b \space c \space d ")")))

(defn make-quaternion [^double a ^double b ^double c ^double d]
  "Construct a quaternion"
  (Quaternion. a b c d))

(defn add [^Quaternion p ^Quaternion q]
  "Add two quaternions"
  (Quaternion. (+ (.a p) (.a q)) (+ (.b p) (.b q)) (+ (.c p) (.c q)) (+ (.d p) (.d q))))

(defn subtract [^Quaternion p ^Quaternion q]
  "Subtract two quaternions"
  (Quaternion. (- (.a p) (.a q)) (- (.b p) (.b q)) (- (.c p) (.c q)) (- (.d p) (.d q))))

(defn multiply [^Quaternion p ^Quaternion q]
  "Multiply two quaternions"
  (make-quaternion
    (- (* (.a p) (.a q)) (* (.b p) (.b q)) (* (.c p) (.c q)) (* (.d p) (.d q)))
    (- (+ (* (.a p) (.b q)) (* (.b p) (.a q)) (* (.c p) (.d q))) (* (.d p) (.c q)))
    (+ (- (* (.a p) (.c q)) (* (.b p) (.d q))) (* (.c p) (.a q)) (* (.d p) (.b q)))
    (+ (- (+ (* (.a p) (.d q)) (* (.b p) (.c q))) (* (.c p) (.b q))) (* (.d p) (.a q)))))
