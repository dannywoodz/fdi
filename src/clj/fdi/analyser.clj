(ns fdi.analyser
  (:require [clojure.core.async :as async :refer [go <! >!]]))

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

(defn- duplicates-of-sub-group [agent-state fingerprints partition-size]
  (let [before (System/currentTimeMillis)]
    (loop [prints fingerprints
           n partition-size
           results '()]
      (if (or (zero? n) (empty? prints))
        (do
          (println "Agent" agent-state "handled" partition-size "prints in" (- (System/currentTimeMillis) before) "milliseconds")
          (remove empty? results))
        (let [ref-print (first prints)]
          (recur (rest prints)
                 (dec n)
                 (cons (duplicates-of ref-print (rest prints))
                       results)))))))

(defn- task-distribution [partition-count set-size]
  (let [base-sequence  (take partition-count (drop 2 ((fn rfib [a b]
                                                        (lazy-seq (cons a (rfib b (+ a b)))))
                                                      0 1)))]
    (loop [multiplier 1]
      (let [scaled-sequence (map #(* % multiplier) base-sequence)]
        (if (< (apply + scaled-sequence) set-size)
          (recur (inc multiplier))
          scaled-sequence)))))

(defn- find-duplicates [fingerprints]
  ;; Finding duplicates naiively (as per this approach) is an n^2 problem.
  ;; That time can be halved by noting that 'A is a duplicate of B' implies
  ;; that B is a duplicate of A.
  ;; The remaining problem is one of distribution.  A simple partitioning of
  ;; the search space is uneven: the candidate set for the first agent is
  ;; the entire input set of prints, but for the last agent it's some fraction
  ;; of that.
  ;; This can be slightly spread out by having varying size partitions, which
  ;; is where #'task-distribution comes in.  Early partitions are smaller than
  ;; later ones, taking into account that earlier partitions have more of a
  ;; search space to contend with.
  (let [thread-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)
        agent-pool (map #(agent %) (range thread-count))]
    (loop [agents agent-pool
           prints fingerprints
           distribution (task-distribution thread-count (count fingerprints))]
      (if (empty? agents)
        (do
          (apply await agent-pool)
          (mapcat deref agent-pool))
        (let [partition-size (first distribution)]
          (send (first agents)
                duplicates-of-sub-group
                prints
                partition-size)
          (recur (rest agents)
                 (drop partition-size prints)
                 (rest distribution)))))))

(defn start [analyser-channel duplicate-handler finished-channel]
  (go
   (let [prints (<! analyser-channel)
         duplicates (find-duplicates prints)]
     (doseq [dup (remove empty? duplicates)] (duplicate-handler dup))
     (>! finished-channel :stop))))
