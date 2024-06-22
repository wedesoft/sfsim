(ns sfsim.t-astro
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [gloss.core :refer (sizeof)]
              [sfsim.astro :refer :all])
    (:import [java.nio.charset StandardCharsets]))

(mi/collect! {:ns ['sfsim.astro]})
(mi/instrument! {:report (pretty/thrower)})

; Header test data cut from de430_1850-2150.bsp: dd if=de430_1850-2150.bsp of=pck-head.bsp bs=1024 count=80
(fact "Map file to a read-only byte buffer"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/test.txt")
            b      (byte-array 4)]
        (.get buffer b)
        (String. b StandardCharsets/US_ASCII) => "Test"))

(facts "Read header from DAF file"
       (let [header (read-daf-header (map-file-to-buffer "test/sfsim/fixtures/astro/pck-head.bsp"))]
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
       (let [buffer  (map-file-to-buffer "test/sfsim/fixtures/astro/pck-head.bsp")
             header  (read-daf-header buffer)
             cmt     (read-daf-comment header buffer)]
         (subs cmt 0 67) => "JPL planetary and lunar ephemeris DE430\n\nIntegrated 29 March 2013\n\n"
         (count cmt) => 50093))

(facts "Size of summary frame"
       (sizeof (daf-summary-frame 1 0)) => 8
       (sizeof (daf-summary-frame 2 0)) => 16
       (sizeof (daf-summary-frame 0 2)) => 8
       (sizeof (daf-summary-frame 0 4)) => 16
       (sizeof (daf-summary-frame 0 3)) => 16)

(facts "Read summaries from a record"
       (let [buffer    (map-file-to-buffer "test/sfsim/fixtures/astro/pck-head.bsp")
             header    (read-daf-header buffer)
             summaries (read-daf-summaries header (:forward header) buffer)]
         (:next-number summaries) => 0.0
         (:previous-number summaries) => 0.0
         (count (:descriptors summaries)) => 14
         (:doubles (first (:descriptors summaries))) => [-4.734072E9 4.735368E9]
         (:integers (first (:descriptors summaries))) => [1 0 1 2 6913 609716]))

(fact "Read source names from record"
      (let [buffer  (map-file-to-buffer "test/sfsim/fixtures/astro/pck-head.bsp")
            header  (read-daf-header buffer)
            sources (read-source-names (inc (:forward header)) 14 buffer)]
        sources => (repeat 14 "DE-0430LE-0430")))
