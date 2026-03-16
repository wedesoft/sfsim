;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-config
  (:require
    [clojure.java.io :as io]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.config :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Configuration file handling"
        (read-user-config "test/clj/sfsim/fixtures/config" "/" "nosuchfile.edn" {}) => {}
        (read-user-config "test/clj/sfsim/fixtures/config" "/" "test.edn" {}) => {:sfsim.config/value 42.0}
        (io/copy (io/file "test/clj/sfsim/fixtures/config/test.edn") (io/file (str tmpdir separator "sfsim.edn")))
        (read-user-config tmpdir separator "sfsim.edn" {}) => {:sfsim.config/value 42.0}
        (write-user-config tmpdir separator "sfsim.edn" {:sfsim.config/value 43.0})
        (read-user-config tmpdir separator "sfsim.edn" {}) => {:sfsim.config/value 43.0})
