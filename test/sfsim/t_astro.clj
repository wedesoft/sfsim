(ns sfsim.t-astro
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [sfsim.astro :refer :all])
    (:import [java.nio.charset StandardCharsets]))

(mi/collect! {:ns ['sfsim.astro]})
(mi/instrument! {:report (pretty/thrower)})

; Header test data cut from de430_1850-2150.bsp: dd if=de430_1850-2150.bsp of=pck-head.bsp bs=1024 count=52
(fact "Map file to a read-only byte buffer"
      (let [buffer (map-file-to-buffer "test/sfsim/fixtures/astro/test.txt")
            b      (byte-array 4)]
        (.get buffer b)
        (String. b StandardCharsets/US_ASCII) => "Test"))

(facts "Read header from SPK file"
       (let [header (read-spk-header (map-file-to-buffer "test/sfsim/fixtures/astro/pck-head.bsp"))]
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
             header  (read-spk-header buffer)
             cmt     (read-spk-comment header buffer)]
         (subs cmt 0 67) => "JPL planetary and lunar ephemeris DE430\n\nIntegrated 29 March 2013\n\n"
         (count cmt) => 50093))
