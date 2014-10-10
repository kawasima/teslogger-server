(ns teslogger.server.core
  (:use [compojure.core]
        [liberator.core :only [defresource request-method-in]]
        [liberator.representation :only [Representation]]
        [hiccup.middleware :only [wrap-base-url]]
        [hiccup core page element])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :as response]
            [teslogger.server [sync :as sync]
                              [report :as report]])
  (:import [java.io File]))

(def screenshots-dir (File. "screenshots"))

(defresource gallery
  :available-media-types ["application/json"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx]
               (let [cases (->> (.listFiles screenshots-dir)
                             (filter #(.isDirectory %)))]
                 (apply assoc {}
                   (interleave
                     (map #(.getName %) cases)
                     (map (fn [dir]
                      (->> (.listFiles dir)
                        (map #(str "/s/" (.getName dir) "/" (.getName %))))) cases)))))
  :handle-exception #(.printStackTrace (:exception %)))

(defn index []
  (html5
    [:head
     [:title "Teslogger server"]
     (include-css "/css/semantic.min.css"
                  "css/animate.min.css"
                  "css/teslogger.css")]
    [:body 
     [:div.segment
      [:div#app]]
     (include-js "/javascript/react-0.11.2.js"
                  "javascript/main.js")
     (javascript-tag "goog.require('teslogger.server.core');")]))

(defroutes main-routes
  (GET "/" [] (index))
  (GET "/gallery" [] gallery)
  (POST "/download" {{case-ids :case-id} :params}
    (-> (response/response (report/create-screenshots-book case-ids))
        (response/content-type "application/vnd.openxmlformats-officeddocument.spreadsheetml.sheet")
        (response/header "content-disposition" (str "attachment; filename=\"evidence.xlsx\""))))
  (route/files "/s" {:root "screenshots"} )
  (route/resources "/"))

(def app
  (-> (handler/site main-routes)
    (wrap-base-url)))

(sync/start)
