(ns fdi.scanner
  (:import [java.io File])
  (:require [clojure.core.async :as async :refer [go >!]]))

(defn scan [#^String base-directory filename-channel]
  (go
   (loop [directories [(File. base-directory)]]
     (let [{subdirs true files false}
           (group-by (fn [#^File file] (.isDirectory file)) (mapcat (fn[#^File f] (.listFiles f)) directories))]
       (doseq [file files]
         (if (.endsWith (.toLowerCase (.getName #^File file))
                        ".jpg")
           (>! filename-channel (.getCanonicalPath #^File file))))
       (let [visible-directories (remove #(.startsWith (.getName #^File %) ".") subdirs)]
        (if-not (empty? visible-directories) (recur visible-directories)))))
   (>! filename-channel :stop)))
