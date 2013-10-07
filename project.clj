(defproject clj_drain "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://clj_drain.herokuapp.com"
  :license {:name "FIXME: choose"
            :url "http://example.com/FIXME"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.2.3"]
                 [compojure "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [ring/ring-devel "1.1.0"]
                 [environ "0.2.1"]
                 [com.taoensso/timbre "2.6.2"]
                 [http-kit "2.1.10"]
                 [com.taoensso/carmine "2.2.3"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]]
  :hooks [environ.leiningen.hooks]
  :profiles {:production {:env {:production true}}})
