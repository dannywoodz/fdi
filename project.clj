(defproject fdi "0.1.0-SNAPSHOT"
  :description "Identifies and reports on duplicate images within a directory tree"
  :license {:name "GNU General Public License, version 3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
	;; The GC options reflect the fact that this app generates a *lot* of transient garbage.  Very little makes it into Old or Survivor spaces, though
  :jvm-opts ["-Djava.library.path=resources", "-Xms4096m", "-Xmx4096m", "-XX:+UseParallelOldGC", "-XX:-UseAdaptiveSizePolicy", "-XX:NewRatio=1", "-XX:SurvivorRatio=12"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.xerial/sqlite-jdbc "3.7.15-M1"]
                 [commons-cli/commons-cli "1.2"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [org.slf4j/slf4j-log4j12 "1.7.6"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :native-path "src/native"
  :resource-paths ["resources"]
  :main fdi.core)
