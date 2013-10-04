(ns clj_drain.web
  (:use [environ.core :refer [env]]
        [taoensso.timbre :as timbre :refer (trace debug info warn error fatal spy with-log-level)]
        [org.httpkit.server]
        [compojure.core :only [defroutes GET POST DELETE ANY context]]
        [compojure.route :only [not-found] :as route]
        [compojure.handler :only [site]]
        [taoensso.carmine :as car :refer (wcar)]))

(defn drain [body]
  (let [body (slurp body)]
    (let [status-code (re-find #"status=([0-9]+)" body)]
      (if-not (nil? status-code) (info status-code)))))

(defroutes all-routes
  (POST "/drain" {body :body}
    (drain body)
    {:status 200})
  (route/not-found "Page not found"))

(defn -main [& [port]]
  (let [port (Integer. (env :port))] port
    (run-server (site #'all-routes) {:port port})))
