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
         duplicates '()]
    (if (empty? prints)
      (if-not (empty? duplicates)
        (cons ref-print duplicates)
        duplicates)
      (recur (rest prints)
             (if (similar? ref-print (first prints))
               (cons (first prints) duplicates)
               duplicates)))))

(defn- duplicates-of-sub-group [_agent-state fingerprints partition-size]
  (loop [prints fingerprints
         n partition-size
         results '()]
    (if (or (zero? n) (empty? prints))
      (remove empty? results)
      (let [ref-print (first prints)]
        (recur (rest prints)
               (dec n)
               (concat (duplicates-of ref-print (rest prints))
                       results))))))

(defn- find-duplicates [fingerprints]
  (let [thread-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)
        agent-pool (map #(agent %) (range thread-count))
        partition-size (int (Math/ceil (/ (count fingerprints) thread-count)))]
    (loop [agents agent-pool
           prints fingerprints]
      (if (empty? agents)
        (do
          (apply await agent-pool)
          (mapcat deref agent-pool))
        (do
          (send (first agents)
                duplicates-of-sub-group
                prints
                partition-size)
          (recur (rest agents)
                 (drop partition-size prints)))))))

(defn start [analyser-channel duplicate-handler finished-channel]
  (go
   (let [prints (<! analyser-channel)
         duplicates (find-duplicates prints)]
     (doseq [dup (remove empty? duplicates)] (duplicate-handler dup))
     (>! finished-channel :stop))))
