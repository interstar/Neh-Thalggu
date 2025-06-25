(defproject dsl-mcp-server "0.1.0-SNAPSHOT"
  :description "A simple MCP-compatible DSL compilation server"
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [instaparse "1.4.12"]
                 [ring/ring-core "1.11.0"]
                 [ring/ring-jetty-adapter "1.11.0"]
                 [compojure "1.7.1"]
                 [cheshire "5.12.0"]
                 [org.clojure/data.json "2.4.0"]
                 [hiccup "1.0.5"]
                 [de.ubercode.clostache/clostache "1.4.0"]
                 [org.clojure/tools.cli "1.1.230"]
                 [clj-http "3.12.3"]
                 [markdown-clj "1.11.7"]
                 [org.clojure/test.check "1.1.1" :scope "test"]
                 [org.clojure/tools.namespace "1.4.4" :scope "test"]
                 [metosin/malli "0.14.0"]]
  :source-paths ["src/" "plugins/" "test/"]
  :main dsl-mcp-server.core
  :profiles {:uberjar {:aot :all}
             :plugin-test {:test-paths ["plugins/speak/test" "plugins/ui/test" "plugins/makedsl/test" "plugins/goldenpond/test"]}}
  :test-paths ["test" "plugins/speak/test" "plugins/ui/test" "plugins/makedsl/test" "plugins/goldenpond/test"]) 
