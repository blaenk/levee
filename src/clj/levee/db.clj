(ns levee.db
  (:require [korma.db :refer [defdb sqlite3]]
            [korma.core :refer :all]))

(defdb db (sqlite3 {:db "levee.db"}))

(defentity users)
(defentity trackers)
(defentity invitations)

(defn get-users []
  (select users
    (fields :id :email :username :password :token :role)))

(defn insert-user [user]
  (let [filtered-map (select-keys user [:email :username :password :token :role])]
    (insert users
      (values filtered-map))))

(defn update-user [id user]
  (let [filtered-map (select-keys user [:username :email :role])]
    (update trackers
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
            (fields :id :username :password :roles)
            (where (= :username name))
            (limit 1))
          (first)
          (update-in [:roles] #(set [(keyword "levee.auth" %)]))))

(defn get-user-by-token [token]
  (-> (select users
        (fields :id :username :role)
        (where (= :token token))
        (limit 1))
      (first)))

(defn get-trackers []
  (select trackers
    (fields :id :name :url :user :password :category)
    (order :name)))

(defn insert-tracker [tracker]
  (let [filtered-map (select-keys tracker [:name :url :user :password :category])]
    (insert users
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

(defn remove-token [token]
  (delete invitations
    (where (= :token token))))

(defn get-invitation-by-token [token]
  (-> (select invitations
        (fields :id :token :created_at)
        (where (= :token token))
        (limit 1))
      (first)))

