(ns levee.routes.users
  (:require
    [cemerick.friend :as friend]
    [compojure.core :refer [defroutes context GET POST PUT DELETE]]
    [ring.util.response :as response]
    [levee.views.layout :as layout]
    [levee.db :as db]
    [levee.routes.common :refer [handle-resource]]
    [levee.models.users :as users]))

(def login-form
  [:form {:method "POST" :action "login"}
   [:input {:type "text" :name "username"}]
   [:input {:type "password" :name "password"}]
   [:input {:type "submit" :value "login"}]])

(defroutes routes
  (GET "/login" req (layout/external login-form))
  (GET "/logout" req (friend/logout* (response/redirect "/login")))

  (context "/invitations" []
    (GET "/" req (friend/authorize #{:levee.auth/admin} (db/get-invitations)))
    (POST "/" req (friend/authorize #{:levee.auth/admin} (users/create-invitation)))

    (context "/:token" [token]
      (DELETE "/" req (friend/authorize #{:levee.auth/admin} (users/remove-invitation token)))
      (GET "/" req (layout/registration token))))

  (context "/users" []
    (GET "/" req (friend/authorize #{:levee.auth/admin} (handle-resource req (db/get-users))))
    (POST "/" req (users/create-user req))

    (GET "/current" req (friend/authenticated
                          (response/response (dissoc (users/current-user req) :password))))

    (context "/:id" [id]
      (GET "/" req (friend/authorize #{:levee.auth/admin} (users/edit-user-page id req)))
      (PUT "/" req (friend/authorize #{:levee.auth/admin} (users/edit-user id req)))
      (DELETE "/" req (friend/authorize #{:levee.auth/admin} (users/remove-user id req))))))

