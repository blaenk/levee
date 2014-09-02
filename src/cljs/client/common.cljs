(ns levee.client.common
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
    [ajax.core :refer [GET POST json-response-format]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [dommy.macros :refer [sel sel1 node]])
  (:import goog.history.Html5History))

(def history
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")))

(defn api-get [endpoint f & {:keys [error-handler]}]
  (GET endpoint
    {:handler f
     :error-handler
      (or error-handler
          (fn [res]
            (.log js/console "error in api-get")
            (.log js/console res)))
     :response-format (json-response-format {:keywords? true})
     :keywords? true
     :headers {:accept "application/json"}}))

(defn api-post [endpoint params f & {:keys [error-handler]}]
  (POST endpoint
    {:format :json
     :params params
     :handler f
     :error-handler
      (or error-handler
          (fn [res]
            (.log js/console "error in api-post")
            (.log js/console res)))
      :response-format (json-response-format {:keywords? true})
      :keywords? true
      :headers {:accept "application/json"}}))

(defelem spinner []
  [:div.spinner
   [:div.rect1]
   [:div.rect2]
   [:div.rect3]
   [:div.rect4]
   [:div.rect5]])

(defn spinner-when [condition component]
  (if condition (spinner) component))

(defn- app-link-handler [e]
  (when (not= 1 (.-button e))
    (.preventDefault e)
    (.setToken history (.getAttribute (.-target e) "href"))))

(defelem app-link [to text]
  "creates a link that prevents default
   navigation and instead sets the HTML5 history API
   token, _only_ if it's not a middle-click"
  [:a {:href to :on-click app-link-handler} text])

(defn bootstrap-or-get [endpoint f]
  (if-let [data (-> (.-levee_bootstrap js/window)
                    (js->clj :keywordize-keys true))]
    (do
      (.log js/console "bootstrapped")
      (f data)
      (set! (.-levee_bootstrap js/window) nil))
    (do
      (.log js/console "couldn't bootstrap; getting")
      (api-get endpoint (fn [res] (f res))))))

(defn fuzzy-search [search]
  "creates a case-insensitive regex where spaces become .*"
  (try (js/RegExp. (clojure.string/replace search " " ".*") "i")
    (catch js/Error e #"")))

(defn filesize [size-in-bytes]
  (if (= size-in-bytes 0)
    "0 B"
    (let [units [:B, :KB, :MB, :GB, :TB]
          base (long
                 (.floor js/Math
                         (/ (.log js/Math size-in-bytes)
                            (.log js/Math 1000))))
          value (/ size-in-bytes (float (.pow js/Math 1000 base)))
          suffix (name (get units base))]
      (str (.toFixed value 1) " " suffix))))

(defelem glyphicon [icon]
  [:span.glyphicon {:class (str "glyphicon-" icon)}])

(defelem fontawesome [icon]
  [:i.fa {:class (str "fa-" icon)}])

(defn is-admin [user]
  (get (set (:roles user)) "levee.auth/admin"))

