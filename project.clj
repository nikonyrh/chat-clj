(defproject chat-clj "0.0.1-SNAPSHOT"
  :description "A simple Redis-based chat applicatin"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure.java-time "0.2.2"]
                 [org.clojure/core.async "0.3.443"]
                 
                 [org.clojars.nikonyrh.utilities-clj "1.0.0"]
                 [org.clojars.nikonyrh.channels-clj  "0.2.1"]
                 [com.taoensso/carmine "2.16.0"]
                 
                 [seesaw "1.4.2" :exclusions [org.clojure/clojure]]
                 [com.github.insubstantial/substance "7.1"]]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :aot [chat-clj.core]
  :main chat-clj.core)

