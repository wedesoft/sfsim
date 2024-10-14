(ns sfsim.jolt
    "Interface with native Jolt physics library"
    (:require [coffi.ffi :refer (defcfn) :as ffi]
              [coffi.mem :as mem]))

(ffi/load-library "src/c/sfsim/libjolt.so")

(defcfn register-default-allocator
  "Register default allocator"
  RegisterDefaultAllocator [] ::mem/void)
