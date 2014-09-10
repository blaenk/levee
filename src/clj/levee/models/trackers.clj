(ns levee.models.trackers
  (:require
    [ring.util.response :as response]
    [levee.routes.common :refer [handle-resource]]
    [levee.models.users :as users]
    [levee.db :as db]))

(defn edit-tracker [id req]
  (db/update-tracker id
                     (clojure.walk/keywordize-keys (:json-params req)))
  (response/response (db/get-trackers)))

(defn edit-tracker-page [id req]
  (handle-resource req (db/get-tracker-by-id id)))

(defn remove-tracker [id req]
  (db/remove-tracker id)
  (response/response (db/get-trackers)))

(defn create-tracker [req]
  (db/insert-tracker
    (clojure.walk/keywordize-keys (:json-params req)))
  (response/response (db/get-trackers)))

