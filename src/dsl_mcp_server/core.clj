(ns dsl-mcp-server.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET POST routes]]
            [compojure.route :as route]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [dsl-mcp-server.registry :as registry]
            [dsl-mcp-server.plugin-loader :as plugin-loader]
            [clojure.string :as string]
            [dsl-mcp-server.web :as web]
            [clojure.tools.cli :refer [parse-opts]]
            ))

(declare DSLregistry)

;; Load prompts from files
(defn load-prompt [filename]
  (let [resource (io/resource (str "prompts/" filename))]
    (if resource
      (-> resource
          slurp
          (json/parse-string true))
      (throw (ex-info (str "Prompt file not found: " filename)
                     {:filename filename})))))

;; Handler for listing available prompts
(defn list-prompts-handler [req]
  (let [prompts (for [[dsl-name dsl-info] (:dsls DSLregistry)
                     [target target-info] (:targets dsl-info)
                     [prompt-type prompt-content] (:prompts target-info)]
                 [(str prompt-type "-" dsl-name "-" target) prompt-content])]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string (into {} prompts))}))

;; Debug endpoint to inspect the DSLregistry's prompts
(defn debug-prompts-handler [req]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string 
          (for [[dsl-name dsl-info] (:dsls DSLregistry)
                [target target-info] (:targets dsl-info)]
            {:dsl dsl-name
             :target target
             :prompts (:prompts target-info)}))})

;; Define the DSL registry by loading plugins
(def DSLregistry
  (let [plugins-dir (or (System/getenv "MCP_PLUGINS_DIR") "plugins")]
    (println "Loading DSL plugins from:" plugins-dir)
    (let [reg (plugin-loader/load-plugins plugins-dir)]
      (println "DEBUG: DSLregistry after loading plugins:\n" (with-out-str (clojure.pprint/pprint reg)))
      reg)))

;; MCP endpoint routes
(defroutes app-routes
  ;; MCP prompt endpoints
  (GET "/prompts/list" [] list-prompts-handler)
  
  ;; Debug endpoint
  (GET "/debug/prompts" [] debug-prompts-handler)
  
  ;; Prompt routes from registry
  (apply routes (registry/get-prompt-routes DSLregistry))
  
  ;; Root endpoint
  (GET "/" [] {:status 200
               :headers {"Content-Type" "application/json"}
               :body (json/generate-string (registry/get-tool-descriptions DSLregistry))})
  
  ;; Tool routes from registry
  (apply routes (registry/get-tool-routes DSLregistry))
  
  ;; Overview endpoint
  (GET "/overview" []
    (let [explanation (-> (io/resource "overview.md") slurp)
          dsls (->> DSLregistry
                    :dsls
                    (map (fn [[dsl-name dsl-info]]
                           (str "- **" dsl-name "**: " (get-in dsl-info [:targets (first (keys (:targets dsl-info))) :description]))))
                    (string/join "\n"))]
      (json/generate-string {:explanation explanation :DSLs dsls})))
  
  ;; Not found handler
  (route/not-found {:status 404 :body "Not Found"}))

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
  (->> ["DSL MCP Server"
        ""
        "Usage: java -jar dsl-mcp-server.jar [options]"
        ""
        "Options:"
        options-summary
        ""
        "Example:"
        "  java -jar dsl-mcp-server.jar -p /path/to/plugins -m 8080 -w 3000"]
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
            (println "Starting MCP server on port" mcp-port)
            (jetty/run-jetty app-routes {:port mcp-port :join? false})
            (println "Starting web server on port" web-port)
            (web/start-web-server registry web-port))
          (catch Exception e
            (println "Error starting server:" (.getMessage e))
            (System/exit 1))))))) 