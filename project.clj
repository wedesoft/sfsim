(defproject sfsim25 "0.1.0"
  :description "Spaceflight Simulator Game"
  :url "https://github.com/wedesoft/sfsim25"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure       "1.11.1"]
                 [org.clojure/core.memoize "1.0.257"]
                 [org.clojure/core.async   "1.6.673"]
                 [org.clj-commons/claypoole  "1.2.2"]
                 [progrock                   "0.1.2"]
                 [net.mikera/core.matrix    "0.63.0"]
                 [net.mikera/vectorz-clj    "0.48.0"]
                 [comb                       "0.1.1"]
                 [gnuplot                    "0.1.3"]]
  :main ^:skip-aot sfsim25.core
  :profiles {:uberjar             {:aot :all}
             :dev                 {:dependencies [[midje "1.9.10"]]}}
  :resource-paths ["/usr/share/java/lwjgl.jar"
                   "/usr/share/java/ij.jar"]
  :plugins [[lein-midje "3.2.1"]
            [lein-codox "0.10.7"]]
  :target-path "target/%s"
  :jvm-opts ["-Xmx2g" "--add-opens" "java.base/java.lang=ALL-UNNAMED"])
