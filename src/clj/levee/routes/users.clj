(ns levee.routes.users
  (:require
    [cemerick.friend :as friend]
    [compojure.core :refer [defroutes context GET POST PUT DELETE]]
    [ring.util.response :as response]
    [levee.views.layout :as layout]
    [levee.db :as db]
    [levee.routes.common :refer [handle-resource]]
    [levee.models.users :as users]))

(defroutes routes
  (GET "/login" req (layout/login))
  (GET "/logout" req (friend/logout* (response/redirect "/login")))

  (context "/invitations" []
    (GET "/" req (friend/authorize #{:levee.auth/admin} (db/get-invitations)))
    (POST "/" req (friend/authorize #{:levee.auth/admin} (users/create-invitation)))

    (context "/:token" [token]
      (DELETE "/" req (friend/authorize #{:levee.auth/admin}
                                        (users/remove-invitation token)))
      (GET "/" req (layout/register token))))

  (context "/users" []
    (GET "/" req (friend/authorize #{:levee.auth/admin}
                                   (handle-resource req (db/get-users))))
    (POST "/" req (users/create-user req))

    (GET "/current" req (friend/authenticated
                          (response/response (dissoc (users/current-user req) :password))))

    (context "/:id" [id]
      (GET "/" req (friend/authorize #{:levee.auth/admin} (users/edit-user-page id req)))
      (PUT "/" req (friend/authorize #{:levee.auth/admin} (users/edit-user id req)))
      (DELETE "/" req (friend/authorize #{:levee.auth/admin} (users/remove-user id req)))

      (context "/reset/:token" [token]
        (GET "/" [] (layout/reset-password (:username (db/get-user-by-id id)) id token))
        (POST "/" req (users/reset-password id token req))))))

