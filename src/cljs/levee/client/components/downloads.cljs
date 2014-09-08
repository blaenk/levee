(ns levee.client.components.downloads
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
    [levee.client.common :as common])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [dommy.macros :refer [sel sel1 node]]))

(defn is-locked [user download]
  ((set (:locks download)) (:username user)))

(defn- is-expiring [_ download]
  (let [d (-> download :date-added js/Date.)
        current (.getDate d)
        now (js/Date.)]
    ;; 2 weeks - 1 day to give 1 day notice
    (.setDate d (+ current (- 14 1)))
    (and (> (.getTime now) (.getTime d))
         (empty? (:locks download)))))

(defn- is-mine [user download]
  (= (:uploader download) (:username user)))

(def ^:private scopes
  {"all" (constantly true)
   "mine" is-mine
   "locked" is-locked
   "expiring" is-expiring})

(defn- sort-by-name [a b]
  (.localeCompare (-> a :name .toLowerCase) (-> b :name .toLowerCase)))

(defn- sort-by-recent [a b]
  (- (- (-> a :date-added js/Date. .getTime) (-> b :date-added js/Date. .getTime))))

(def ^:private sorts
  {"name" sort-by-name
   "recent" sort-by-recent})

(defn- search-link [cursor k prop]
  [:li
   [:a {:on-click (fn [e]
                    (.preventDefault e)
                    (om/transact! cursor #(assoc % k prop)))
        :href "#"
        :class (when (= (get-in cursor [k]) prop) "dropdown-menu-selected")}
       prop]])

(defn download-view [{:keys [download current-user]} owner]
  (om/component
    (html
      [:li.download
       [:div.download-tab-progress
        {:data-toggle "tooltip"
         :data-placement "right"
         :title (:state download)}
        [:div.download-tab-progress-inner
         {:class (str "download-state-" (:state download))
          :style {:height (str (:progress download) "%")}
          :aria-valuenow (:progress download)}]]
        (common/app-link {:class "name"}
          (str "/downloads/" (clojure.string/lower-case (:hash download)))
          (:name download))
        [:div {:class (when (is-locked current-user download) "download-lock-status")
               :data-toggle "tooltip"
               :data-placement "left"
               :title "locked"}]])))

(defn downloads-search [{:keys [search downloads-length]} owner]
  (reify
   om/IDidMount
   (did-mount [_]
     (let [node (om/get-node owner "search-box")
           value (.-value node)
           length (count value)]
       (.setSelectionRange node length length)))

   om/IRender
   (render [_]
     (html
       [:div.row
        [:div.search.col-lg-6
         [:div.input-group
          [:div.input-group-btn
           [:button
            {:type "button"
             :class "btn btn-default dropdown-toggle"
             :data-toggle "dropdown"}
            [:span.scope-label (:scope search)]
            [:span.caret]]

           [:ul.dropdown-menu {:role "menu"}
            [:li {:role "presentation" :class "dropdown-header"} "Search Scope"]
            (search-link search :scope "all")
            (search-link search :scope "mine")
            (search-link search :scope "locked")
            (search-link search :scope "expiring")]]

          [:input.form-control
           {:type "text"
            :auto-focus "on"
            :placeholder "Search"
            :auto-complete "off"
            :value (:pattern search)
            :ref "search-box"
            :on-change
            (fn [e]
              (om/transact! search #(assoc % :pattern (.. e -target -value))))}]

          [:div.input-group-btn
           [:button
            {:type "button"
             :class "btn btn-default dropdown-toggle"
             :style {:border-left "0"}
             :data-toggle "dropdown"}
            [:span.glyphicon.glyphicon-sort]]

           [:ul.dropdown-menu {:role "menu" :style {:left "auto" :right 0}}
            [:li {:role "presentation" :class "dropdown-header"} "Sort By"]
            (search-link search :sort "name")
            (search-link search :sort "recent")]]

          [:span.input-group-addon
           [:span.badge downloads-length]]]]]))))

(defn- build-download-view [d]
  (om/build download-view d {:react-key (get-in d [:download :hash])}))

(defn downloads-list [{:keys [downloads search current-user]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:websocket nil})

    ;; sub to ws feed
    om/IWillMount
    (will-mount [_]
      (.log js/console "mounting downloads")
      (when (empty? downloads)
        (common/bootstrap-or-get "/downloads" #(om/update! downloads %)))

      ;; TODO: select ws or wss based on http https, keep hostname + port
      (om/set-state! owner :websocket
                     (js/WebSocket. (str "ws://" (.. js/location -hostname) ":888/downloads/ws")))

      (set! (.-onmessage (om/get-state owner :websocket))
            (fn [msg]
              (let [json (.parse js/JSON (.-data msg))
                    clj (js->clj json :keywordize-keys true)]
                (om/update! downloads clj)))))

    ;; unsub from ws feed
    om/IWillUnmount
    (will-unmount [_]
      (.close (om/get-state owner :websocket))
      (.log js/console "unmounting downloads"))

    om/IRender
    (render [_]
      (html
        (common/spinner-when (empty? downloads)
          (let [regex (common/fuzzy-search (:pattern search))
                scoped (filter #((scopes (:scope search)) current-user %) downloads)
                filtered (filter #(.test regex (:name %)) scoped)
                sorted (sort (sorts (:sort search)) filtered)
                augmented (map (fn [d] {:download d :current-user current-user}) sorted)]
            [:div
              (om/build downloads-search
                {:search search
                 :downloads-length (count sorted)})
              [:ul.downloads (map build-download-view augmented)]]))))))

