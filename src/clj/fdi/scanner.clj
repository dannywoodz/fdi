;; APPLICATION
;;
;; fdi - Find Duplicates Images
;;
;; FILE
;;
;; scanner.clj
;;
;; DESCRIPTION
;;
;; Scans a directory tree starting at a supplied base and identifies image files.
;; Such files are written to the output channel.
;;
;; When all files have been processed, the keyword :stop is sent to the output channel
;;
;; COPYRIGHT
;;
;; Copyright (C) 2014 Daniel Woods
;;
;; LICENSE
;;
;; GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

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
