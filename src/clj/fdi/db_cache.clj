(ns fdi.db-cache
  (:require [clojure.java.jdbc :as db])
  (:import [java.sql DriverManager]
           [java.io File])
  (:refer-clojure :exclude [load]))

(defn load []
  (Class/forName "org.sqlite.JDBC")
  (let [connection (DriverManager/getConnection "jdbc:sqlite::memory:")
        create (.createStatement connection)
        restore (.createStatement connection)]
    (try
      (do
        (println "Creating cache table")
        (.executeUpdate create "CREATE TABLE IF NOT EXISTS cache(id TEXT PRIMARY KEY NOT NULL, fingerprint BLOB NOT NULL, size INTEGER NOT NULL)")

        (if (-> (File. "cache.sqlite") .exists)
          (try
            (.executeUpdate restore "restore from cache.sqlite")
            (finally (.close restore)))))
      (finally
        (.close create)))
    {:db connection}))

(defn save
  ([db] (save db true))
  ([{:keys [db] :as cache} close-after-save]
     (let [backup (.createStatement db)]
       (try
         (.executeUpdate backup "backup to cache.sqlite")
         (finally (.close backup))))
     (if close-after-save (.close db))))

(defn- cache-hit [{id :id :as record}]
  (println "Cache HIT for" id)
  record)

(defn- cache-miss [connection id genfn]
  (let [insert (.prepareStatement connection "insert into cache(id,fingerprint,size) values(?,?,?)")
        {fingerprint :fingerprint size :size :as record} (genfn)]
    (println "Cache MISS for" id)
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
