(defproject teslogger-server "0.1.0-SNAPSHOT"
  :description "Collect screenshots"
  :url "https://github.com/kawasima/teslogger-server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [compojure "1.1.9"]
                 [hiccup "1.0.5"]
                 [liberator "0.12.2"]
                 [net.unit8/ulon-colon "0.2.0-SNAPSHOT"]
                 [net.unit8/axebomber-clj "0.1.0-SNAPSHOT"]

                 [org.clojure/clojurescript "0.0-2356"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [om "0.7.3"]
                 [sablono "0.2.22"]
                 [prismatic/om-tools "0.3.3"]]
  :source-paths ["src/clj"]
  :pom-plugins [[net.unit8.maven.plugins/assets-maven-plugin "0.2.0"
                  {:configuration [:auto "true"]}]]
  :plugins [[lein-ring "0.8.11"]
            [lein-cljsbuild "1.0.3"]]
  :ring {:handler teslogger.server.core/app}

  :cljsbuild {:builds
               [{:id "dev"
                 :source-paths ["src/cljs"]
                 :compiler {:output-to "resources/public/javascript/main.js"
                            :optimizations :simple}}]})
