(ns levee.models.trackers
  (:require
    [ring.util.response :as response]
    [levee.routes.common :refer [handle-resource]]
    [levee.models.users :as users]
    [levee.db :as db]))

(defn edit-tracker [id req]
  (if (users/admin? req)
    (do
      (db/update-tracker id
        (clojure.walk/keywordize-keys (:json-params req)))
      (response/response (db/get-trackers)))
    (response/response {:error "lacks authorization"})))

(defn edit-tracker-page [id req]
  (if (users/admin? req)
    (handle-resource req (db/get-tracker-by-id id))
    (response/redirect "/trackers")))

(defn remove-tracker [id req]
  (if (users/admin? req)
    (do
      (db/remove-tracker id)
      (response/response (db/get-trackers)))
    (response/response {:error "lacks authorization"})))

(defn create-tracker [req]
  (if (users/admin? req)
    (do
      (db/insert-tracker
        (clojure.walk/keywordize-keys (:json-params req)))
      (response/response (db/get-trackers)))
    (response/response {:error "lacks authorization"})))

