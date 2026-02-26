;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-astro
  (:require
    [clojure.math :refer (PI)]
    [fastmath.matrix :refer (mat3x3 mulm mulv eye rotation-matrix-3d-x rotation-matrix-3d-y rotation-matrix-3d-z)]
    [fastmath.vector :refer (vec3)]
    [gloss.core :refer (sizeof)]
    [instaparse.core :as insta]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.astro :refer :all :as astro]
    [sfsim.conftest :refer (roughly-matrix roughly-vector)]
    [sfsim.matrix :refer :all])
  (:import
    (java.nio.charset
      StandardCharsets)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


;; Header test data cut from de430_1850-2150.bsp: dd if=de430_1850-2150.bsp of=spk-head.bsp bs=1024 count=80
(fact "Map file to a read-only byte buffer"
      (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/test.txt")
            b      (byte-array 4)]
        (.get buffer b)
        (String. b StandardCharsets/US_ASCII) => "Test"))


(facts "Read header from DAF file"
       (let [header (read-daf-header (map-file-to-buffer "test/clj/sfsim/fixtures/astro/spk-head.bsp"))]
         (:sfsim.astro/locidw header) => "DAF/SPK "
         (:sfsim.astro/num-doubles header) => 2
         (:sfsim.astro/num-integers header) => 6
         (:sfsim.astro/forward header) => 53
         (:sfsim.astro/backward header) => 53
         (:sfsim.astro/free header) => 4089585
         (:sfsim.astro/locfmt header) => "LTL-IEEE"
         (:sfsim.astro/ftpstr header) => (map int "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")))


(facts "Check endianness"
       (check-endianness {:sfsim.astro/locfmt "LTL-IEEE"}) => true
       (check-endianness {:sfsim.astro/locfmt "BIG-IEEE"}) => false)


(facts "Check FTP string"
       (check-ftp-str {:sfsim.astro/ftpstr (map int "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")}) => true
       (check-ftp-str {:sfsim.astro/ftpstr (map int "unexpected text")}) => false)


(facts "Extract comment"
       (let [buffer  (map-file-to-buffer "test/clj/sfsim/fixtures/astro/spk-head.bsp")
             header  (read-daf-header buffer)
             cmt     (read-daf-comment header buffer)]
         (subs cmt 0 67) => "JPL planetary and lunar ephemeris DE430\n\nIntegrated 29 March 2013\n\n"
         (count cmt) => 50093))


(facts "Size of summary frame"
       (sizeof (daf-descriptor-frame 1 0)) => 8
       (sizeof (daf-descriptor-frame 2 0)) => 16
       (sizeof (daf-descriptor-frame 0 2)) => 8
       (sizeof (daf-descriptor-frame 0 4)) => 16
       (sizeof (daf-descriptor-frame 0 3)) => 16)


(facts "Read summaries from a record"
       (let [buffer    (map-file-to-buffer "test/clj/sfsim/fixtures/astro/spk-head.bsp")
             header    (read-daf-header buffer)
             summaries (read-daf-descriptor header (:sfsim.astro/forward header) buffer)]
         (:sfsim.astro/next-number summaries) => 0.0
         (:sfsim.astro/previous-number summaries) => 0.0
         (count (:sfsim.astro/descriptors summaries)) => 14
         (:sfsim.astro/doubles (first (:sfsim.astro/descriptors summaries))) => [-4.734072E9 4.735368E9]
         (:sfsim.astro/integers (first (:sfsim.astro/descriptors summaries))) => [1 0 1 2 6913 609716]))


(fact "Read source names from record"
      (let [buffer  (map-file-to-buffer "test/clj/sfsim/fixtures/astro/spk-head.bsp")
            header  (read-daf-header buffer)
            sources (read-source-names (inc (:sfsim.astro/forward header)) 14 buffer)]
        sources => (repeat 14 "DE-0430LE-0430")))


(facts "Read multiple summary records"
       (with-redefs [astro/read-daf-descriptor
                     (fn [header index buffer]
                       (fact index => 53)
                       {:sfsim.astro/next-number 0.0
                        :sfsim.astro/descriptors [{:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}]})
                     astro/read-source-names
                     (fn [index n buffer]
                       (fact index => 54)
                       ["DE-0430LE-0430"])]
         (read-daf-summaries {:sfsim.astro/forward 53} 53 :buffer)
         => [{:sfsim.astro/source "DE-0430LE-0430" :sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}])
       (with-redefs [astro/read-daf-descriptor
                     (fn [header index buffer]
                       ({53 {:sfsim.astro/next-number 55.0 :sfsim.astro/descriptors [{:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}]}
                         55 {:sfsim.astro/next-number  0.0 :sfsim.astro/descriptors [{:sfsim.astro/doubles [3.0] :sfsim.astro/integers [4]}]}} index))
                     astro/read-source-names
                     (fn [index n buffer]
                       ({54 ["SOURCE1"]
                         56 ["SOURCE2"]} index))]
         (read-daf-summaries {:sfsim.astro/forward 53} 53 :buffer)
         => [{:sfsim.astro/source "SOURCE1" :sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}
             {:sfsim.astro/source "SOURCE2" :sfsim.astro/doubles [3.0] :sfsim.astro/integers [4]}]))


(facts "Create SPK segment from DAF descriptor"
       (let [segment (summary->spk-segment {:sfsim.astro/source "DE-0430LE-0430"
                                            :sfsim.astro/integers [1 0 1 2 6913 609716]
                                            :sfsim.astro/doubles [-4.734072E9 4.7353689E9]})]
         (:sfsim.astro/source segment) => "DE-0430LE-0430"
         (:sfsim.astro/start-second segment) => -4.734072E9
         (:sfsim.astro/end-second segment) => 4.7353689E9
         (:sfsim.astro/target segment) => 1
         (:sfsim.astro/center segment) => 0
         (:sfsim.astro/frame segment) => 1
         (:sfsim.astro/data-type segment) => 2
         (:sfsim.astro/start-i segment) => 6913
         (:sfsim.astro/end-i segment) => 609716))


(facts "Create PCK segment from DAF descriptor"
       (let [segment (summary->pck-segment {:sfsim.astro/source "de421.nio"
                                            :sfsim.astro/integers [31006 1 2 641 221284]
                                            :sfsim.astro/doubles [-3.1557168E9 1.609416E9]})]
         (:sfsim.astro/source segment) => "de421.nio"
         (:sfsim.astro/start-second segment) => -3.1557168E9
         (:sfsim.astro/end-second segment) => 1.609416E9
         (:sfsim.astro/target segment) => 31006
         (:sfsim.astro/frame segment) => 1
         (:sfsim.astro/data-type segment) => 2
         (:sfsim.astro/start-i segment) => 641
         (:sfsim.astro/end-i segment) => 221284))


(facts "Create lookup table for SPK segments (lookup by center and target)"
       (with-redefs [astro/read-daf-summaries
                     (fn [header ^long index buffer]
                       (facts header => {:sfsim.astro/forward 53}
                              index => 53
                              buffer => :buffer)
                       [{:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}])
                     astro/summary->spk-segment
                     (fn [descriptor]
                       (fact descriptor => {:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]})
                       {:sfsim.astro/source "SOURCE"
                        :sfsim.astro/center 3
                        :sfsim.astro/target 399
                        :sfsim.astro/start-i 1234})]
         (spk-segment-lookup-table {:sfsim.astro/forward 53} :buffer)
         => {[3 399] {:sfsim.astro/source "SOURCE" :sfsim.astro/center 3 :sfsim.astro/target 399 :sfsim.astro/start-i 1234}}))


(facts "Create lookup table for PCK segments (lookup by target)"
       (with-redefs [astro/read-daf-summaries
                     (fn [header ^long index buffer]
                       (facts header => {:sfsim.astro/forward 4}
                              index => 4
                              buffer => :buffer)
                       [{:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]}])
                     astro/summary->pck-segment
                     (fn [descriptor]
                       (fact descriptor => {:sfsim.astro/doubles [1.0] :sfsim.astro/integers [2]})
                       {:sfsim.astro/source "SOURCE"
                        :sfsim.astro/target 31006
                        :sfsim.astro/start-i 1234})]
         (pck-segment-lookup-table {:sfsim.astro/forward 4} :buffer)
         => {31006 {:sfsim.astro/source "SOURCE" :sfsim.astro/target 31006 :sfsim.astro/start-i 1234}}))


(facts "Read out coefficient layout from raw data"
       (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/coeff-layout.raw")
             layout (read-coefficient-layout {:sfsim.astro/end-i 4} buffer)]
         (:sfsim.astro/init layout) => -4.734072E9
         (:sfsim.astro/intlen layout) => 1382400.0
         (:sfsim.astro/rsize layout) => 41
         (:sfsim.astro/n layout) => 6850))


(facts "Read out coefficient layout at end of a segment"
       (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/coeff-layout-offset.raw")
             layout (read-coefficient-layout {:sfsim.astro/end-i 8} buffer)]
         (:sfsim.astro/init layout) => -4.734072E9
         (:sfsim.astro/intlen layout) => 1382400.0
         (:sfsim.astro/rsize layout) => 41
         (:sfsim.astro/n layout) => 6850))


(fact "Read coefficients for index zero from segment"
      (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/coefficients.raw")
            coeffs (read-interval-coefficients {:sfsim.astro/data-type 2 :sfsim.astro/start-i 1} {:sfsim.astro/rsize 41} 0 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))


(fact "Read coefficients for index one from segment"
      (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/coefficients-offset.raw")
            coeffs (read-interval-coefficients {:sfsim.astro/data-type 2 :sfsim.astro/start-i 1} {:sfsim.astro/rsize 41} 1 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))


(fact "Use start position of segment when reading coefficients"
      (let [buffer (map-file-to-buffer "test/clj/sfsim/fixtures/astro/coefficients-offset.raw")
            coeffs (read-interval-coefficients {:sfsim.astro/data-type 2 :sfsim.astro/start-i 42} {:sfsim.astro/rsize 41} 0 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))


(facts "Chebyshev polynomials"
       (chebyshev-polynomials             [1.0] -1.0 0.0) =>  1.0
       (chebyshev-polynomials             [1.0]  0.0 0.0) =>  1.0
       (chebyshev-polynomials             [1.0]  1.0 0.0) =>  1.0
       (chebyshev-polynomials         [1.0 0.0] -1.0 0.0) => -1.0
       (chebyshev-polynomials         [1.0 0.0]  0.0 0.0) =>  0.0
       (chebyshev-polynomials         [1.0 0.0]  1.0 0.0) =>  1.0
       (chebyshev-polynomials     [1.0 0.0 0.0] -1.0 0.0) =>  1.0
       (chebyshev-polynomials     [1.0 0.0 0.0]  0.0 0.0) => -1.0
       (chebyshev-polynomials     [1.0 0.0 0.0]  1.0 0.0) =>  1.0
       (chebyshev-polynomials [1.0 0.0 0.0 0.0] -1.0 0.0) => -1.0
       (chebyshev-polynomials [1.0 0.0 0.0 0.0] -0.5 0.0) =>  1.0
       (chebyshev-polynomials [1.0 0.0 0.0 0.0]  0.0 0.0) =>  0.0
       (chebyshev-polynomials [1.0 0.0 0.0 0.0]  0.5 0.0) => -1.0
       (chebyshev-polynomials [1.0 0.0 0.0 0.0]  1.0 0.0) =>  1.0
       (chebyshev-polynomials [(vec3 1 0 0) (vec3 0 0 0)] 0.0 (vec3 0 0 0)) => (vec3 0 0 0))


(facts "Compute interval index and position (s) inside for given timestamp"
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} T0) => [0 -1.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} (+ T0 1.0)) => [1 -1.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 86400.0 :sfsim.astro/intlen 86400.0} (+ T0 1.0)) => [0 -1.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 43200.0} (+ T0 1.0)) => [2 -1.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} (+ T0 0.5)) => [0 0.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} (+ T0 1.5)) => [1 0.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} (- T0 1.5)) => [0 -4.0]
       (interval-index-and-position {:sfsim.astro/n 3 :sfsim.astro/init 0.0 :sfsim.astro/intlen 86400.0} (+ T0 3.0)) => [2  1.0])


(facts "Convert list of vectors to list of vec3 objects"
       (map-vec3 []) => []
       (map-vec3 [[1.0 2.0 3.0]]) => [(vec3 1.0 2.0 3.0)])


(facts "Convert calendar date to Julian date"
       (julian-date #:sfsim.astro{:year 2024 :month 1 :day 1}) => 2460311
       (julian-date #:sfsim.astro{:year 2024 :month 1 :day 2}) => 2460312
       (julian-date #:sfsim.astro{:year 2024 :month 2 :day 1}) => 2460342
       (julian-date #:sfsim.astro{:year 2024 :month 3 :day 1}) => 2460371
       (julian-date #:sfsim.astro{:year 2025 :month 2 :day 1}) => 2460708
       (julian-date #:sfsim.astro{:year 2025 :month 3 :day 1}) => 2460736)


(facts "Convert Julian date to calendar date"
       (calendar-date 2460311) => #:sfsim.astro{:year 2024 :month 1 :day 1}
       (calendar-date 2460312) => #:sfsim.astro{:year 2024 :month 1 :day 2}
       (calendar-date 2460342) => #:sfsim.astro{:year 2024 :month 2 :day 1}
       (calendar-date 2460371) => #:sfsim.astro{:year 2024 :month 3 :day 1}
       (calendar-date 2460708) => #:sfsim.astro{:year 2025 :month 2 :day 1}
       (calendar-date 2460736) => #:sfsim.astro{:year 2025 :month 3 :day 1})


(facts "Convert day fraction to hours, minutes, and seconds"
       (clock-time 0.0) => #:sfsim.astro{:hour 0 :minute 0 :second 0}
       (clock-time 0.5) => #:sfsim.astro{:hour 12 :minute 0 :second 0}
       (clock-time (/ 25.0 48.0)) => #:sfsim.astro{:hour 12 :minute 30 :second 0}
       (clock-time (/ 3001.0 5760.0)) => #:sfsim.astro{:hour 12 :minute 30 :second 15})


(facts "Freeze some test values for the Earth precession angles"
       (psi-a 0.0)   => 0.0
       (psi-a 0.5)   => (roughly 2518.9709 1e-4)
       (psi-a 1.0)   => (roughly 5037.4015 1e-4)
       (omega-a 0.0) => (roughly 84381.406 1e-6)
       (omega-a 0.5) => (roughly 84381.404973 1e-6)
       (omega-a 0.1) => (roughly 84381.403929 1e-6)
       (chi-a 0.0)   => 0.0
       (chi-a 0.5)   => (roughly 4.682703 1e-6)
       (chi-a 1.0)   => (roughly 8.173932 1e-6))


(defn mock-psi-a
  ^double [^double t]
  (fact t => 1.0) (/ (* 0.125 PI) ASEC2RAD))


(defn mock-omega-a
  ^double [^double t]
  (fact t => 1.0) (/ (* 0.25 PI) ASEC2RAD))


(defn mock-chi-a
  ^double [^double t]
  (fact t => 1.0) (/ (* 0.5 PI) ASEC2RAD))


(fact "Test composition of precession matrix"
      (with-redefs [astro/psi-a   mock-psi-a
                    astro/omega-a mock-omega-a
                    astro/chi-a   mock-chi-a]
        (let [tdb        (+ T0 36525.0)
              r3-chi-a   (rotation-matrix-3d-z (* -0.5 PI))
              r1-omega-a (rotation-matrix-3d-x (* 0.25 PI))
              r3-psi-a   (rotation-matrix-3d-z (* 0.125 PI))
              r1-eps0    (rotation-matrix-3d-x (* (- eps0) ASEC2RAD))]
          (compute-precession tdb) => (mulm r3-chi-a (mulm r1-omega-a (mulm r3-psi-a r1-eps0))))))


(facts "Compute Earth rotation angle as a value between 0 and 1"
       (earth-rotation-angle (+ T0 0.0)) => (roughly 0.779057 1e-6)
       (earth-rotation-angle (+ T0 0.5)) => (roughly 0.280426 1e-6)
       (earth-rotation-angle (+ T0 1.0)) => (roughly 0.781795 1e-6)
       (earth-rotation-angle (+ T0 183.0)) => (roughly 0.280077 1e-6)
       (earth-rotation-angle (+ T0 36525.0)) => (roughly 0.777637 1e-6))


(facts "Get Earth rotation speed in radians per second"
       earth-rotation-speed => (roughly (* 2 PI (- (earth-rotation-angle (+ T0 (/ 1.0 86400))) (earth-rotation-angle T0))) 1e-9)
       (* earth-rotation-speed (-> 23 (* 60) (+ 56) (* 60) (+ 4.0905))) => (roughly (* 2 PI) 1e-6))


(facts "Compute Greenwich Mean Sidereal Time (GMST) in hours"
       (sidereal-time (+ T0 0.0))     => (roughly (+ 18 (/ 41 60) (/ 50.55 3600)) 2e-6)
       (sidereal-time (+ T0 0.5))     => (roughly (+  6 (/ 43 60) (/ 48.83 3600)) 2e-6)
       (sidereal-time (+ T0 1.0))     => (roughly (+ 18 (/ 45 60) (/ 47.10 3600)) 2e-6)
       (sidereal-time (+ T0 36525.0)) => (roughly (+ 18 (/ 44 60) (/ 55.44 3600)) 2e-6))


(fact "ICRS to J2000 transformation matrix"
      ICRS-to-J2000 => (roughly-matrix (mat3x3  +1.00000000e+00 -7.07827974e-08  8.05614894e-08
                                                +7.07827974e-08  1.00000000e+00  3.30604145e-08
                                                -8.05614894e-08 -3.30604145e-08  1.00000000e+00) 1e-8))


(fact "ICRS to current epoch (omitting nutation)"
      (icrs-to-now (+ T0 36525.0)) => (mulm (compute-precession (+ T0 36525.0)) ICRS-to-J2000))


(fact "Earth orientation in ICRS system (omitting nutation)"
      (let [ut 2456818.5742190727
            m  (earth-to-icrs ut)
            v  (vec3 6378137.0 0.0 0.0)]
        (mulv m v) => (roughly-vector (vec3 1.63777087e+06, -6.16427866e+06, -2.59868171e+03) 3e+2)))


(facts "Parse PCK files"
       (str (:expecting (first (.reason (pck-parser ""))))) => "KPL/(FK|PCK)\\r?\\n"
       (pck-parser "KPL/PCK\n") => [:START]
       (pck-parser "KPL/FK\n") => [:START]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/text.tf")) => [:START]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/integer.tf")) => [:START [:ASSIGNMENT "TEST_VAR_1" [:EQUALS] [:NUMBER "42"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/string.tf")) => [:START [:ASSIGNMENT "S" [:EQUALS] [:STRING "Testing"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/negative-int.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:NUMBER "-42"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/moretext.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:NUMBER "+42"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/double.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "3.14"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/exponent.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1E3"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/double-exp1.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1.2E-1"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/double-exp2.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1.2D-1"]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/vector.tf"))
       => [:START [:ASSIGNMENT "V" [:EQUALS] [:VECTOR [:DECIMAL "1.0"] [:DECIMAL "2.0"] [:DECIMAL "3.0"]]]]
       (pck-parser (slurp "test/clj/sfsim/fixtures/astro/increase.tf"))
       => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1.25"]] [:ASSIGNMENT "X" [:PLUSEQUALS] [:DECIMAL "2.5"]]]
       => [:START [:ASSIGNMENT "V" [:EQUALS] [:VECTOR [:DECIMAL "1.0"] [:DECIMAL "2.0"] [:DECIMAL "3.0"]]]]
       (count (insta/parses pck-parser (slurp "test/clj/sfsim/fixtures/astro/unambiguous.tf"))) => 1)


(facts "Convert PCK file to hashmap"
       (str (:expecting (first (.reason (read-frame-kernel "test/clj/sfsim/fixtures/astro/empty.tf"))))) => "KPL/(FK|PCK)\\r?\\n"
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/text.tf") => {}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/string.tf") => {"S" "Testing"}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/integer.tf") => {"TEST_VAR_1" 42}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/double.tf") => {"X" 3.14}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/double-exp2.tf") => {"X" 0.12}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/increase.tf") => {"X" 3.75}
       (read-frame-kernel "test/clj/sfsim/fixtures/astro/vector.tf") => {"V" [1.0 2.0 3.0]})


(facts "Extract information from PCK file"
       (let [pck  {"FRAME_MOON_ME_DE421" 31007
                   "FRAME_31007_CENTER" 301
                   "TKFRAME_31007_ANGLES" [67.92 78.56 0.3]
                   "TKFRAME_31007_AXES" [3 2 1]
                   "TKFRAME_31007_UNITS" "ARCSECONDS"}
             body (frame-kernel-body-frame-data pck "FRAME_MOON_ME_DE421")]
         (:sfsim.astro/center body) => 301
         (:sfsim.astro/angles body) => [67.92 78.56 0.3]
         (:sfsim.astro/axes body) => [3 2 1]
         (:sfsim.astro/units body) => "ARCSECONDS"))


(facts "Convert body frame information to matrix"
       (frame-kernel-body-frame {:sfsim.astro/angles [] :sfsim.astro/axes [] :sfsim.astro/units "DEGREES"})
       => (eye 3)
       (frame-kernel-body-frame {:sfsim.astro/angles [30.0] :sfsim.astro/axes [1] :sfsim.astro/units "DEGREES"})
       => (rotation-matrix-3d-x (/ PI 6.0))
       (frame-kernel-body-frame {:sfsim.astro/angles [30.0] :sfsim.astro/axes [2] :sfsim.astro/units "DEGREES"})
       => (rotation-matrix-3d-y (/ PI 6.0))
       (frame-kernel-body-frame {:sfsim.astro/angles [30.0] :sfsim.astro/axes [3] :sfsim.astro/units "DEGREES"})
       => (rotation-matrix-3d-z (/ PI 6.0))
       (frame-kernel-body-frame {:sfsim.astro/angles [30.0 45.0] :sfsim.astro/axes [3 2] :sfsim.astro/units "DEGREES"})
       => (mulm (rotation-matrix-3d-y (/ PI 4.0)) (rotation-matrix-3d-z (/ PI 6.0))))
