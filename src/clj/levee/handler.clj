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
    [levee.common :refer [dev? conf]]
    [levee.views.layout :as layout]
    [levee.routes [downloads :as downloads]
                  [trackers :as trackers]
                  [users :as users]
                  [common :refer [handle-resource]]]))

(defn dev-handlers [handler]
  (if (dev?)
    (-> handler
      (wrap-with-logger)
      (prone/wrap-exceptions)
      (reload/wrap-reload))
    handler))

(defroutes base-routes
  (GET "/*" [] (friend/authenticated (layout/app))))

(def app-routes
  (routes
    users/routes
    downloads/routes
    trackers/routes
    base-routes))

(defn- unauthorized-handler [req]
  (if (= (get-in req [:params :accept]) "json")
    (response/response {:error "you're unauthorized"})
    {:status 403
     :body (layout/external "unauthorized"
                            [:div.error-authorization
                             "You don't have the proper authorization!"])}))

(defn- unauthenticated-handler [req]
  (if (= (get-in req [:params :accept]) "json")
    (response/response {:error "you're unauthenticated"})
    (friend/default-unauthenticated-handler req)))

(defn- login-failure-handler [req]
  (response/redirect-after-post (get-in req [:headers "referer"])))

;; request comes in from top-layer, needs to be processed
;; in preparation for next inner-layer
(def app
  (-> app-routes
      (friend/authenticate
        {:credential-fn
          (partial creds/bcrypt-credential-fn db/get-user-by-name)
         :workflows [(workflows/interactive-form
                       :login-failure-handler #'login-failure-handler)]
         :default-landing-uri "/"
         :unauthenticated-handler #'unauthenticated-handler
         :unauthorized-handler #'unauthorized-handler})
      (compojure.handler/site
        {:session
         {:store (cookie-store {:key (conf :secret)})
          :cookie-name "levee-session"
          :cookie-attrs
            {:max-age (-> (interval (now) (-> 2 weeks from-now))
                          in-seconds)}}})
      (wrap-json-body {:keywords? true :bigdecimals? true})
      (wrap-json-params)
      (wrap-json-response)
      (wrap-accept-param)
      (wrap-gzip)
      (dev-handlers)))

