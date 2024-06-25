(ns sfsim.t-astro
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [fastmath.vector :refer (vec3)]
              [gloss.core :refer (sizeof)]
              [sfsim.astro :refer :all :as astro])
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
         (:locidw header) => "DAF/SPK "
         (:num-doubles header) => 2
         (:num-integers header) => 6
         (:forward header) => 53
         (:backward header) => 53
         (:free header) => 4089585
         (:locfmt header) => "LTL-IEEE"
         (:ftpstr header) => (map int "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")))

(facts "Check endianness"
       (check-endianness {:locfmt "LTL-IEEE"}) => true
       (check-endianness {:locfmt "BIG-IEEE"}) => false)

(facts "Check FTP string"
       (check-ftp-str {:ftpstr (map int "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")}) => true
       (check-ftp-str {:ftpstr (map int "unexpected text")}) => false)

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
             summaries (read-daf-descriptor header (:forward header) buffer)]
         (:next-number summaries) => 0.0
         (:previous-number summaries) => 0.0
         (count (:descriptors summaries)) => 14
         (:doubles (first (:descriptors summaries))) => [-4.734072E9 4.735368E9]
         (:integers (first (:descriptors summaries))) => [1 0 1 2 6913 609716]))

(fact "Read source names from record"
      (let [buffer  (map-file-to-buffer "test/sfsim/fixtures/astro/spk-head.bsp")
            header  (read-daf-header buffer)
            sources (read-source-names (inc (:forward header)) 14 buffer)]
        sources => (repeat 14 "DE-0430LE-0430")))

(facts "Read multiple summary records"
       (with-redefs [astro/read-daf-descriptor
                     (fn [header index buffer]
                         (fact index => 53)
                         {:next-number 0.0 :descriptors [{:doubles [1.0] :integers [2]}]})
                     astro/read-source-names
                     (fn [index n buffer]
                         (fact index => 54)
                         ["DE-0430LE-0430"])]
         (read-daf-summaries {:forward 53} 53 :buffer) => [{:source "DE-0430LE-0430" :doubles [1.0] :integers [2]}])
       (with-redefs [astro/read-daf-descriptor
                     (fn [header index buffer]
                         ({53 {:next-number 55.0 :descriptors [{:doubles [1.0] :integers [2]}]}
                           55 {:next-number  0.0 :descriptors [{:doubles [3.0] :integers [4]}]}} index))
                     astro/read-source-names
                     (fn [index n buffer]
                         ({54 ["SOURCE1"]
                           56 ["SOURCE2"]} index))]
         (read-daf-summaries {:forward 53} 53 :buffer) => [{:source "SOURCE1" :doubles [1.0] :integers [2]}
                                                           {:source "SOURCE2" :doubles [3.0] :integers [4]}]))

(facts "Create SPK segment from DAF descriptor"
       (let [segment (summary->spk-segment {:source "DE-0430LE-0430"
                                            :integers [1 0 1 2 6913 609716]
                                            :doubles [-4.734072E9 4.7353689E9]})]
         (:source segment) => "DE-0430LE-0430"
         (:start-second segment) => -4.734072E9
         (:end-second segment) => 4.7353689E9
         (:target segment) => 1
         (:center segment) => 0
         (:frame segment) => 1
         (:data-type segment) => 2
         (:start-i segment) => 6913
         (:end-i segment) => 609716))

(facts "Create lookup table for SPK segments"
       (with-redefs [astro/read-daf-summaries
                     (fn [header index buffer]
                         (facts header => {:forward 53}
                                index => 53
                                buffer => :buffer)
                         [{:doubles [1.0] :integers [2]}])
                     astro/summary->spk-segment
                     (fn [descriptor]
                         (fact descriptor => {:doubles [1.0] :integers [2]})
                         {:source "SOURCE"
                          :center 3
                          :target 399
                          :start-i 1234})]
         (spk-segment-lookup-table {:forward 53} :buffer) => {[3 399] {:source "SOURCE" :center 3 :target 399 :start-i 1234}}))

(facts "Read out coefficient layout from raw data"
       (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coeff-layout.raw")
             layout (read-coefficient-layout {:end-i 4} buffer)]
         (:init layout) => -4.734072E9
         (:intlen layout) => 1382400.0
         (:rsize layout) => 41
         (:n layout) => 6850))

(facts "Read out coefficient layout at end of a segment"
       (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coeff-layout-offset.raw")
             layout (read-coefficient-layout {:end-i 8} buffer)]
         (:init layout) => -4.734072E9
         (:intlen layout) => 1382400.0
         (:rsize layout) => 41
         (:n layout) => 6850))

(fact "Read coefficients for index zero from segment"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients.raw")
            coeffs (read-interval-coefficients {:data-type 2 :start-i 1} {:rsize 41} 0 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))

(fact "Read coefficients for index one from segment"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients-offset.raw")
            coeffs (read-interval-coefficients {:data-type 2 :start-i 1} {:rsize 41} 1 buffer)]
        coeffs => (reverse (apply map vector (partition 13 (map double (range 39)))))))

(fact "Use start position of segment when reading coefficients"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/coefficients-offset.raw")
            coeffs (read-interval-coefficients {:data-type 2 :start-i 42} {:rsize 41} 0 buffer)]
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
