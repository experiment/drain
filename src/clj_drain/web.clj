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

(def librato-conf
  (let [email (String. (env :librato-email)) token (String. (env :librato-token))]
    {:email email :token token}))

(def redis-conf
  (let [port (Integer. (env :redis-port)) host (String. (env :redis-host)) password (String. (env :redis-password))]
    {:spec {:host host :port port :password password}}))

(defmacro redis* [& body]
  `(car/wcar redis-conf ~@body))

(defn push-hit [hit]
  (let [hit-str (generate-string hit)]
    (redis*
      (car/publish "hits" hit-str)
      (car/lpush "hits" hit-str)
      (car/ltrim "hits" 0 1000))))

(defn push-gauge [gauge]
  (metrics/collate
    (librato-conf :email) (librato-conf :token) gauge []))

(defn extract-match [regex body]
  (nth (re-find regex body) 1))

(defn hit-hash [body]
  {
    :code (extract-match #"status=([0-9]+)" body)
    :host (extract-match #"host=(\S+)" body)
    :path (extract-match #"path=(\S+)" body)
    :connect (extract-match #"connect=(\d+)ms" body)
    :service (extract-match #"service=(\d+)ms" body)
  })

(defn connections-gauge [body]
  [
    {:name "postgres" :source "active-connections" :value (extract-match #"sample#active-connections=(\d+)" body)}
    {:name "postgres" :source "waiting-connections" :value (extract-match #"sample#waiting-connections=(\d+)" body)}
  ])

(defn drain [body]
  (if (re-find #"router" body)
    (push-hit (hit-hash body)))
  (if (re-find #"heroku-postgres" body)
    (push-gauge (connections-gauge body))))

(defroutes all-routes
  (POST "/drain" {body :body}
    (drain (slurp body))
    {:status 200})
  (route/not-found "Page not found"))

(defn -main [& [port]]
  (let [port (Integer. (env :port))] port
    (run-server (site #'all-routes) {:port port})))
