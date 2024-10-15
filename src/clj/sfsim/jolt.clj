(ns sfsim.jolt
    "Interface with native Jolt physics library"
    (:require [coffi.ffi :refer (defcfn) :as ffi]
              [coffi.mem :as mem]))

(ffi/load-library "src/c/sfsim/libjolt.so")

(defcfn jolt-init
  "Initialize Jolt library"
  jolt_init [] ::mem/void)

(defcfn jolt-destroy
  "Destruct Jolt library setup"
  jolt_destroy [] ::mem/void)
