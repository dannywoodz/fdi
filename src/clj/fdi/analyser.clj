(ns fdi.analyser
  (:require [clojure.core.async :as async :refer [go <!! <! >!]]))

(defn- similar? [{#^bytes first-print :fingerprint fn1 :filename :as fp1}
                 {#^bytes second-print :fingerprint fn2 :filename :as fp2}]
  (fdi.FileID/isSimilar first-print second-print))

(defn- fingerprint-from-file-named [filename]
  {:filename filename
   :fingerprint (fdi.FileID/fingerprint filename)
   :id (fdi.FileID/idString filename)})

(defn- duplicates-of [ref-print all-prints]
  (loop [prints all-prints
         duplicates []]
    (if (empty? prints)
      duplicates
      (recur (rest prints)
             (if (similar? ref-print (first prints))
               (conj duplicates (first prints))
               duplicates)))))

(defn- spawn-channels [fingerprints]
  (loop [prints fingerprints
         answers []]
    (if (empty? prints)
      answers
      (recur
       (rest prints)
       (conj answers
             (go
              (let [dups (duplicates-of (first prints) (rest prints))]
                (if (empty? dups)
                  dups
                  (cons (first prints) dups)))))))))

(defn- find-duplicates [fingerprints]
  (loop [channels (spawn-channels fingerprints)
         results []]
    (if (empty? channels)
      results
      (recur
       (rest channels)
       (conj results (<!! (first channels)))))))

(defn start [analyser-channel duplicate-handler finished-channel]
  (go
   (let [prints (<! analyser-channel)
         duplicates (find-duplicates prints)]
     (doseq [dup (remove empty? duplicates)] (duplicate-handler dup))
     (>! finished-channel :stop))))
