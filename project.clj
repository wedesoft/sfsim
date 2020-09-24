(defproject sfsim25 "0.1.0-SNAPSHOT"
  :description "Spaceflight Simulator Game"
  :url "https://github.com/wedesoft/sfsim25"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot sfsim25.core
  :resource-paths ["resources/jogamp-fat.jar"
                   "/usr/share/java/gluegen2-rt.jar"]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
