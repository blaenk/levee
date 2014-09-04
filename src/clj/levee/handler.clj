(ns levee.handler
  (:require
    [ring.util.response :as response]

    [ring.middleware.session.cookie :refer [cookie-store]]
    [ring.middleware
     [logger :refer [wrap-with-logger]]
     [accept-param :refer [wrap-accept-param accept-format?]]
     [reload :as reload]
     [gzip :refer [wrap-gzip]]
     [json :refer [wrap-json-response
                   wrap-json-body
                   wrap-json-params]]]

    [prone.middleware :as prone]

    [environ.core :refer [env]]

    [compojure.route :as route]
    [compojure.core :refer [defroutes routes context GET POST PUT DELETE]]
    compojure.handler

    [cemerick.friend :as friend]
    (cemerick.friend [workflows :as workflows]
                     [credentials :as creds])

    [clj-time.core :refer [in-seconds interval now weeks from-now]]

    [levee.db :as db]
    [levee.common :refer [dev?]]
    [levee.views.layout :as layout]
    [levee.routes [downloads :as downloads]
                  [trackers :as trackers]
                  [users :as users]
                  [invitations :as invitations]
                  [common :refer [handle-resource]]]))

(defn hot-reload [handler]
  (if (dev?)
    (reload/wrap-reload handler)
    handler))

(defroutes base-routes
  (GET "/*" [] (friend/authenticated (layout/app))))

(def app-routes
  (routes
    users/routes
    downloads/routes
    invitations/routes
    trackers/routes
    base-routes))

;; request comes in from top-layer, needs to be processed
;; in preparation for next inner-layer
(def app
  (-> app-routes
      (friend/authenticate
        {:credential-fn
          (partial creds/bcrypt-credential-fn db/get-user-by-name)
         :workflows [(workflows/interactive-form)]
         :allow-anon? true
         :login-uri "/login"
         :default-landing-uri "/downloads"})
      (compojure.handler/site
        {:session
         ;; TODO: make this configurable of course
         {:store (cookie-store {:key (env :secret)})
          :cookie-name "levee-session"
          :cookie-attrs
            {:max-age (-> (interval (now) (-> 2 weeks from-now))
                          in-seconds)}}})
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-json-params)
      (wrap-json-response)
      (wrap-accept-param)
      (wrap-gzip)
      (wrap-with-logger)
      (prone/wrap-exceptions)
      (hot-reload)))

