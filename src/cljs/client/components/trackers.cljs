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

(defn tracker-component [{:keys [tracker user]} owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [clipboard]}]
      (html
        [:tr
         [:td (html/link-to (str "http://anonym.to/?" (:url tracker)) (:name tracker))]
         [:td (:category tracker)]
         [:td.text-center
          [:button.btn.btn-info.clipboard
           {:type "button" :ref "user-button" :data-clipboard-text (:user tracker)}
           (common/glyphicon "user")]]
         [:td.text-center
          [:button.btn.btn-warning.clipboard
           {:type "button" :data-clipboard-text (:password tracker)}
           (common/glyphicon "asterisk")]]
         (when (common/is-admin user)
           [:td.text-center
            [:button.btn.btn-primary
             {:type "button"}
             (common/glyphicon "edit")]])
         (when (common/is-admin user)
           [:td.text-center
            [:button.btn.btn-danger
             {:type "button"}
             (common/glyphicon "remove")]])
         ]))))

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
        [:table.table.table-striped.table-bordered.trackers
         [:thead
          [:tr
           [:th "Name"]
           [:th "Category"]
           [:th.text-center "User"]
           [:th.text-center "Pass"]
           (when (common/is-admin user) [:th])
           (when (common/is-admin user) [:th])
           ]]
         [:tbody
          (map #(om/build tracker-component {:tracker % :user user}) trackers)
          ]]))))
