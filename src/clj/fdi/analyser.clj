;; APPLICATION
;;
;; fdi - Find Duplicates Images
;;
;; FILE
;;
;; analyser.clj
;;
;; DESCRIPTION
;;
;; Reads a sequence of image fingerprints from an input channel provided to
;; the #'start and sends groupings of fingerprints identified as duplicates to
;; an output channel.
;;
;; The fingerprint bundle is a hash:
;;
;; { :filename    java.lang.String,
;;   :fingerprint byte[],
;;   :id          java.lang.String }
;;
;; Duplicates are similarly sequences of fingerprints.
;;
;; Unique images are not reported at all on the output channel.
;;
;; COPYRIGHT
;;
;; Copyright (C) 2014 Daniel Woods
;;
;; LICENSE
;;
;; GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

(ns fdi.analyser
  (:require [clojure.core.async :as async :refer [go >!! <! >!]]))

(defn- similar? [{#^bytes first-print :fingerprint fn1 :filename :as fp1}
                 {#^bytes second-print :fingerprint fn2 :filename :as fp2}]
  (fdi.FileID/isSimilar first-print second-print))

(defn- fingerprint-from-file-named [filename]
  {:filename filename
   :fingerprint (fdi.FileID/fingerprint filename)
   :id (fdi.FileID/idString filename)})

(defn- duplicates-of [ref-print all-prints]
  (loop [prints all-prints
         duplicates '()]
    (if (empty? prints)
      duplicates
      (recur (rest prints)
             (if (similar? ref-print (first prints))
               (cons (first prints) duplicates)
               duplicates)))))

(defn- finder [prints-atom duplicates-channel]
  (loop [[ref-print & prints] (swap! prints-atom rest)]
    (if (nil? prints)
      :finished
      (let [dups (duplicates-of ref-print prints)]
        (if-not (empty? dups)
          (>!! duplicates-channel (cons ref-print dups)))
        (recur (swap! prints-atom rest))))))



(defn start [analyser-channel duplicates-channel]
  (go
   (let [fingerprints (atom (<! analyser-channel))
         thread-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)
         agent-pool (map (fn [_] (send (agent fingerprints) finder duplicates-channel)) (range thread-count))]
     (apply await agent-pool)
     (>! duplicates-channel :stop))))

