(ns levee.main
  (:require [org.httpkit.server :refer [run-server]]
            [levee.handler :refer [app]]
            [levee.rtorrent :as rtorrent]
            [levee.db :as db]
            [levee.config :as config])
  (:gen-class))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server [port]
  (reset! server (run-server app {:port port})))

(defn -main [& [port]]
  (clojure.pprint/pprint @config/data)

  (let [port (config/get [:port])]
    (start-server port)
    (println (str "You can view the site at http://localhost:" port))))

