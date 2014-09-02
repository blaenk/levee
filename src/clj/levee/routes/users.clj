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
  (GET "/logout" req (friend/logout* (response/redirect (str (:context req) "/login"))))

  (context "/users" []
    (GET "/" [] (friend/authenticated nil))
    (POST "/" req (friend/authenticated nil))

    (GET "/current" req (friend/authenticated (response/response (dissoc (users/current-user req) :password))))

    (context "/:id" [id]
      (PUT "/" req (friend/authenticated nil))
      (DELETE "/" req (friend/authenticated nil)))))

