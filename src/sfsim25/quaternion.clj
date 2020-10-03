(ns sfsim25.quaternion)

(deftype Quaternion [a b c d]
  Object
  (equals [this other] (and (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other)))))

(defn add [p q]
  (Quaternion. (+ (.a p) (.a q)) (+ (.b p) (.b q)) (+ (.c p) (.c q)) (+ (.d p) (.d q))))
