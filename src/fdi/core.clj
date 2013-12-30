(ns fdi.core
  (:require [clojure.core.async :as async :refer :all]
            [fdi.scanner :as scanner]
            [fdi.error-reporter :as error]
            [fdi.cache-builder :as builder]
            [fdi.analyser :as analyser]))

(defn- duplicate-identified [a-vector]
  (println "Duplicate identified:" a-vector))

(defn- fingerprint-generation-failed [{filename :filename}]
  (println "Couldn't generate fingerprint for" filename))

(defn -main [& args]
  (let [base-directory (first args)
        error-channel (chan)
        filename-channel (chan)
        fingerprint-channel (chan)
        finished-channel (chan)
        directory-scanner (scanner/scan base-directory filename-channel)
        error-reporter (error/start error-channel fingerprint-generation-failed)
        cache-builder (builder/start filename-channel fingerprint-channel error-channel)
        analyser (analyser/start fingerprint-channel duplicate-identified finished-channel)]
    (<!! finished-channel)
    ;; (go
    ;;  (loop [open-channels #{error-channel filename-channel fingerprint-channel finished-channel}]
    ;;    (let [[message channel] (alts! (vec open-channels))]
    ;;      (cond
    ;;       (identical? channel error-channel)
    ;;       (println "Read" message "from the error channel")
    ;;       (identical? channel filename-channel)
    ;;       (println "Read" message "from the filename channel")
    ;;       (identical? channel fingerprint-channel)
    ;;       (println "Read" message "from the fingerprint channel")
    ;;       (identical? channel finished-channel)
    ;;       (println "Read" message "from the finished channel"))
    ;;      (if-not (empty? open-channels)
    ;;        (recur (disj open-channels channel))))))
    ))








