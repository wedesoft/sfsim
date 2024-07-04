(ns sfsim.t-astro
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [clojure.math :refer (PI)]
              [sfsim.conftest :refer (roughly-matrix roughly-vector)]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3 mulm mulv)]
              [gloss.core :refer (sizeof)]
              [sfsim.astro :refer :all :as astro]
              [sfsim.matrix :refer :all])
    (:import [java.nio.charset StandardCharsets]))

(mi/collect! {:ns ['sfsim.astro]})
(mi/instrument! {:report (pretty/thrower)})

; Header test data cut from de430_1850-2150.bsp: dd if=de430_1850-2150.bsp of=spk-head.bsp bs=1024 count=80
(fact "Map file to a read-only byte buffer"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/test.txt")
            b      (byte-array 4)]
        (.get buffer b)
        (String. b StandardCharsets/US_ASCII) => "Test"))

(facts "Read header from DAF file"
       (let [header (read-daf-header (map-file-to-buffer "test/sfsim/fixtures/astro/spk-head.bsp"))]
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
       (let [buffer  (map-file-to-buffer "test/sfsim/fixtures/astro/spk-head.bsp")
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
       (let [buffer    (map-file-to-buffer "test/sfsim/fixtures/astro/spk-head.bsp")
             header    (read-daf-header buffer)
             summaries (read-daf-descriptor header (:sfsim.astro/forward header) buffer)]
         (:sfsim.astro/next-number summaries) => 0.0
         (:sfsim.astro/previous-number summaries) => 0.0
         (count (:sfsim.astro/descriptors summaries)) => 14
         (:sfsim.astro/doubles (first (:sfsim.astro/descriptors summaries))) => [-4.734072E9 4.735368E9]
         (:sfsim.astro/integers (first (:sfsim.astro/descriptors summaries))) => [1 0 1 2 6913 609716]))

(fact "Read source names from record"
      (let [buffer  (map-file-to-buffer "test/sfsim/fixtures/astro/spk-head.bsp")
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

(facts "Create lookup table for SPK segments"
       (with-redefs [astro/read-daf-summaries
                     (fn [header index buffer]
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

(facts "Read out coefficient layout from raw data"
       (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coeff-layout.raw")
             layout (read-coefficient-layout {:sfsim.astro/end-i 4} buffer)]
         (:sfsim.astro/init layout) => -4.734072E9
         (:sfsim.astro/intlen layout) => 1382400.0
         (:sfsim.astro/rsize layout) => 41
         (:sfsim.astro/n layout) => 6850))

(facts "Read out coefficient layout at end of a segment"
       (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coeff-layout-offset.raw")
             layout (read-coefficient-layout {:sfsim.astro/end-i 8} buffer)]
         (:sfsim.astro/init layout) => -4.734072E9
         (:sfsim.astro/intlen layout) => 1382400.0
         (:sfsim.astro/rsize layout) => 41
         (:sfsim.astro/n layout) => 6850))

(fact "Read coefficients for index zero from segment"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients.raw")
            coeffs (read-interval-coefficients {:sfsim.astro/data-type 2 :sfsim.astro/start-i 1} {:sfsim.astro/rsize 41} 0 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))

(fact "Read coefficients for index one from segment"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients-offset.raw")
            coeffs (read-interval-coefficients {:sfsim.astro/data-type 2 :sfsim.astro/start-i 1} {:sfsim.astro/rsize 41} 1 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))

(fact "Use start position of segment when reading coefficients"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients-offset.raw")
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
       (julian-date {:year 2024 :month 1 :day 1}) => 2460311
       (julian-date {:year 2024 :month 1 :day 2}) => 2460312
       (julian-date {:year 2024 :month 2 :day 1}) => 2460342
       (julian-date {:year 2024 :month 3 :day 1}) => 2460371
       (julian-date {:year 2025 :month 2 :day 1}) => 2460708
       (julian-date {:year 2025 :month 3 :day 1}) => 2460736)

(facts "Convert Julian date to calendar date"
       (calendar-date 2460311) => {:year 2024 :month 1 :day 1}
       (calendar-date 2460312) => {:year 2024 :month 1 :day 2}
       (calendar-date 2460342) => {:year 2024 :month 2 :day 1}
       (calendar-date 2460371) => {:year 2024 :month 3 :day 1}
       (calendar-date 2460708) => {:year 2025 :month 2 :day 1}
       (calendar-date 2460736) => {:year 2025 :month 3 :day 1})

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

(fact "Test composition of precession matrix"
      (with-redefs [astro/psi-a   (fn [t] (fact t => 1.0) (/ (* 0.125 PI) ASEC2RAD))
                    astro/omega-a (fn [t] (fact t => 1.0) (/ (* 0.25  PI) ASEC2RAD))
                    astro/chi-a   (fn [t] (fact t => 1.0) (/ (* 0.5   PI) ASEC2RAD))]
        (let [tdb        (+ T0 36525.0)
              r3-chi-a   (rotation-z (* -0.5 PI))
              r1-omega-a (rotation-x (* 0.25 PI))
              r3-psi-a   (rotation-z (* 0.125 PI))
              r1-eps0    (rotation-x (* (- eps0) ASEC2RAD))]
          (compute-precession tdb) => (mulm r3-chi-a (mulm r1-omega-a (mulm r3-psi-a r1-eps0))))))

(fact "Compute Earth rotation angle as a value between 0 and 1"
      (earth-rotation-angle (+ T0 0.0)) => (roughly 0.779057 1e-6)
      (earth-rotation-angle (+ T0 0.5)) => (roughly 0.280426 1e-6)
      (earth-rotation-angle (+ T0 1.0)) => (roughly 0.781795 1e-6)
      (earth-rotation-angle (+ T0 183.0)) => (roughly 0.280077 1e-6)
      (earth-rotation-angle (+ T0 36525.0)) => (roughly 0.777637 1e-6))

(fact "Compute Greenwich Mean Sidereal Time (GMST) in hours"
      (sidereal-time (+ T0 0.0))     => (roughly (+ 18 (/ 41 60) (/ 50.55 3600)) 2e-6)
      (sidereal-time (+ T0 0.5))     => (roughly (+  6 (/ 43 60) (/ 48.83 3600)) 2e-6)
      (sidereal-time (+ T0 1.0))     => (roughly (+ 18 (/ 45 60) (/ 47.10 3600)) 2e-6)
      (sidereal-time (+ T0 36525.0)) => (roughly (+ 18 (/ 44 60) (/ 55.44 3600)) 2e-6))

(fact "ICRS to J2000 transformation matrix"
      ICRS-to-J2000 => (roughly-matrix (mat3x3  1.00000000e+00 -7.07827974e-08  8.05614894e-08
                                                7.07827974e-08  1.00000000e+00  3.30604145e-08
                                               -8.05614894e-08 -3.30604145e-08  1.00000000e+00) 1e-8))

(fact "ICRS to current epoch (omitting nutation)"
      (icrs-to-now (+ T0 36525.0)) => (mulm (compute-precession (+ T0 36525.0)) ICRS-to-J2000))

(fact "Earth orientation in ICRS system (omitting nutation)"
      (let [ut 2456818.5742190727
            m  (earth-to-icrs ut)
            v  (vec3 6378137.0 0.0 0.0)]
        (mulv m v) => (roughly-vector (vec3 1.63777087e+06, -6.16427866e+06, -2.59868171e+03) 3e+2)))

(facts "Parse PCK files"
       (str (:expecting (first (.reason (pck-parser ""))))) => "KPL/(FK|PCK)\\n"
       (pck-parser "KPL/PCK\n") => [:START]
       (pck-parser "KPL/FK\n") => [:START]
       (pck-parser (slurp "test/sfsim/fixtures/astro/text.tf")) => [:START]
       (pck-parser (slurp "test/sfsim/fixtures/astro/integer.tf")) => [:START [:ASSIGNMENT "TEST_VAR_1" [:EQUALS] [:NUMBER "42"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/negative-int.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:NUMBER "-42"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/moretext.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:NUMBER "+42"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/double.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "3.14"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/exponent.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1E3"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/double-exp1.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1.25E-1"]]]
       (pck-parser (slurp "test/sfsim/fixtures/astro/double-exp2.tf")) => [:START [:ASSIGNMENT "X" [:EQUALS] [:DECIMAL "1.25D-1"]]]
       )
