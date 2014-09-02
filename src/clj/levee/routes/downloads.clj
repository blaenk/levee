(ns levee.routes.downloads
  (:require
    [levee.models.downloads :as downloads]
    [cemerick.friend :as friend]
    [org.httpkit.server :refer :all]
    [ring.util.response :as response]
    [compojure.core :refer [defroutes context GET POST PUT]]
    [me.raynes.fs :as fs]
    [levee.routes.common :refer [handle-resource]]
    [levee.rtorrent :as rtorrent]))

;; open?, close, websocket?, send!, on-receive, on-close
(defn- downloads-ws-feed [req]
  (with-channel req chan
    (on-close chan
      (fn [status] (println "channel closed: " status)))
    (on-receive chan
      (fn [data] (send! chan data)))

    (loop []
      (Thread/sleep 5000)
      (println "sleeping ds")
      (when (open? chan)
        (println "sending downloads")
        (send! chan (cheshire.core/generate-string (downloads/get-downloads)))
        (recur)))))

(defn- download-ws-feed [hash req]
  (with-channel req chan
    (on-close chan
      (fn [status] (println "channel closed: " status)))
    (on-receive chan
      (fn [data] (send! chan data)))

    (loop [fast? false]
      (Thread/sleep (if fast? 1000 5000))
      (println "sleeping d")
      (when (open? chan)
        (println "sending download")
        (let [d (cheshire.core/generate-string (downloads/get-download hash))
              downloading? (or (= (:state d) "downloading")
                               (= (:state d) "hashing"))]
          (send! chan d)
          (recur downloading?))))))

(defn sendfile [file]
  (let [base-name (fs/base-name file)
        sendfile-path (.getPath (fs/file "/sendfile" file))]
    {:status 200
     :headers
       {"Content-Disposition" (str "filename=\"" base-name "\"")
        "Content-Type" ""
        "X-Accel-Redirect" sendfile-path}
     :body ""}))

(defroutes routes
  (GET "/file/*" req (friend/authenticated (sendfile (get-in req [:params :*]))))

  (context "/downloads" []
    (GET "/" req (friend/authenticated (handle-resource req (downloads/get-downloads))))
    (POST "/" req (friend/authenticated (downloads/load-torrent req)))
    (POST "/magnet" req (friend/authenticated (downloads/load-magnet req)))

    (GET "/ws" req (friend/authenticated (downloads-ws-feed req)))

    (context "/:hash" [hash]
      (GET "/" req (friend/authenticated (handle-resource req (downloads/get-download hash))))
      (GET "/files" req (friend/authenticated (response/response (downloads/get-files hash))))
      (POST "/files" req (friend/authenticated (downloads/commit-file-priorities req)))

      (GET "/ws" req (friend/authenticated (download-ws-feed hash req)))

      (POST "/lock-toggle" req (friend/authenticated (downloads/lock-toggle hash req)))
      (POST "/start" req (friend/authenticated (downloads/start hash)))
      (POST "/stop" req (friend/authenticated (downloads/stop hash)))
      (POST "/erase" req (friend/authenticated (downloads/erase hash req))))))

