(ns fdi.core
  (:require [clojure.core.async :as async :refer [chan <!!]]
            [fdi.scanner :as scanner]
            [fdi.error-reporter :as error]
            [fdi.cache-builder :as builder]
            [fdi.collator :as collator]
            [fdi.analyser :as analyser]))

(defn- duplicate-identified [a-vector]
  (println (clojure.string/join "\t" (map :filename (sort #(< (:size %2) (:size %1)) a-vector)))))

(defn- fingerprint-generation-failed [{filename :filename}]
  (println "Couldn't generate fingerprint for" filename))

(defn scan [base-directory duplicate-handler]
  (let [error-channel (chan)
        filename-channel (chan)
        fingerprint-channel (chan)
        analyser-channel (chan)
        finished-channel (chan)
        directory-scanner (scanner/scan base-directory filename-channel)
        error-reporter (error/start error-channel fingerprint-generation-failed)
        cache-builder (builder/start filename-channel fingerprint-channel error-channel)
        collator (collator/start fingerprint-channel analyser-channel)
        analyser (analyser/start analyser-channel duplicate-handler finished-channel)]
    (<!! finished-channel)))


(defn -main [& args]
  (scan (first args) duplicate-identified))









