(ns levee.main
  (:require
    [org.httpkit.server :refer [run-server]]
    [levee.handler :refer [app]]
    [levee.rtorrent :as rtorrent]
    [levee.db :as db]
    [levee.jobs :as jobs]
    [environ.core :refer [env]]
    [crypto.random :as random]
    [levee.common :refer [conf]])
  (:gen-class))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server [opts]
  (reset! server (run-server app opts)))

(defn generate-secret-key []
  (subs (random/url-part 16) 0 16))

(defn -main []
  (db/connect)

  (if (clojure.string/blank? (conf :secret))
    (println (str "secret key: " (generate-secret-key)))
    (let [port (Integer/parseInt (conf :port))
          host (conf :host)]
      (.start (Thread. jobs/prune))
      (.start (Thread. jobs/stale))

      (start-server {:ip host :port port})
      (println (str "listening on: " port)))))

