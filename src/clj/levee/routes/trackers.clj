(ns levee.routes.trackers
  (:require
    [cemerick.friend :as friend]
    [compojure.core :refer [defroutes context GET POST PUT DELETE]]
    [levee.routes.common :refer [handle-resource]]
    [levee.db :as db]
    [levee.models.trackers :as trackers]))

(defroutes routes
  (context "/trackers" []
    (GET "/" req (friend/authenticated (handle-resource req (db/get-trackers))))
    (POST "/" req (friend/authenticated (trackers/create-tracker req)))

    (context "/:id" [id]
      (GET "/" req (friend/authenticated (trackers/edit-tracker-page id req)))
      (PUT "/" req (friend/authenticated (trackers/edit-tracker id req)))
      (DELETE "/" req (friend/authenticated (trackers/remove-tracker id req))))))

