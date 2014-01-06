(ns fdi.core
  (:require [clojure.core.async :as async :refer [chan <!!]]
            [clojure.java.io :as io]
            [fdi.scanner :as scanner]
            [fdi.error-reporter :as error]
            [fdi.cache-builder :as builder]
            [fdi.collator :as collator]
            [fdi.analyser :as analyser]))

(defn- duplicate-report [a-duplicate-report]
  (clojure.string/join "\t" (map :filename (sort #(< (:size %2) (:size %1)) a-duplicate-report))))

(defn- duplicate-reporter-on [#^String filename]
  (let [#^java.io.Writer writer (io/writer (io/file filename))]
    (fn [report]
      (doto writer
        (.write (duplicate-report report))
        (.write "\n")
        .flush))))

(defn- duplicate-identified [a-vector]
  (println (duplicate-report a-vector)))

(defn- fingerprint-generation-failed [{filename :filename}]
  (println "Couldn't generate fingerprint for" filename))

(defn scan [base-directory duplicate-handler]
  (let [error-channel (chan)
        filename-channel (chan)
        fingerprint-channel (chan)
        analyser-channel (chan)
        duplicates-channel (chan)
        directory-scanner (scanner/scan base-directory filename-channel)
        error-reporter (error/start error-channel fingerprint-generation-failed)
        cache-builder (builder/start filename-channel fingerprint-channel error-channel)
        collator (collator/start fingerprint-channel analyser-channel)
        analyser (analyser/start analyser-channel duplicates-channel)]
    (loop [message (<!! duplicates-channel)]
      (when-not (= message :stop)
        (duplicate-handler message)
        (recur (<!! duplicates-channel))))))

(defn -main [& args]
  (scan (first args) (if (= (count args) 2)
                       (duplicate-reporter-on (second args))
                       duplicate-identified)))









