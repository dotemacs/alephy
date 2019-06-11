(defproject alephy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [compojure "1.6.1"]
                 [ring "1.7.0"]
                 [aleph "0.4.6"]
                 [byte-streams "0.2.4"]
                 [manifold "0.1.8"]
                 [org.clojure/core.async "0.4.490"]]
  :repl-options {:init-ns alephy.core})
