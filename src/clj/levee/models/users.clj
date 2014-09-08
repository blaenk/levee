(ns levee.models.users
  (:refer-clojure :exclude [bytes])
  (:require
    [cemerick.friend :as friend]
    [ring.util.response :as response]

    (cemerick.friend [credentials :as creds])

    [pandect.core :refer [sha1]]
    [crypto.random :refer [bytes]]

    [clj-time.core :refer [now]]
    [clj-time.coerce :refer [to-string]]

    [levee.routes.common :refer [handle-resource]]
    [levee.db :as db]))

(defn current-user [req]
  (-> req
      friend/identity
      :current
      db/get-user-by-name))

(defn admin? [req]
  (get (:roles (current-user req)) :levee.auth/admin))

(defn edit-user [id req]
  (db/update-user id
    (clojure.walk/keywordize-keys (:json-params req)))
  (response/response (db/get-users)))

(defn edit-user-page [id req]
  (handle-resource req (db/get-user-by-id id)))

(defn remove-user [id req]
  (db/remove-user id)
  (response/response (db/get-users)))

(defn create-user [req]
  (let [user (select-keys (:params req) [:username :password :email :token])
        user (assoc user :roles "member")
        user (update-in user [:password] creds/hash-bcrypt)
        ident (dissoc user :password :token)
        ident (clojure.set/rename-keys ident {:username :identity})
        ident (update-in ident [:roles] #(set [(keyword "levee.auth" %)]))]
    (if (empty? (db/get-invitation-by-token (:token user)))
      (response/response {:error "non-existent token"})
      (do
        (db/insert-user user)
        (db/remove-invitation (:token user))
        (friend/merge-authentication (response/redirect "/") ident)))))

(defn create-invitation []
  (let [invitation {:token (sha1 (bytes 64))
                    :created_at (to-string (now))}
        invitation_id (db/insert-invitation invitation)]
    (response/response (db/get-invitations))))

(defn remove-invitation [token]
  (db/remove-invitation token)
  (response/response (db/get-invitations)))

