(ns clj_drain.web
  (:use [environ.core :refer [env]]
        [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy with-log-level)]
        [org.httpkit.server]
        [compojure.core :only [defroutes GET POST DELETE ANY context]]
        [compojure.route :only [not-found] :as route]
        [compojure.handler :only [site]]
        [cheshire.core :refer :all]
        [taoensso.carmine :as car :refer (wcar)]
        [clj-librato.metrics :as metrics]))

(def redis-conf
  (let [port (Integer. (env :redis-port)) host (String. (env :redis-host)) password (String. (env :redis-password))]
    {:spec {:host host :port port :password password}}))

(defmacro redis* [& body]
  (info redis-conf)
  `(car/wcar redis-conf ~@body))

(defn push-hit [hit]
  (info hit)
  (redis* (car/publish "codes" (generate-string hit))))

(defn hit-hash [body]
  {
    :code (nth (re-find #"status=([0-9]+)" body) 1)
    :path (nth (re-find #"path=(\S+)" body) 1)
  })

(defn drain [body]
  (if-not (nil? (re-find #"router" body))
    (push-hit (hit-hash body))))

(defroutes all-routes
  (POST "/drain" {body :body}
    (drain (slurp body))
    {:status 200})
  (route/not-found "Page not found"))

(defn -main [& [port]]
  (let [port (Integer. (env :port))] port
    (run-server (site #'all-routes) {:port port})))
