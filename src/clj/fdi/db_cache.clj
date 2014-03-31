(ns fdi.db-cache
  (:require [clojure.java.jdbc :as db])
  (:import [java.sql DriverManager]
           [java.io File])
  (:use [clojure.tools.logging :only (debug)])
  (:refer-clojure :exclude [load]))

(defn load
  ([] (load "cache.sqlite"))
  ([cache-filename]
     (Class/forName "org.sqlite.JDBC")
     (let [connection (DriverManager/getConnection "jdbc:sqlite::memory:")
           create (.createStatement connection)
           restore (.createStatement connection)]
       (try
         (do
           (.executeUpdate create "CREATE TABLE IF NOT EXISTS cache(id TEXT PRIMARY KEY NOT NULL, fingerprint BLOB NOT NULL, size INTEGER NOT NULL)")
           (if (-> (File. cache-filename) .exists)
             (try
               (do
                 (debug "Restoring persisted cache from" cache-filename)
                 (.executeUpdate restore (str "restore from " cache-filename)))
               (finally (.close restore)))
             (debug "Starting with a clean cache")))
         (finally
           (.close create)))
       {:db connection
        :db-file cache-filename})))

(defn save
  ([db] (save db true))
  ([{:keys [db db-file] :as cache} close-after-save]
     (debug "Persisting cache to" db-file)
     (let [backup (.createStatement db)]
       (try
         (.executeUpdate backup (str "backup to " db-file))
         (finally (.close backup))))
     (if close-after-save (.close db))))

(defn- cache-hit [record]
  (debug "Cache HIT for" (:id record))
  record)

(defn- cache-miss [connection id genfn]
  (debug "Cache MISS for" id)
  (let [insert (.prepareStatement connection "insert into cache(id,fingerprint,size) values(?,?,?)")
        {fingerprint :fingerprint size :size :as record} (genfn)]
    (try
      (do
        (.setString insert 1 id)
        (.setBytes insert 2 fingerprint)
        (.setLong insert 3 size)
        (locking connection
          (assert (= (.executeUpdate insert) 1))))
      (finally
        (.close insert)))
    record))

(defn- lookup [connection id]
  (let [query (.prepareStatement connection "select fingerprint, size from cache where id=?")]
    (try
      (do
        (.setString query 1 id)
        (let [rs (.executeQuery query)]
          (if (.next rs)
            {:id id :fingerprint (.getBytes rs 1) :size (.getLong rs 2)})))
      (finally (.close query)))))


(defn find-if-absent-put [{connection :db :as cache} id genfn]
  (let [record (lookup connection id)]
    (if record
      (cache-hit record)
      (cache-miss connection id genfn))))
