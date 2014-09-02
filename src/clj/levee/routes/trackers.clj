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
    (POST "/" req (friend/authenticated nil))

    (context "/:id" [id]
      (GET "/" [] (friend/authenticated nil))
      (PUT "/" [] (friend/authenticated nil))
      (DELETE "/" [] (friend/authenticated nil)))))

