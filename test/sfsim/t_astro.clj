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
