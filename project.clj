(defproject dsl-mcp-server "0.1.0-SNAPSHOT"
  :description "A simple MCP-compatible DSL compilation server"
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [instaparse "1.4.12"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [compojure "1.7.1"]
                 [cheshire "5.12.0"]
                 [org.clojure/test.check "1.1.1" :scope "test"]
                 [org.clojure/tools.namespace "1.4.4" :scope "test"]]
  :main dsl-mcp-server.core
  :profiles {:uberjar {:aot :all}}
  :test-paths ["test"]) 