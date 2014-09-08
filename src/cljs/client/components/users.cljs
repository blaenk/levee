(ns levee.client.components.users
  (:require
    [cljs.core.async :refer [put! chan <! mult tap close! untap]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    [sablono.core :as html :refer-macros [html defelem defhtml]]
    [goog.events :as events]
    [goog.history.EventType :as EventType]
    [levee.client.common :as common]
    [clojure.data :refer [diff]]
    [jayq.core :refer [$ on off ajax]])
  (:require-macros
    [cljs.core.async.macros :refer [go-loop]]))

(defn edit-user [{users :users
                     found :found
                     user-id :user-id
                     {:keys [id email username roles]
                      :as user} :user} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (if (and (empty? user) (not found))
        (common/bootstrap-or-get
          (str "/user/" user-id)
          #(om/update! user %))))

    om/IRender
    (render [_]
      (letfn [(handler [k] (fn [e] (om/transact! user #(assoc % k (.. e -target -value)))))]
        (html
          [:form {:role "form"}
           (common/form-input "username" username (handler :username))
           (common/form-input "email" email (handler :email))
           (common/form-input "roles" roles (handler :roles))
           [:div.form-group
            [:div.input-group.col-sm-12
             (common/app-link
               {:class "btn btn-danger"}
               "/users" "cancel")
             [:button.btn.btn-primary.pull-right
              {:type "button"
               :on-click
               (fn [e]
                 (common/api :put (str "/users/" user-id)
                   @user
                   (fn [m]
                     (om/update! users m)
                     (common/redirect "/users"))))}
              "submit"]]]
           ])))))

(defn user-component [{{:keys [id email username roles]} :user
                       users :users
                       :as props} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:tr
         [:td username]
         [:td email]
         [:td.text-center roles]
         [:td.text-center
          (common/app-link
            {:class "btn btn-primary"}
            (str "/users/" id) (common/glyphicon "edit"))]
         [:td.text-center
          [:button.btn.btn-danger
           {:type "button"
            :on-click
            (fn [e]
              (when (js/confirm "are you sure you want to delete this?")
                (common/api :delete (str "/users/" id)
                  (fn [m]
                    (om/update! users m)))))}
           (common/glyphicon "remove")]]]))))

(defn users-component [{:keys [users current-user invitations]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (when (empty? users)
        (common/bootstrap-or-get "/users" #(om/update! users %)))

      (common/api :get "/invitations" #(om/update! invitations %)))

    om/IRender
    (render [_]
      (html
        [:div
         [:table.table.table-striped.table-bordered.users
          [:thead
           [:tr
            [:th "Name"]
            [:th "email"]
            [:th.text-center "roles"]
            [:th]
            [:th]]]
          [:tbody
           (for [user users]
             (om/build user-component
                       {:users users
                        :user user
                        :current-user current-user}))]]
         [:form {:role "form"}
          [:div.form-group
           [:div.input-group.col-sm-12
            [:button.btn.btn-success.pull-right
             {:type "button"
              :on-click
              (fn [e]
                (common/api :post "/invitations"
                            (fn [m] (om/update! invitations m))))}
             "new invitation"]
            ]]]

         (when (> (count invitations) 0)
           [:table.table.table-striped.table-bordered.users
            [:thead
             [:tr
              [:th "token"]
              [:th "created"]
              [:th]]]
            [:tbody
             (for [{:keys [token created_at] :as invitation} invitations]
               [:tr
                [:td (html/link-to (str "/invitations/" token) token)]
                [:td created_at]
                [:td.text-center
                 [:button.btn.btn-danger
                  {:type "button"
                   :on-click
                   (fn [e]
                     (when (js/confirm "are you sure you want to delete this?")
                       (common/api :delete (str "/invitations/" token)
                                   (fn [m]
                                     (om/update! invitations m)))))}
                  (common/glyphicon "remove")]
                 ]])]])]))))

