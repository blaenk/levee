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
    [jayq.core :refer [ajax]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [dommy.macros :refer [sel sel1 node]])
  (:import goog.history.Html5History))

(def history
  (doto (Html5History.)
    (.setUseFragment false)
    (.setPathPrefix "")))

(defn redirect [to]
  (.setToken history to))

(defn api
  "last param is an success callback or a settings map:

   (api :get \"/trackers\" (fn [m] done))

   (api :get \"/trackers\"
     {:success (fn [m] done)
      :error (fn [m] error)}

   can also be called with data where last param
   is also success callback or settings map:

   (api :post \"/trackers\" {:name \"test\"}
     (fn [m] done))"

  ([method endpoint fn-or-map]
   (api method endpoint nil fn-or-map))

  ([method endpoint data fn-or-map]
   (let [defaults {:type (name method)
                   :contentType "application/json; charset=UTF-8"
                   :converters
                     {"json clojure" #(js->clj % :keywordize-keys true)}
                   :data (if (or
                               (keyword? data)
                               (symbol? data)
                               (map? data)
                               (coll? data)
                               (satisfies? cljs.core/IEncodeJS data))
                           (.stringify js/JSON (clj->js data))
                           data)
                   :dataType "json clojure"}
         settings (if (fn? fn-or-map)
                    {:success fn-or-map}
                    fn-or-map)
         merged (merge defaults settings)]
     (ajax endpoint merged))))

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
    (.stopPropagation e)
    (redirect (.getAttribute (.-currentTarget e) "href"))))

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
      (api :get endpoint f))))

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

(defn admin? [user]
  (get (set (:roles user)) "levee.auth/admin"))

(defn form-input [placeholder value on-change]
  [:div.form-group
   [:div.input-group.col-sm-12
    [:input.form-control
     {:type "text"
      :placeholder placeholder
      :on-change on-change
      :value value}]]])

