(defproject fdi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :resource-paths ["/home/danny/sfw/lib/jmagick-6.4.0.jar"]
  :jvm-opts ["-Djava.library.path=/home/danny/sfw/lib/"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]]
  :main fdi.core)
