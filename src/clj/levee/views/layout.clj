(ns levee.views.layout
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [hiccup.element :refer [javascript-tag link-to]]
            [cheshire.core :refer [generate-string]]
            [levee.common :refer [dev?]]
            [cemerick.austin.repls :refer [browser-connected-repl-js]]))

(defn- google-fonts [name & {:keys [sizes italics]}]
  (let [stem "http://fonts.googleapis.com/css?family="
        italics (when italics
                  (let [to-italicize (if (= italics :all) sizes italics)]
                    (map #(str % "italic") to-italicize)))
        sizes (clojure.string/join "," (concat sizes italics))
        safe-name (clojure.string/replace name " " "+")
        path (str stem safe-name ":" sizes)]
    [:link {:href path
            :rel "stylesheet"
            :type "text/css"}]))

(defn app [& {:keys [body bootstrap]}]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "icon" :type "image/png" :href "/img/favicon.png"}]
     [:title "LEVEE"]
     (include-css
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"
       "//maxcdn.bootstrapcdn.com/font-awesome/4.2.0/css/font-awesome.min.css"
       "/css/style.css")
     (google-fonts "Noto Sans" :sizes [400 700] :italics :all)]
    [:body
     [:div.upload-overlay]
     [:div#root.container body]
     (when bootstrap
       (javascript-tag
         (str "window.levee_bootstrap = " (generate-string bootstrap) ";")))
     (when (dev?)
       (include-js "/js/cljs/goog/base.js"))
     (include-js
       "//code.jquery.com/jquery-1.11.0.min.js"
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"
       "/js/moment.min.js"
       "/js/react-0.11.1.min.js"
       "/js/ZeroClipboard.min.js"
       "/js/main.js")
     (when (dev?)
       (javascript-tag
         (str
           "goog.require('levee.client.common');\n"
           "goog.require('levee.client.components.upload');\n"
           "goog.require('levee.client.components.downloads');\n"
           "goog.require('levee.client.components.download');\n"
           "goog.require('levee.client.core');\n"
           "goog.require('figwheel');")))
     (when (dev?)
       [:script (browser-connected-repl-js)])]))

(defn external [scope body]
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:link {:rel "icon" :type "image/png" :href "/img/favicon.png"}]
     [:title "LEVEE"]
     (include-css
       "//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"
       "/css/style.css")
     (google-fonts "Noto Sans" :sizes [400 700] :italics :all)]
    [:body
     [:div#root.container
      [:div.col-xs-12.col-sm-offset-3.col-sm-6.col-md-6.col-md-offset-3
       [:div.navbar.navbar-default.navbar-static-top {:role "navigation"}
        [:div.container
         [:div.navbar-header
          [:div.navbar-brand scope]]]]

       body]]]))

(def login-form
  [:form {:method "POST" :action "login"}
   [:input {:type "text" :name "username"}]
   [:input {:type "password" :name "password"}]
   [:input {:type "submit" :value "login"}]])

(defn login []
  (external "login"
    [:form {:role "form" :action "/login" :method "post"}
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "text"
         :placeholder "username"
         :name "username"}]]]
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "password"
         :placeholder "password"
         :name "password"}]]]
     [:input.btn.btn-primary.pull-right
      {:type "submit"
       :value "login"}]]))

(defn register [token]
  (external "register"
    [:form {:role "form" :action "/users" :method "post"}
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "text"
         :placeholder "username"
         :name "username"}]]]
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "text"
         :placeholder "password"
         :name "password"}]]]
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "text"
         :placeholder "email"
         :name "email"}]]]
     [:input {:type "hidden" :value token :name "token"}]
     [:input.btn.btn-primary.pull-right
      {:type "submit"
       :value "register"}]
     ]))

(defn reset-password [user id token]
  (external "reset password"
    [:form {:role "form" :action (str "/users/" id "/reset/" token) :method "post"}
     [:p (str "Change the password for " user)]
     [:div.form-group
      [:div.input-group.col-xs-12
       [:input.form-control
        {:type "text"
         :placeholder "password"
         :name "password"}]]]
     [:input {:type "hidden" :value id :name "id"}]
     [:input {:type "hidden" :value token :name "token"}]
     [:input.btn.btn-primary.pull-right
      {:type "submit"
       :value "reset"}]
     ]))

