;; APPLICATION
;;
;; fdi - Find Duplicates Images
;;
;; FILE
;;
;; core.clj
;;
;; DESCRIPTION
;;
;; Entry point and choreographer for the Find Duplicate Images application.
;;
;; The #'scan method takes a base directory name and a function that will be
;; invoked with a list of fingerprint bundles when duplicates are identified.
;;
;; The fingerprint bundle is a hash:
;;
;; { :filename    java.lang.String,
;;   :fingerprint byte[],
;;   :id          java.lang.String }
;;
;; COPYRIGHT
;;
;; Copyright (C) 2014 Daniel Woods
;;
;; LICENSE
;;
;; GNU General Public License, version 3 (http://opensource.org/licenses/GPL-3.0)

(ns fdi.core
  (:require [clojure.core.async :as async :refer [chan <!!]]
            [clojure.java.io :as io]
            [fdi.scanner :as scanner]
            [fdi.error-reporter :as error]
            [fdi.cache-builder :as builder]
            [fdi.collator :as collator]
            [fdi.analyser :as analyser])
  (:use [clojure.tools.logging :only (error)])
  (:import [org.apache.commons.cli Options GnuParser HelpFormatter]))

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
  (error "Couldn't generate fingerprint for" filename))

(def ^{:private true} +default-options+ {:tolerance 3 :disable-cache false :cache-file "cache.sqlite" :agent-count (-> clojure.lang.Agent/pooledExecutor .getCorePoolSize)})

(defn scan
  ([base-directory duplicate-handler]
     (scan base-directory duplicate-handler +default-options+))
  ([base-directory duplicate-handler {:keys [tolerance agent-count disable-cache] :as config}]
     (let [full-config (merge +default-options+ config)
           error-channel (chan)
           filename-channel (chan)
           fingerprint-channel (chan)
           analyser-channel (chan)
           duplicates-channel (chan)
           directory-scanner (scanner/scan base-directory filename-channel)
           error-reporter (error/start error-channel fingerprint-generation-failed)
           cache-builder (builder/start filename-channel fingerprint-channel error-channel (dissoc full-config :tolerance))
           collator (collator/start fingerprint-channel analyser-channel)
           analyser (analyser/start analyser-channel duplicates-channel (dissoc full-config :disable-cache))]
       (loop [message (<!! duplicates-channel)]
         (when-not (= message :stop)
           (duplicate-handler message)
           (recur (<!! duplicates-channel)))))))

(defn- usage [options message]
  (-> (HelpFormatter.)
      (.printHelp "fdi [options] base-directory" "" options message)))

(defn -main [& args]
  (let [options (doto (Options.)
                  (.addOption "n" "no-cache" false "disable loading/updating the cache")
                  (.addOption "t" "tolerance" true "set a tolerance for identifying matches (default is 3)")
                  (.addOption "a" "agents" true "set the number of agents to use (default #CPUs+2)")
                  (.addOption "c" "cache-file" true "specify the file for loading/saving the cache"))
        command-line (-> (GnuParser.) (.parse options (.toArray (or args '()) (make-array String 0))))
        disable-cache (.hasOption command-line "n")
        cache-file (.getOptionValue command-line "c" (:cache-file +default-options+))
        tolerance (Integer/parseInt (.getOptionValue command-line "t" (str (:tolerance +default-options+))))
        agent-count (Integer/parseInt (.getOptionValue command-line "a" (str (:agent-count +default-options+))))
        base-directory (first (.getArgList command-line))]
    (if (nil? base-directory)
      (usage options "No base directory specified"))
    (if (<= agent-count 0)
      (usage options "--agent-count must be > 0"))
    (if (< tolerance 0)
      (usage options "--tolerance must be >= 0"))
    (scan base-directory
          duplicate-identified
          {:tolerance tolerance :agent-count agent-count :disable-cache disable-cache :cache-file cache-file})))
