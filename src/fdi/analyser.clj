(ns fdi.analyser
  (:require [clojure.core.async :as async :refer :all]))

(defn- similar? [{first-print :fingerprint} {second-print :fingerprint}]
  (loop [f first-print
         s second-print
         error 0]
    (if (empty? f)
      true
      (let [new-error (+ error (Math/abs (- (first f) (first s))))]
        (if-not (> new-error (* 5 (count first-print)))
                (recur (rest f)
                       (rest s)
                       new-error))))))

(defn- duplicates-of [ref-print all-prints]
  (loop [prints all-prints
         duplicates []]
    (if (empty? prints)
      duplicates
      (recur (rest prints)
             (if (similar? ref-print (first prints))
               (conj duplicates (first prints))
               duplicates)))))

(defn- find-duplicates [prints duplicates-handler]
  (loop [p prints
         duplicates '()]
    (if (empty? p)
      duplicates
      (let [dups (duplicates-of (first p) (rest p))]
        (recur
         (remove #(contains? dups %) (rest p))
         (if (empty? dups)
           duplicates
           (cons dups duplicates)))))))

(defn start [fingerprint-channel duplicate-handler finished-channel]
  (go
   (println "Starting analyser")
   (loop [fingerprint (<! fingerprint-channel)
          prints {}]
     (println "Analysing" fingerprint)
     (if (identical? fingerprint :stop)
       (do
         (println "Checking for duplicates")
         (find-duplicates prints duplicate-handler)
         (println "Done checking for duplicates")
         (>! finished-channel :stop))
       (let [{:keys [fingerprint filename] :as print} fingerprint]
         (recur (<! fingerprint-channel)
                (assoc prints fingerprint print)))))))





