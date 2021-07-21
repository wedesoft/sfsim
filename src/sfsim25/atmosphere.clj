(ns sfsim25.atmosphere
  "Functions for computing the atmosphere")

(set! *unchecked-math* true)

(defn air-density
  "Compute pressure of atmosphere at specified height"
  [height base scale]
  (* base (Math/exp (- (/ height scale)))))

(set! *unchecked-math* false)
