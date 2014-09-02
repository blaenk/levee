(ns levee.models.users
  (:require
    [cemerick.friend :as friend]
    [levee.db :as db]))

(defn current-user [req]
  (-> req
      friend/identity
      :current
      db/get-user-by-name))

