(ns dsl-mcp-server.web
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer [defroutes GET routes POST]]
            [compojure.route :as route]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [html5 include-css]]
            [hiccup.util :refer [escape-html]]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dsl-mcp-server.registry :as registry]
            [cheshire.core :as json]
            [clj-http.client :as clj-http]
            [markdown.core :as md]
            [clojure.pprint :refer [pprint]]
            [clojure.core :refer [with-out-str]]))

(defn render-page-header [title]
  [:head
    [:title title]
    (include-css "/css/style.css")
    [:script {:src "/js/compile.js"}]])

(defn render-dsl-description [dsl-info target]
  [:div.dsl-details
    [:h2 "Description"]
    [:p (get-in dsl-info [:targets target :description])]
    [:h2 "Example"]
    [:pre.example
      (get-in dsl-info [:targets target :example])]
    [:a.back-link {:href "/"} "Back to DSL List"]])

(defn render-compile-form [dsl-name targets]
  [:div.compile-section
    [:h2 "Try it out"]
    [:form#compile-form {:action (str "/compile/" dsl-name)}
      [:div.input-group
        [:label {:for "dsl-input"} "DSL Input:"]
        [:textarea#dsl-input {:name "dsl" :rows "10" :cols "50" :placeholder "Enter your DSL code here..."}]]
      [:div.compile-controls
        [:select#target {:name "target"}
          (for [target targets]
            [:option {:value target} target])]
        [:button {:type "submit"} "Compile"]]
      [:div#output-container.output-container
        [:div.output-group
          [:label {:for "output-0"} "Compiled Output:"]
          [:pre.output-area
            [:code#output-0 {:class "language-clojure"} "Compiled output will appear here..."]]]
        [:div#additional-outputs]]
      [:div#notes-section.notes-section
        [:div#warnings.warnings {:style "display: none"}]
        [:div#notes.notes {:style "display: none"}]
        [:div#error.error {:style "display: none"}]]
      [:div.header-section
        [:h3 "Required Header"]
        [:button#get-header {:type "button"} "Get Header"]
        [:div.output-group
          [:pre.output-area
            [:code#header-output {:class "language-clojure"} "Header code will appear here..."]]]]]])

(defn render-dsl-page [registry dsl-name]
  (let [dsl-info (get-in registry [:dsls dsl-name])
        targets (keys (:targets dsl-info))
        first-target (first targets)]
    (html5
      [:html
        (render-page-header (str "DSL MCP Server - " dsl-name))
        [:body
          [:div.container
            [:h1 dsl-name]
            (render-dsl-description dsl-info first-target)
            (render-compile-form dsl-name targets)]]])))

(defn render-dsl-item [dsl-name dsl-info]
  [:div.dsl-item
    [:h3 dsl-name]
    [:p (get-in dsl-info [:targets (first (keys (:targets dsl-info))) :description])]
    [:a {:href (str "/dsl/" dsl-name)} "View Details"]])

(defn render-dsls-section [registry]
  [:div.dsl-list
    [:h2 "Available DSLs"]
    (for [[dsl-name dsl-info] (:dsls registry)]
      (render-dsl-item dsl-name dsl-info))])

(defn render-overview [registry]
  (let [overview-content (slurp "resources/overview.md")
        rendered-markdown (md/md-to-html-string overview-content)]
    (html5
      [:head
        [:title "MCP DSL Server"]
        [:link {:rel "stylesheet" :href "/css/style.css"}]]
      [:body
        [:div.container
          [:div.markdown-content
            (html rendered-markdown)]
          (render-dsls-section registry)]])))

(defn render-registry-debug [registry]
  (html5
    [:head
      [:title "Registry Debug View"]
      [:link {:rel "stylesheet" :href "/css/style.css"}]
      [:style "
        .registry-debug {
          font-family: monospace;
          white-space: pre-wrap;
          background: #f5f5f5;
          padding: 20px;
          border-radius: 5px;
          margin: 20px;
        }
        .registry-key {
          color: #2c3e50;
          font-weight: bold;
        }
        .registry-value {
          color: #27ae60;
        }
      "]]
    [:body
      [:div.container
        [:h1 "Registry Debug View"]
        [:a.back-link {:href "/"} "Back to Overview"]
        [:div.registry-debug
          [:pre
            (with-out-str (clojure.pprint/pprint registry))]]]]))

(defn handle-compile [registry dsl-name target dsl-input]
  (try
    (let [dsl-info (get-in registry [:dsls dsl-name])
          compile-fn (get-in dsl-info [:targets target :compile-fn])]
      (if compile-fn
        (let [result (compile-fn dsl-input)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (json/generate-string result)})
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string 
                {:success false 
                 :error (str "No compiler found for " dsl-name " targeting " target)})}))
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string 
              {:success false 
               :error (str "Compilation error: " (.getMessage e))})})))

(defn handle-header [registry dsl-name target]
  (try
    (let [dsl-info (get-in registry [:dsls dsl-name])
          header-fn (get-in dsl-info [:targets target :header-fn])]
      (if header-fn
        (let [result (header-fn)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (json/generate-string result)})
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string 
                {:success false 
                 :error (str "No header generator found for " dsl-name " targeting " target)})}))
    (catch Exception e
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string 
              {:success false 
               :error (str "Error generating header: " (.getMessage e))})})))

(defn create-web-routes [registry]
  (defroutes web-routes
    (GET "/" [] (render-overview registry))
    (GET "/dsl/:dsl-name" [dsl-name] (render-dsl-page registry dsl-name))
    (GET "/debug/registry" [] (render-registry-debug registry))
    (POST "/compile/:dsl-name" [dsl-name :as request]
      (let [body (json/parse-string (slurp (:body request)) true)
            target (:target body)
            dsl-input (:dsl body)]
        (handle-compile registry dsl-name target dsl-input)))
    (GET "/header/:dsl-name/:target" [dsl-name target]
      (handle-header registry dsl-name target))
    (route/resources "/")
    (route/not-found "Page not found")))

(defn start-web-server [registry port]
  (println (str "Starting web interface on port " port "..."))
  (jetty/run-jetty (create-web-routes registry) {:port port})) 