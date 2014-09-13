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

(defn start-server [port]
  (reset! server (run-server app {:port port})))

(defn generate-secret-key []
  (subs (random/url-part 16) 0 16))

(defn -main []
  (db/connect)

  (let [port (Integer/parseInt (conf :port))]
    (if (clojure.string/blank? (conf :secret))
      (println (str "secret key: " (generate-secret-key)))
      (do
        (.start (Thread. jobs/prune))
        (.start (Thread. jobs/stale))

        (start-server port)
        (println (str "listening on: " port))))))

