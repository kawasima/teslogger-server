(ns teslogger.server.discovery
  (:require [clojure.tools.logging :as log])
  (:use [ulon-colon.consumer :only [make-consumer consume]])
  (:import [java.nio.channels DatagramChannel Selector SelectionKey]
    [java.nio ByteBuffer]
    [java.io ByteArrayInputStream DataInputStream]
    [java.net InetSocketAddress InetAddress]))

(defn- create-channel [port]
  (let [channel (DatagramChannel/open)]
    (doto (.socket channel)
      (.setBroadcast true)
      (.setReuseAddress true)
      (.bind (InetSocketAddress. 56294)))
    (.configureBlocking channel false)
    channel))

(defn read-hostname [buf]
  (let [dis (DataInputStream. (ByteArrayInputStream. (.array buf)))
        ip-bytes (make-array Byte/TYPE 4)]
    (.read dis ip-bytes 0 4)
    (str "ws:/" (InetAddress/getByAddress ip-bytes) ":" (.readInt dis))))

(defn save-screenshot [{case-id :case-id image :image}]
  (println case-id))

(defn do-receive [key]
  (let [channel (.channel key)
        buf (ByteBuffer/allocate 16)]
    (.receive channel buf)
    (.flip buf)
    (let [remote-host (read-hostname buf)
          consumer (make-consumer remote-host)]
      (log/info "Find producer: " remote-host)
      (consume consumer save-screenshot))))

(defn start []
  (let [channel (create-channel 56294)
         selector (Selector/open)]
    (.register channel selector SelectionKey/OP_READ)
    (loop [key-num (.select selector)]
      (when (> key-num 0)
        (let [key-set (.selectedKeys selector)]
          (doseq [key key-set]
            (when (.isReadable key)
              (do-receive key)))
          (recur (.select selector)))))))

