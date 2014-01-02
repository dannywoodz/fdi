(ns fdi.scanner
  (:import [java.io File])
  (:require [clojure.core.async :as async :refer [go >!]]))

(defn scan [base-directory filename-channel]
  (go
   (loop [directories [(File. base-directory)]]
     (let [{subdirs true files false}
           (group-by #(.isDirectory %) (mapcat #(.listFiles %) directories))]
       (doseq [file files] (if (.endsWith (.toLowerCase (.getName file))
                                          ".jpg")
                             (>! filename-channel (.getCanonicalPath file))))
       (let [visible-directories (remove #(.startsWith (.getName %) ".") subdirs)]
        (if-not (empty? visible-directories) (recur visible-directories)))))
   (>! filename-channel :stop)))
