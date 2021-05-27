(defproject sfsim25 "0.1.0"
  :description "Spaceflight Simulator Game"
  :url "https://github.com/wedesoft/sfsim25"
  :license {:name "GPL-3.0-or-later"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure       "1.10.3"]
                 [org.clojure/core.memoize "1.0.236"]
                 [org.clojure/core.async   "1.3.618"]
                 [com.climate/claypoole      "1.1.4"]
                 [net.mikera/core.matrix    "0.62.0"]
                 [net.mikera/vectorz-clj    "0.48.0"]]
  :main ^:skip-aot sfsim25.core
  :profiles {:run-scale-image     {:main sfsim25.scale-image}
             :run-map-tiles       {:main sfsim25.map-tiles}
             :run-scale-elevation {:main sfsim25.scale-elevation}
             :run-elevation-tiles {:main sfsim25.elevation-tiles}
             :run-globe           {:main sfsim25.globe}
             :uberjar             {:aot :all}
             :dev                 {:dependencies [[midje "1.9.10"]]}}
  :aliases {"run-scale-image"     ["with-profile" "run-scale-image"     "run"]
            "run-map-tiles"       ["with-profile" "run-map-tiles"       "run"]
            "run-scale-elevation" ["with-profile" "run-scale-elevation" "run"]
            "run-elevation-tiles" ["with-profile" "run-elevation-tiles" "run"]
            "run-globe"           ["with-profile" "run-globe"           "run"]}
  :resource-paths ["/usr/share/java/lwjgl.jar"
                   "/usr/share/java/jmagick.jar"]
  :target-path "target/%s"
  :jvm-opts ["-Xmx2g"])
