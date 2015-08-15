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
  (:require [clojure.core.async :as async :refer [go >!! <! >!]]
            [clojure.tools.logging :as log]))

(defn- similar? [{#^bytes first-print :fingerprint fn1 :filename :as fp1}
                 {#^bytes second-print :fingerprint fn2 :filename :as fp2}
                 tolerance]
  (fdi.FDI/isSimilar first-print second-print tolerance))

(defn- fingerprint-from-file-named [filename]
  "Convenience function for testing from the REPL"
  {:filename filename
   :fingerprint (fdi.FDI/fingerprint filename)
   :id (fdi.FDI/idString filename)})

(defn- duplicates-of [ref-print tolerance all-prints]
  (loop [prints all-prints
         duplicates '()]
    (if (empty? prints)
      duplicates
      (recur (rest prints)
             (if (similar? ref-print (first prints) tolerance)
               (cons (first prints) duplicates)
               duplicates)))))

(defn- finder [prints-atom tolerance duplicates-channel]
  (loop [[ref-print & prints] (swap! prints-atom rest)]
    (if (nil? prints)
      :finished
      (let [dups (duplicates-of ref-print tolerance prints)]
        (if-not (empty? dups)
          (>!! duplicates-channel (cons ref-print dups)))
        (recur (swap! prints-atom rest))))))

(defn- first-index-where
  ([sequence predicate]
     (first-index-where sequence predicate 0 0))
  ([sequence predicate index]
     (first-index-where sequence predicate index 0))
  ([sequence predicate index default-answer]
     (cond (empty? sequence) default-answer
           (predicate (first sequence)) index
           :default (recur (rest sequence) predicate (inc index) default-answer))))

(defn- belongs-in-bucket [fingerprint bucket tolerance]
  (some #(similar? % fingerprint tolerance) bucket))

(defn- allocate-to-bucket [buckets fingerprint tolerance]
  (let [number-of-buckets (count buckets)
        bucket-size       (min (max 1 number-of-buckets)
                               (inc (int (/ number-of-buckets (-> (Runtime/getRuntime) .availableProcessors)))))
        index-finders     (map #(future (first-index-where % (fn [b] (belongs-in-bucket fingerprint b tolerance)) 0 nil))
                               (partition bucket-size buckets))]
    (loop [finders index-finders
           offset 0] ;; Each finder yields an index that's relative to its own partition, so an offset that increments by bucket-size must be maintained
      (if (empty? finders)
        (conj buckets #{fingerprint})
        (if-let [index (deref (first finders))]
          (let [real-index (+ offset index)]
            (assoc buckets real-index (conj (get buckets real-index) fingerprint)))
          (recur (rest finders)
                 (+ offset bucket-size)))))))

(defn- greater-than-1 [coll]
  (> (count coll) 1))

(defn start [fingerprint-channel duplicates-channel {:keys [agent-count tolerance]}]
  (go
    (loop [fingerprint (<! fingerprint-channel)
           buckets []] ;; buckets is a vector of sets, where each set contains 1 or more fingerprint definitions
      (if (= fingerprint :stop)
        (do
          (doseq [bucket buckets]
            (if (> (count bucket) 1)
              (>! duplicates-channel bucket)))
          (>! duplicates-channel :stop))
        (recur (<! fingerprint-channel)
               (allocate-to-bucket buckets fingerprint tolerance))))))

