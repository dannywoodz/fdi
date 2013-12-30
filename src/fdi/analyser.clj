(ns fdi.analyser
  (:require [clojure.core.async :as async :refer :all]))

(defn- similar? [[first-print _] [second-print _]]
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
           (cons (cons (:filename (second (first p))) (clojure.core/map (comp :filename second) dups)) duplicates)))))))

(defn start [fingerprint-channel duplicate-handler finished-channel]
  (go
   (println "Starting analyser")
   (loop [fingerprint (<! fingerprint-channel)
          prints {}]
     (if (identical? fingerprint :stop)
       (do
         (doseq [duplicate-set (find-duplicates prints duplicate-handler)]
				   (println "DUPLICATES:" duplicate-set))
         (>! finished-channel :stop)
         (println "Stopped analyser"))
       (let [{:keys [fingerprint filename] :as print} fingerprint]
         (recur (<! fingerprint-channel)
                (assoc prints fingerprint print)))))))





