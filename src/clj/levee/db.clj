(ns levee.db
  (:require [korma.db :refer [create-db default-connection sqlite3]]
            [korma.core :refer :all]
            [me.raynes.fs :as fs]
            [environ.core :refer [env]]))

(defn connect []
  (let [db (create-db (sqlite3 {:db (.getPath (fs/file "db" (env :db)))}))]
    (default-connection db)))

(defentity users)
(defentity trackers)
(defentity invitations)

(defn get-users []
  (select users
    (fields :id :email :username :roles :token)
    (order :username)))

(defn insert-user [user]
  (let [filtered-map (select-keys user [:username :password :email :token :roles])]
    (insert users
      (values filtered-map))))

(defn update-user [id user]
  (let [filtered-map (select-keys user [:username :password :email :roles :token])]
    (update users
      (set-fields filtered-map)
      (where (= :id id)))))

(defn remove-user [id]
  (delete users
    (where (= :id id))))

(defn get-user-password [user]
  (-> (select users
        (fields :password)
        (where (= :username user))
        (limit 1))
      (first)
      (:password)))

(defn get-user-token [user]
  (-> (select users
        (fields :token)
        (where (= :username user))
        (limit 1))
      (first)
      (:token)))

(defn get-user-by-name [name]
  (some-> (select users
            (fields :id :username :password :roles :email)
            (where (= :username name))
            (limit 1))
          (first)
          (update-in [:roles] #(set [(keyword "levee.auth" %)]))))

(defn get-user-by-id-and-token [id token]
  (-> (select users
        (fields :id :username :roles :email)
        (where (and (= :id id) (= :token token)))
        (limit 1))
      (first)))

(defn get-user-by-id [id]
  (-> (select users
        (fields :id :username :roles :email)
        (where (= :id id))
        (limit 1))
      (first)))

(defn get-trackers []
  (select trackers
    (fields :id :name :url :user :password :category)
    (order :name)))

(defn get-tracker-by-id [id]
  (-> (select trackers
        (fields :id :name :url :user :password :category)
        (where (= :id id))
        (limit 1))
      (first)))

(defn insert-tracker [tracker]
  (let [filtered-map (select-keys tracker [:name :url :user :password :category])]
    (insert trackers
      (values filtered-map))))

(defn update-tracker [id tracker]
  (let [filtered-map (select-keys tracker [:name :url :user :password :category])]
    (update trackers
      (set-fields filtered-map)
      (where (= :id id)))))

(defn remove-tracker [id]
  (delete trackers
    (where (= :id id))))

(defn get-invitations []
  (select invitations
    (fields :id :token :created_at)))

(defn insert-invitation [invitation]
  (let [filtered-map (select-keys invitation [:token :created_at])]
    (insert invitations
      (values filtered-map))))

(defn remove-invitation [token]
  (delete invitations
    (where (= :token token))))

(defn get-invitation-by-token [token]
  (-> (select invitations
        (fields :id :token :created_at)
        (where (= :token token))
        (limit 1))
      (first)))

