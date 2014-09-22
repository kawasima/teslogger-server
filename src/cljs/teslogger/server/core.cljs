(ns teslogger.server.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan]]
            [clojure.browser.net :as net]
            [goog.events :as events])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(def app-state (atom {:screenshot-url ""}))
(enable-console-print!)

(defcomponent annotation-line [line owner]
  (render [_]
    (html
      [:line line])))

(defcomponent annotation-comment-box [data owner]
  (render [_]
    (html
      [:rect {:class "note"
              :x (:x data)
              :y (:y data)
              :width "200px"
              :height "50px"
              :on-click (fn [e]
                          (put! (om/get-state owner :comm) :select-annotation))}])))

(defcomponent annotation [position owner]
  (init-state [_]
    {:text "Note"
     :select? false
     :comm (chan)})
  (will-mount [_]
    (go-loop []
      (let [command (<! (om/get-state owner :comm))]
        (case command
          :select-annotation (om/set-state! owner :select? true))
        (recur))))
  (render-state [_ {:keys [comm select?]}]
    (html
      [:g
        (om/build annotation-comment-box {:x (:x2 position) :y (:y2 position)}
          {:init-state {:comm comm}})
        [:text {:x (:x2 position) :y (:y2 position)} (om/get-state owner :text)]
        (om/build annotation-line position)
        (when select?
          [:rect {:class "handle handle-line"
                  :x (- (:x1 position) 4)  :y (- (:y1 position) 4)
                  :width 8 :height 8}])])))

(defn- line-length [{:keys [x1 x2 y1 y2]}]
  (.sqrt js/Math
    (+ (.pow js/Math (.abs js/Math (- x1 x2)) 2)
       (.pow js/Math (.abs js/Math (- y1 y2)) 2))))

(defcomponent main-app [app owner]
  (init-state [_]
    {:annotations []
     :dragging nil
     :offset {:x 0 :y 0}})

  (did-mount [_]
    (let [parent (.. (om/get-node owner) -offsetParent) ]
      (om/set-state! owner :offset {:x (.-offsetLeft parent) :y (.-offsetTop parent)})))

  (render-state [_ {:keys [dragging offset annotations]}]
    (html
      [:svg {:width "800px"
             :height "600px"
             :on-mouse-down (fn [e]
                              (let [x (- (.. e -pageX) (:x offset))
                                    y (- (.. e -pageY) (:y offset))]
                                (om/set-state! owner :dragging
                                  {:x1 x :y1 y
                                   :x2 x :y2 y})))
             :on-mouse-up (fn [e]
                            (let [line (om/get-state owner :dragging)]
                              (when (and line (> (line-length line) 20))
                                (om/update-state! owner :annotations
                                  #(conj % dragging))))
                            (om/set-state! owner :dragging nil))
                            
             :on-mouse-move (fn [e]
                              (if dragging 
                                (om/update-state! owner :dragging
                                  #(assoc % :x2 (- (.. e -pageX) (:x offset))
                                     :y2 (- (.. e -pageY) (:y offset))))))}
        (om/build-all annotation annotations)
        (om/build annotation-line dragging)])))

(om/root main-app app-state
  {:target (.getElementById js/document "app")})

