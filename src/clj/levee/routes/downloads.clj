(ns levee.routes.downloads
  (:require
    [levee.models.downloads :as downloads]
    [cemerick.friend :as friend]
    [ring.util.response :as response]
    [compojure.core :refer [defroutes context GET POST PUT]]
    [me.raynes.fs :as fs]
    [levee.routes.common :refer [handle-resource]]
    [levee.rtorrent :as rtorrent]))

(defroutes routes
  (GET "/file/*" req (friend/authenticated (downloads/serve-file (get-in req [:params :*]))))

  (context "/downloads" []
    (GET "/" req (friend/authenticated (handle-resource req (downloads/get-downloads))))
    (POST "/" req (friend/authenticated (downloads/load-torrent req)))
    (POST "/magnet" req (friend/authenticated (downloads/load-magnet req)))

    (GET "/feed" req (friend/authenticated (downloads/downloads-feed req)))

    (context "/:hash" [hash]
      (GET "/" req (friend/authenticated (handle-resource req (downloads/get-download hash))))

      (GET "/files" req (friend/authenticated (response/response (downloads/get-files hash))))
      (POST "/files" req (friend/authenticated (downloads/commit-file-priorities req)))

      (GET "/extracted" req (friend/authenticated (response/response (downloads/get-extracted hash))))

      (GET "/feed" req (friend/authenticated (downloads/download-feed hash req)))

      (POST "/lock-toggle" req (friend/authenticated (downloads/lock-toggle hash req)))
      (POST "/start" req (friend/authenticated (downloads/start hash)))
      (POST "/stop" req (friend/authenticated (downloads/stop hash)))
      (POST "/erase" req (friend/authenticated (downloads/erase hash req))))))

