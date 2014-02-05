(ns clj_drain.web
  (:use [environ.core :refer [env]]
        [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy with-log-level)]
        [org.httpkit.server]
        [compojure.core :only [defroutes GET POST DELETE ANY context]]
        [compojure.route :only [not-found] :as route]
        [compojure.handler :only [site]]
        [cheshire.core :refer :all]
        [taoensso.carmine :as car :refer (wcar)]
        [clj-librato.metrics :as metrics]
        [clj-time.coerce :as cljc]
        [org.httpkit.timer :as timer]))

(defn average [set]
  (let [numbers (map read-string set)]
    (/ (apply + numbers) (count numbers))))

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
      (car/ltrim "hits" 0 1000)
      (car/sadd "connects" (hit :connect)))))

(defn push-gauge [gauge]
  (metrics/collate
    (librato-conf :email) (librato-conf :token) gauge []))

(defn push-annotation [annotation-name annotation]
  (metrics/create-annotation
    (librato-conf :email) (librato-conf :token) annotation-name annotation))

(defn extract-match [regex body]
  (nth (re-find regex body) 1))

(defn string-to-seconds-from-epoc [string]
  (quot (cljc/to-long (cljc/from-string string)) 1000))

(defn hit-hash [body]
  {
    :code (extract-match #"status=([0-9]+)" body)
    :host (extract-match #"host=(\S+)" body)
    :path (extract-match #"path=(\S+)" body)
    :connect (extract-match #"connect=(\d+)ms" body)
    :service (extract-match #"service=(\d+)ms" body)
    :ip (extract-match #"fwd=.((\d+\.?){4})" body)
  })

(defn postgres-connections-gauge [body]
  [
    {:name "postgres" :source "active-connections" :value (extract-match #"sample#active-connections=(\d+)" body)}
    {:name "postgres" :source "waiting-connections" :value (extract-match #"sample#waiting-connections=(\d+)" body)}
  ])

(defn dyno-gauge [body]
  [{
    :name "dyno.memory_total"
    :source (extract-match #"source=(\S+)" body)
    :value (extract-match #"sample#memory_total=([\d|\.]+)MB" body)
  }])

(defn dyno-connections-gauge [body]
  [{
    :name "dyno.connections"
    :source (extract-match #"host app (\D+\.\d+)" body)
    :value (extract-match #"connections=(\d+)" body)
  }])

(defn deploy-annotation [body]
  (info body)
  {
    :title (extract-match #"Deploy\s(.+)" body)
    :start_time (string-to-seconds-from-epoc (extract-match #"^(\S+)" body))
  })

(defn drain [body]
  (if (re-find #"router" body)
    (push-hit (hit-hash body)))
  (if (re-find #"heroku-postgres" body)
    (push-gauge (postgres-connections-gauge body)))
  (if (re-find #"sample#memory_total" body)
    (push-gauge (dyno-gauge body)))
  (if (re-find #"sql\.active_record" body)
    (push-gauge (dyno-connections-gauge body)))
  (if (re-find #"heroku api" body)
    (info (deploy-annotation body))))
    ; (push-annotation "deploy" (deploy-annotation body))))

(defn push-average-connection []
  (let [connects (redis* (car/smembers "connects"))]
    (redis* (car/del "connects"))
    (push-gauge [{:name "connect" :value (average connects)}])))

(defn tick [interval]
  (timer/schedule-task interval
    (tick interval)
    (push-average-connection)))

(defroutes all-routes
  (POST "/drain" {body :body}
    (drain (slurp body))
    {:status 200})
  (route/not-found "Page not found"))

(defn -main [& [port]]
  (let [port (Integer. (env :port))] port
    (run-server (site #'all-routes) {:port port}))
  (tick 30000))
