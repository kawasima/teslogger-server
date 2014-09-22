(ns teslogger.server.core
  (:use [compojure.core]
    [hiccup.middleware :only [wrap-base-url]]
    [hiccup core page element])
  (:require [compojure.route :as route]
    [compojure.handler :as handler]
    [compojure.response :as response]))

(defn index []
  (html5 {:ng-app "teslogger"}
    [:head
      [:title ""]
      (include-css "//netdna.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css"
        "css/teslogger.css")]
    [:body 
      [:div.row
        [:div#app.col-md-8.col-md-offset-2]]
      (include-js "http://fb.me/react-0.11.1.js"
        "js/main.js")
      (javascript-tag "goog.require('teslogger.server.core');")]))

(defroutes main-routes
  (GET "/" [] (index))
  (route/resources "/"))

(def app
  (-> (handler/site main-routes)
    (wrap-base-url)))
