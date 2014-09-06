(ns levee.client.components.trackers
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

(defn tracker-component [{{:keys [id name url user password category]} :tracker
                          trackers :trackers
                          :as props} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [clipboard]}]
      (html
        [:tr
         [:td (html/link-to (str "http://anonym.to/?" url) name)]
         [:td category]
         [:td.text-center
          [:button.btn.btn-info.clipboard
           {:type "button" :ref "user-button" :data-clipboard-text user}
           (common/glyphicon "user")]]
         [:td.text-center
          [:button.btn.btn-warning.clipboard
           {:type "button" :data-clipboard-text password}
           (common/glyphicon "asterisk")]]
         (when (common/is-admin (:user props))
           [:td.text-center
            (common/app-link
              {:class "btn btn-primary"}
              (str "/trackers/" id) (common/glyphicon "edit"))
            ])
         (when (common/is-admin (:user props))
           [:td.text-center
            [:button.btn.btn-danger
             {:type "button"
              :on-click
               (fn [e]
                 (when (js/confirm "are you sure you want to delete this?")
                  (common/api :delete (str "/trackers/" id)
                    (fn [m]
                      (om/update! trackers m)
                      (common/redirect "/trackers")))))}
             (common/glyphicon "remove")]])]))))

(defn trackers-component [{:keys [trackers user]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:clipboard (js/ZeroClipboard.)})

    om/IWillMount
    (will-mount [_]
      (when (empty? trackers)
        (common/bootstrap-or-get "/trackers" #(om/update! trackers %))))

    om/IDidMount
    (did-mount [_]
      (doto (om/get-state owner :clipboard)
        (.unclip)
        (.clip ($ "button.clipboard"))))

    om/IDidUpdate
    (did-update [_ _ _]
      (doto (om/get-state owner :clipboard)
        (.unclip)
        (.clip ($ "button.clipboard"))))

    om/IRender
    (render [_]
      (html
        [:div
         [:table.table.table-striped.table-bordered.trackers
          [:thead
           [:tr
            [:th "Name"]
            [:th "Category"]
            [:th.text-center "User"]
            [:th.text-center "Pass"]
            (when (common/is-admin user) [:th])
            (when (common/is-admin user) [:th])]]
          [:tbody
           (map #(om/build tracker-component {:trackers trackers :tracker % :user user}) trackers)]]
         [:form {:role "form"}
          [:div.form-group
           [:div.input-group.col-sm-12
            (common/app-link
              {:class "btn btn-success pull-right"}
              "/trackers/new" "new tracker")
            ]]]]))))

(defn edit-tracker [{trackers :trackers
                     found :found
                     tracker-id :tracker-id
                     {:keys [id name url user password category]
                      :as tracker} :tracker} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (if (and (empty? tracker) (not found))
        (common/bootstrap-or-get
          (str "/trackers/" tracker-id)
          #(om/update! tracker %))))

    om/IRender
    (render [_]
      (html
        [:form {:role "form"}
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "name"
             :on-change
              (fn [e]
                (om/transact!
                  tracker
                  #(assoc % :name (.. e -target -value))))
             :value name
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :class "form-control"
             :placeholder "url"
             :on-change
              (fn [e]
                (om/transact!
                  tracker
                  #(assoc % :url (.. e -target -value))))
             :value url
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "category"
             :on-change
              (fn [e]
                (om/transact!
                  tracker
                  #(assoc % :category (.. e -target -value))))
             :value category
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "user"
             :on-change
              (fn [e]
                (om/transact!
                  tracker
                  #(assoc % :user (.. e -target -value))))
             :value user
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "password"
             :on-change
              (fn [e]
                (om/transact!
                  tracker
                  #(assoc % :password (.. e -target -value))))
             :value password
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           (common/app-link
             {:class "btn btn-danger"}
             "/trackers" "cancel")
           [:button.btn.btn-primary.pull-right
            {:type "button"
             :on-click
             (fn [e]
               (common/api :put (str "/trackers/" tracker-id)
                 @tracker
                 (fn [m]
                   (om/update! trackers m)
                   (common/redirect "/trackers"))))}
            "submit"]]]
         ]))))

(defn new-tracker [{:keys [trackers]} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [id name url user password category]}]
      (html
        [:form {:role "form"}
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "name"
             :on-change
              (fn [e]
                (om/set-state!
                  owner
                  :name (.. e -target -value)))
             :value name
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :class "form-control"
             :placeholder "url"
             :on-change
              (fn [e]
                (om/set-state!
                  owner
                  :url (.. e -target -value)))
             :value url
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "category"
             :on-change
              (fn [e]
                (om/set-state!
                  owner
                  :category (.. e -target -value)))
             :value category
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "user"
             :on-change
              (fn [e]
                (om/set-state!
                  owner
                  :user (.. e -target -value)))
             :value user
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           [:input.form-control
            {:type "text"
             :placeholder "password"
             :on-change
              (fn [e]
                (om/set-state!
                  owner
                  :password (.. e -target -value)))
             :value password
             }]]]
         [:div.form-group
          [:div.input-group.col-sm-12
           (common/app-link
             {:class "btn btn-danger"}
             "/trackers" "cancel")
           [:button.btn.btn-primary.pull-right
            {:type "button"
             :on-click
             (fn [e]
               (common/api :post "/trackers"
                 (om/get-state owner)
                 (fn [m]
                   (om/update! trackers m)
                   (common/redirect "/trackers"))))}
            "submit"]]]
         ]))))

