(defproject sfsim25 "0.1.0-SNAPSHOT"
  :description "Spaceflight Simulator Game"
  :url "https://github.com/wedesoft/sfsim25"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]]
  :profiles {:run-sfsim25 {:main sfsim25.core}
             :uberjar {:aot :all}}
  :aliases {"run-sfsim25" ["with-profile" "run-sfsim25" "run"]}
  :resource-paths ["resources/jogamp-fat.jar"
                   "/usr/share/java/gluegen2-rt.jar"
                   "/usr/share/java/jmagick.jar"]
  :target-path "target/%s")
