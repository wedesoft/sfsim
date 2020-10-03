(ns sfsim25.quaternion)

(deftype Quaternion [^double a ^double b ^double c ^double d]
  Object
  (equals [this other] (and (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other))))
  (toString [this] (str "(quaternion " a \space b \space c \space d ")")))

(defn make-quaternion [^double a ^double b ^double c ^double d]
  (Quaternion. a b c d))

(defn add [^Quaternion p ^Quaternion q]
  (Quaternion. (+ (.a p) (.a q)) (+ (.b p) (.b q)) (+ (.c p) (.c q)) (+ (.d p) (.d q))))
