(ns fdi.analyser
  (:require [clojure.core.async :as async :refer :all]))

(defn- similar? [{first-print :fingerprint} {second-print :fingerprint}]
  (loop [f first-print
         s second-print
         error (* 5 (Math/abs (- (count first-print) (count second-print))))]
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
   (println "Starting analyser")
   (let [prints (<! analyser-channel)
         duplicates (find-duplicates prints)]
     (doseq [dup (remove empty? duplicates)] (duplicate-handler dup))
     (>! finished-channel :stop))))







