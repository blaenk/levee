(ns levee.client.core
  (:require
    [cljs.core.async :refer [put! chan <!]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [secretary.core :as secretary :include-macros true :refer [defroute]]
    [sablono.core :as html :refer-macros [html defelem defhtml]]
    [goog.events :as events]
    [goog.history.EventType :as EventType]
    [dommy.utils :as utils]
    [dommy.core :as dommy]

    [levee.client.common :as common]

    [levee.client.components.upload :as upload]
    [levee.client.components.users :as users]
    [levee.client.components.trackers :as trackers]
    [levee.client.components.downloads :as downloads]
    [levee.client.components.download :as download]

    [jayq.core :refer [$ on off ajax]])
  (:require-macros
    [cljs.core.async.macros :refer [go-loop]]
    [jayq.macros :refer [ready]]
    [dommy.macros :refer [sel sel1 node]]))

(enable-console-print!)

(defn try-localstorage [key default]
  (or (.getItem js/localStorage key) default))

(defonce app-state
  (atom
    {:downloads []
     :download {}
     :trackers []
     :tracker {}
     :users []
     :user {}
     :current-user {}
     :route-component (fn [])
     :uploading false
     :file-settings
       {:collapsed (= (.getItem js/localStorage "file-collapsed") "true")}
     :search
       {:scope (try-localstorage "search-scope" "all")
        :pattern ""
        :sort (try-localstorage "search-sort" "name")}}))

(add-watch app-state :local-storage
  (fn [key reference old-val new-val]
    (doto js/localStorage
      (.setItem "search-scope" (get-in new-val [:search :scope]))
      (.setItem "search-sort" (get-in new-val [:search :sort]))
      (.setItem "file-collapsed" (str (get-in new-val [:file-settings :collapsed]))))))

(common/api :get "/users/current"
  (fn [res] (swap! app-state #(assoc % :current-user res))))

(ready
  (goog.events/listen
    common/history EventType/NAVIGATE
    (fn [e]
      (.log js/console (str "[LEVEE] navigated to " (.-token e)))
      (secretary/dispatch! (.-token e))))

  (.setEnabled common/history true)

  (.tooltip ($ "body")
            #js {:selector "[data-toggle=\"tooltip\"]"
                 :container "body"})

  (.config js/ZeroClipboard #js {:swfPath "/js/ZeroClipboard.swf"}))

(defn- set-route-handler! [f]
  (swap! app-state assoc :route-component f))

(defn- find-cursor [cursor condition]
  (get cursor (first (keep-indexed #(when (condition %2) %1) cursor))))

;; TODO: set page titles on route

(defroute "/" []
  (common/redirect "/downloads"))

(defroute users-path "/users" []
  (set-route-handler! #(om/build users/users-component %)))

(defroute trackers-path "/trackers" []
  (set-route-handler! #(om/build trackers/trackers-component %)))

(defroute trackers-new-path "/trackers/new" []
  (set-route-handler!
    (fn [props]
      (om/build trackers/new-tracker
                {:trackers (:trackers props)
                 :tracker {}}))))

(defroute trackers-edit-path "/trackers/:id" [id]
  (set-route-handler!
    (fn [props]
      (let [id (js/parseInt id)
            already-set (= (get-in props [:tracker :id]) id)
            loc (when-not already-set
                  (find-cursor
                    (:trackers props)
                    #(= (:id %) id)))
            found (not (nil? loc))]
        (when (and (not already-set) found)
          (om/update! (:tracker props) (om/value loc)))

        (om/build trackers/edit-tracker
                  {:tracker (:tracker props)
                   :trackers (:trackers props)
                   :tracker-id id
                   :found (or already-set found)})))))

(defroute downloads-path "/downloads" []
  (set-route-handler! #(om/build downloads/downloads-list %)))

;; shouldn't find the download if the hashes already match
(defroute download-path "/downloads/:hash" [hash]
  (set-route-handler!
    (fn [props]
      (let [already-set (= (get-in props [:download :hash]) hash)
            loc (when-not already-set
                  (find-cursor
                    (:downloads props)
                    #(= (:hash %) hash)))
            found (not (nil? loc))]
        (when (and (not already-set) found)
          (om/update! (:download props) (om/value loc)))

        (om/build download/download-page
          {:download (:download props)
           :downloads (:downloads props)
           :current-user (:current-user props)
           :hash hash
           :found (or already-set found)
           :file-settings (:file-settings props)})))))

(defn app-component [props owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [chan (om/get-state owner :chan)]
        (go-loop []
          (let [[topic value :as msg] (<! chan)]
            (case topic
              :dragging (if (not= (om/get-state owner :dragging) value)
                           (om/set-state! owner [:dragging] value))
              nil)
            (recur)))))

    om/IInitState
    (init-state [_]
      {:chan (chan)
       :dragging false})

    om/IRenderState
    (render-state [_ {:keys [chan dragging]}]
      (html
        [:div
         [:div.navbar.navbar-default.navbar-static-top
          {:role "navigation"
           :class (when dragging "dragging")}
          [:div.container
           [:div.navbar-header
            [:button.navbar-toggle
             {:type "button"
              :data-toggle "collapse"
              :data-target ".navbar-collapse"}
             [:span.sr-only "Toggle navigation"]
             [:span.icon-bar]
             [:span.icon-bar]
             [:span.icon-bar]]

            (common/app-link {:class "navbar-brand"} "/" "Levee")]

           [:div.navbar-collapse.collapse
            [:ul.nav.navbar-nav
             [:li (common/app-link "/downloads" "download")]
             [:li
              [:a
               {:href "/upload"
                :on-click
                 (fn [e]
                   (.preventDefault e)
                   (om/transact! props #(update-in % [:uploading] not)))}
               "upload"]]
             [:li (common/app-link "/trackers" "trackers")]
             (when (common/admin? (:user props))
               [:li (common/app-link "/users" "users")])
             ]
            [:ul.nav.navbar-nav.navbar-right
             [:li  (html/link-to "/logout" "logout")]]]]]

         (om/build upload/upload-component props
           {:init-state {:app-chan chan}})

         ((:route-component props) props)]))))

(defn render []
  (let [target (.getElementById js/document "root")]
    (om/detach-root target)
    (om/root app-component app-state {:target target})))

(render)

