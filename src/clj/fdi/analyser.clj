(ns fdi.analyser
  (:require [clojure.core.async :as async :refer [go close! chan >!! <!! <! >!]])
  (:import [java.util.concurrent ArrayBlockingQueue]))

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

(defn- finder [prints-atom]
  (loop [[ref-print & prints] (swap! prints-atom rest)
         duplicates '()]
    (if (nil? prints)
      duplicates
      (let [dups (duplicates-of ref-print prints)]
        (recur (swap! prints-atom rest)
               (if (empty? dups)
                 duplicates
                 (cons (cons ref-print dups) duplicates)))))))

(defn start [analyser-channel duplicate-handler finished-channel]
  (go
   (let [fingerprints (atom (<! analyser-channel))
         thread-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)
         agent-pool (map (fn [_] (send (agent fingerprints) finder)) (range thread-count))]
     (apply await agent-pool)
     (doseq [dup (remove empty? (mapcat deref agent-pool))]
       (duplicate-handler dup))
     (>! finished-channel :stop))))
