(ns levee.routes.invitations
  (:require
    [cemerick.friend :as friend]
    [compojure.core :refer [defroutes context GET POST PUT DELETE]]
    [levee.routes.common :refer [handle-resource]]))

(defroutes routes
  (context "/invitations" []
    (GET "/" [] (friend/authenticated nil))
    (POST "/" req (friend/authenticated nil))

    (context "/:id" [id]
      (GET "/" [] (friend/authenticated nil))
      (DELETE "/" [] (friend/authenticated nil)))))

