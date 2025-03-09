(require '[clojure.math :refer (to-radians)]
         '[sfsim.aerodynamics :refer :all])

(def cl (compose (basic-lift 1.1) (glide 0.8 (to-radians 13) 0.5 (to-radians 12)) (tail 0.5 (to-radians 8) (to-radians 12))))

; Create GNUplot Dat file
(spit "etc/cl.dat"
      (with-out-str
        (doseq [alpha (range -180.0 180.1 0.1)]
               (println alpha (cl (to-radians alpha))))))
