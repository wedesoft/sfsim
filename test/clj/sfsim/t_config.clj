;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-config
  (:require
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.config :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Configuration file handling"
        (read-user-config "test/clj/sfsim/fixtures/config" "/" "nosuchfile.edn" {}) => {}
        (read-user-config "test/clj/sfsim/fixtures/config" "/" "test.edn" {}) => {:sfsim.config/value 42.0})
