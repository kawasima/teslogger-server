
(ns teslogger.server.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :refer [put! <! chan pub sub]]
            [clojure.browser.net :as net]
            [goog.events :as events])
  (:import [goog.net.EventType]
           [goog.events EventType]))

(def app-state (atom {:page :splash
                      :screenshot {:url nil
                                   :annotations {}}}))

(enable-console-print!)

(defcomponent annotation-line [line owner]
  (render [_]
    (html
      [:line line])))

(defcomponent annotation-comment-box [annotation owner]
  (init-state [_]
    {:edit? false})
  (render-state [_ {:keys [parent-ch edit?]}]
    (html
     (if-not edit?
       [:rect {:class "note"
               :x (get-in annotation [:position :x2])
               :y (get-in annotation [:position :y2])
               :width "200px"
               :height "50px"
               :on-click (fn [e]
                           (put! parent-ch [:select-annotation]))
               :on-dbl-click (fn [e]
                               (put! parent-ch [:edit-comment]))}]))))

(defcomponent annotation [annotation owner]
  (init-state [_]
    {:select? false
     :dragging? false
     :comm (chan)
     :sub-ch (chan)})
  (will-mount [_]
    (sub (om/get-state owner :parent-sub-ch)
         (:id annotation)
         (om/get-state owner :sub-ch))
    (go-loop []
      (let [[command val] (<! (om/get-state owner :comm))]
        (case command
          :select-annotation (om/set-state! owner :select? true)
          :edit-comment ())
        (recur)))
    (go-loop []
      (let [{:keys [command args]} (<! (om/get-state owner :sub-ch))]
        (case command
          :unselect (om/set-state! owner :select? false)
          :move     (om/transact! annotation
                                  #(-> %
                                       (assoc-in [:position :x1] (:x args))
                                       (assoc-in [:position :y1] (:y args))))))))
  (render-state [_ {:keys [comm select? dragging? offset parent-ch sub-ch]}]
    (html
      [:g
        (om/build annotation-comment-box annotation
          {:init-state {:parent-ch comm}})
        (om/build annotation-line (:position annotation))
        (when select?
          [:rect {:class "handle handle-line"
                  :x (- (get-in annotation [:position :x1]) 4)
                  :y (- (get-in annotation [:position :y1]) 4)
                  :width 8 :height 8
                  :on-mouse-down (fn [e]
                                   (.stopPropagation e)
                                   (om/set-state! owner :dragging? true))
                  :on-mouse-up (fn [e]
                                 (.stopPropagation e)
                                 (put! parent-ch [:move annotation])
                                 (om/set-state! owner :dragging false))}])])))

(defn- line-length [{:keys [x1 x2 y1 y2]}]
  (.sqrt js/Math
    (+ (.pow js/Math (.abs js/Math (- x1 x2)) 2)
       (.pow js/Math (.abs js/Math (- y1 y2)) 2))))

(defcomponent screenshot-app [screenshot owner]
  (init-state [_]
    (let [pub-ch (chan)]
      {:dragging nil
       :max-id 0
       :offset {:x 0 :y 0}
       :image-size {:w 0 :h 0}
       :selected-annotation nil
       :pub-ch pub-ch
       :sub-ch (pub pub-ch :id) ;; Annotation ID
       :comm (chan)}))

  (will-mount [_]
    (go-loop []
      (let [[type value] (<! (om/get-state owner :comm))]
        (case type
          :move (om/update! screenshot [:annotations (:id value)] value)
          :select (om/set-state! owner :selected-annotation value)))))
 
  (did-mount [_]
    (loop [el (om/get-node owner "screenshot-app") offset-x (.-offsetLeft el) offset-y (.-offsetTop el)]
      (let [parent (.-offsetParent el)]
        (if (or (= parent nil) (= parent el)) 
          (om/set-state! owner :offset {:x offset-x :y offset-y})
          (recur parent
            (+ offset-x (.-offsetLeft parent))
            (+ offset-y (.-offsetTop  parent)))))))

  (render-state [_ {:keys [dragging offset comm parent-ch
                           max-id selected-annotation pub-ch sub-ch]}]
    (html
      [:div.ui.dimmer.page.visible.active {:on-click (fn [e] (put! parent-ch [:hide-shot nil]))}
       [:div.ui.test.modal.transition.visible.active
        {:style {:top "10"}
         :on-click (fn [e] (.stopPropagation e))}
        [:div.header "Evidence"]
        [:div#screenshot-app {:ref "screenshot-app"}
         [:img {:src (:url screenshot)
                :on-load (fn [e]
                           (om/set-state! owner :image-size
                                          {:w (.. e -target -width)
                                           :h (.. e -target -height)}))}]
          [:svg {:width  (str (om/get-state owner [:image-size :w]) "px")
                 :height (str (om/get-state owner [:image-size :h]) "px")
                 :on-mouse-down (fn [e]
                                  (.stopPropagation e)
                                  (if selected-annotation
                                    (put! pub-ch {:id (:id selected-annotation)
                                                  :command    :unselected})
                                    (let [x (- (.. e -pageX) (:x offset))
                                          y (- (.. e -pageY) (:y offset))]
                                      (om/set-state! owner :dragging
                                                     {:x1 x :y1 y
                                                      :x2 x :y2 y}))))
                 :on-mouse-up (fn [e]
                                (.stopPropagation e)
                                (let [line (om/get-state owner :dragging)]
                                  (when (and line (> (line-length line) 20))
                                    (om/update-state! owner :max-id inc)
                                    (om/update! screenshot [:annotations (om/get-state owner :max-id)]
                                                  {:id (om/get-state owner :max-id)
                                                   :position dragging
                                                   :description "memo"})))
                                (om/set-state! owner :dragging nil))
                 
                 :on-mouse-move (fn [e]
                                  (.stopPropagation e)
                                  (let [x (- (.. e -pageX) (:x offset))
                                        y (- (.. e -pageY) (:y offset))]
                                    (if selected-annotation
                                      (put! pub-ch {:id (:id selected-annotation) 
                                                    :command    :move
                                                    :args       {:x x :y y}})
                                      (when dragging
                                        (om/update-state! owner :dragging
                                                          #(assoc % :x2 x :y2 y))))))}
           (om/build-all annotation (vals (:annotations screenshot)) 
                         {:init-state {:offset offset
                                       :parent-ch comm
                                       :parent-sub-ch sub-ch}})
           (om/build annotation-line dragging)]]]])))

(defcomponent test-case-panel [[case-name shot-in-case] owner]
  (init-state [_]
    {:selected? false})
  (render-state [_ {:keys [selected? parent-ch]}]
    (html
     [:div
      [:h2
       {:on-click (fn [e]
                    (om/set-state! owner :selected? (not selected?))
                    (if selected?
                      (put! parent-ch [:unselect-case case-name])
                      (put! parent-ch [:select-case   case-name])))}
       (if selected?
         [:i.icon.checkbox.checked.green]
         [:i.icon.checkbox.empty])
       case-name]
      [:div#links.ui.medium.images
       (for [shot shot-in-case]
         [:img.data-gallery
          {:src shot
           :on-click (fn [e]
                       (put! parent-ch [:show-shot shot]))}])]])))

(defcomponent gallery-app [app owner]
  (init-state [_]
    {:test-cases []
     :selected-cases #{}
     :comm (chan)})
  (will-mount [_]
    (let [xhrio (net/xhr-connection)]
      (events/listen xhrio goog.net.EventType.SUCCESS
        (fn [e]
          (om/set-state! owner :test-cases (js->clj (.getResponseJson xhrio)))))
      (.send xhrio "/gallery"))
    (go-loop []
      (let [[type val] (<! (om/get-state owner :comm))]
        (case type
          :select-case   (om/update-state! owner :selected-cases #(conj % val))
          :unselect-case (om/update-state! owner :selected-cases #(disj % val))
          :show-shot     (om/update! app [:screenshot :url] val)
          :hide-shot     (om/update! app [:screenshot] {:url nil :annotations []}))
        (recur))))

  (render-state [_ {:keys [selected-cases test-cases selected-shot comm]}]
    (html
      [:div
       (om/build-all test-case-panel test-cases
                     {:init-state {:parent-ch comm}})
       (when (get-in app [:screenshot :url])
         (om/build screenshot-app (:screenshot app)
                   {:init-state {:parent-ch comm}}))
       [:div.ui.fixed.menu.inverted.purple
        (when-not (empty? selected-cases)
          [:div.item
           (str (count selected-cases) " case" (when (> (count selected-cases) 1) "s") " selected.")
           [:form.actions {:method "post" :action "/download"}
            (for [c selected-cases]
              [:input {:type "hidden" :name "case-id" :value c}])
            [:button.ui
             [:i.icon.download]]]])]])))

(defcomponent splash-app [app owner]
  (render [_]
    (doseq [body (array-seq (.getElementsByTagName js/document "body"))]
      (.setAttribute body "class" "splash"))
    (html
     [:div.segment
      [:div.container
       [:h1 "teslogger server"]
       [:a.ui.circular.button.blue
        {:on-click (fn [e]
                     (doseq [body (array-seq (.getElementsByTagName js/document "body"))]
                       (.removeAttribute body "class"))
                     (om/update! app :page :gallery))}
        "Start to evidence"]]])))

(defcomponent main-app [app owner]
  (render [_]
    (html
      (case (:page app)
        :splash  (om/build splash-app app)
        :gallery (om/build gallery-app app)))))

(om/root main-app app-state
  {:target (.getElementById js/document "app")})

