(ns teslogger.server.report
  (:use [axebomber.renderer excel]
        [axebomber.usermodel])
  (:require [clojure.java.io :as io])
  (:import [java.io PipedOutputStream PipedInputStream BufferedOutputStream]))

(defn create-screenshots-book [case-ids]
  (let [pos (PipedOutputStream.)
        pis (PipedInputStream.)
        bos (BufferedOutputStream. pos 8192)
        wb (create-workbook)]
    (doseq [case-id (if (coll? case-ids) case-ids [case-ids])]
      (let [shots (->> (.listFiles (io/file "screenshots" case-id))
                   (filter #(.isFile %)))
            sheet (to-grid (.createSheet wb case-id))]
        (render sheet {:x 1 :y 1}
                   [:div
                     (for [shot shots]
                       [:div
                         [:img {:src shot :data-width 40}]
                         [:row-break]])])))
    (.connect pis pos)
    (future
      (try
        (.write wb bos)
        (finally (.close bos))))
    pis))
