(ns build
    (:require [clojure.tools.build.api :as b]))

(defn clean [_]
  (b/delete {:path "data"}))
