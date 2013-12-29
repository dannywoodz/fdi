(ns fdi.scanner
  (:import [java.io File])
  (:require [clojure.core.async :as async :refer :all]))

(defn drain []
  (let [channel (chan)]
    (go
     (loop [message (<! channel)]
       (println message)
       (if-not (identical? message :stop) (recur (<! channel)))))
    channel))

(defn scan [base-directory filename-channel]
  (go
   (loop [directories [(File. base-directory)]]
     (let [{subdirs true files false}
           (group-by #(.isDirectory %) (mapcat #(.listFiles %) directories))]
       (doseq [file files] (if (.endsWith (.toLowerCase (.getName file))
                                          ".jpg")
                             (>! filename-channel (.getCanonicalPath file))))
       (if-not (empty? subdirs) (recur subdirs))))
   (>! filename-channel :stop)))
