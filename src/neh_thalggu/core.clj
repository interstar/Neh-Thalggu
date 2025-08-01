(ns neh-thalggu.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET POST routes]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [neh-thalggu.registry :as registry]
            [neh-thalggu.plugin-loader :as plugin-loader]
            [clojure.string :as string]
            [neh-thalggu.web :as web]
            [clojure.tools.cli :refer [parse-opts]]
            [neh-thalggu.schema :as schema]
            ))



;; Handler for listing available prompts
(defn list-prompts-handler [registry req]
  (let [prompts (for [[dsl-name dsl-info] (:dsls registry)
                     [target target-info] (:targets dsl-info)
                     [prompt-type prompt-content] (:prompts target-info)]
                 [(str prompt-type "-" dsl-name "-" target) prompt-content])]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (into {} prompts))}))

;; Debug endpoint to inspect the DSLregistry's prompts
(defn debug-prompts-handler [registry req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string 
          (for [[dsl-name dsl-info] (:dsls registry)
                [target target-info] (:targets dsl-info)]
            {:dsl dsl-name
             :target target
             :prompts (:prompts target-info)}))})

;; MCP endpoint routes
(defn app-routes [registry]
  (let [prompt-routes (registry/get-prompt-routes registry)
        tool-routes (registry/get-tool-routes registry)]
    (routes
     ;; MCP prompt endpoints
     (GET "/prompts/list" [] (fn [req] (list-prompts-handler registry req)))
     
     ;; Debug endpoint
     (GET "/debug/prompts" [] (fn [req] (debug-prompts-handler registry req)))
     
     ;; Prompt routes from registry
     (apply routes prompt-routes)
     
     ;; Root endpoint
     (GET "/" [] {:status 200
                  :headers {"Content-Type" "application/json"}
                  :body (json/generate-string (registry/get-tool-descriptions registry))})
     
     ;; Tool routes from registry
     (apply routes tool-routes)
     
     ;; Overview endpoint
     (GET "/overview" []
       (let [explanation (-> (io/resource "overview.md") slurp)
             dsls (->> registry
                       :dsls
                       (map (fn [[dsl-name dsl-info]]
                              (str "- **" dsl-name "**: " (get-in dsl-info [:targets (first (keys (:targets dsl-info))) :description]))))
                       (string/join "\n"))]
         (json/generate-string {:explanation explanation :DSLs dsls})))
     
     ;; Not found handler
     (route/not-found {:status 404 :body "Not Found"}))))

(def cli-options
  [["-p" "--plugin-dir DIR" "Directory containing DSL plugins"
    :default "plugins"
    :validate [#(.exists (clojure.java.io/file %)) "Plugin directory must exist"]]
   ["-m" "--mcp-port PORT" "Port for MCP server"
    :default 8080
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Port must be between 0 and 65536"]]
   ["-w" "--web-port PORT" "Port for web server"
    :default 3000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Port must be between 0 and 65536"]]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Neh Thalggu - DSL MCP Server"
        ""
        "Usage: java -jar neh-thalggu.jar [options]"
        ""
        "Options:"
        options-summary
        ""
        "Example:"
        "  java -jar neh-thalggu.jar -p /path/to/plugins -m 3000 -w 3001"]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn validate-args
  "Validate command line arguments. Either return a map indicating the program
  should exit (with a error message, and optional ok status), or a map
  indicating the action the program should take and the options provided."
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      (or (not (:plugin-dir options))
          (not (:mcp-port options))
          (not (:web-port options))) ; missing required args => exit with error
      {:exit-message "Error: All three arguments (plugin-dir, mcp-port, web-port) are required."}
      :else ; failed => exit with usage summary
      {:options options})))

(defn -main
  "Main entry point for the DSL MCP server"
  [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (do
        (println exit-message)
        (System/exit (if ok? 0 1)))
      (let [{:keys [plugin-dir mcp-port web-port]} options]
        (try
          (let [registry (plugin-loader/load-plugins plugin-dir)]
            (println "DEBUG: registry after loading plugins in -main:" (keys registry))
            (println "DEBUG: registry schema valid?" (schema/validate-registry registry))
            (when-not (schema/validate-registry registry)
              (println "DEBUG: registry schema errors:" (schema/explain-validation-error schema/registry-schema registry)))
            (println "Starting MCP server on port" mcp-port)
            (jetty/run-jetty (app-routes registry) {:port mcp-port :join? false})
            (println "Starting web server on port" web-port)
            (web/start-web-server registry web-port))
          (catch Exception e
            (println "Error starting server:" (.getMessage e))
            (System/exit 1))))))) 