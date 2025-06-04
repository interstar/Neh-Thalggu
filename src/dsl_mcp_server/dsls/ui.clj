(ns dsl-mcp-server.dsls.ui
  (:require [instaparse.core :as insta]
            [clojure.string :as str]))

(def ui-grammar
  "S = Layout
   Layout = HorizontalLayout | VerticalLayout | ResponsiveLayout | GridLayout
   
   HorizontalLayout = '<' <SPACE>? Item (<SPACE> Item)* <SPACE>? '>'
   VerticalLayout = '[' <SPACE>? Item (<SPACE> Item)* <SPACE>? ']'
   ResponsiveLayout = '<?' <SPACE>? Item (<SPACE> Item)* <SPACE>? '>'
   GridLayout = '[#' <SPACE>? Row (<SPACE>? '|' <SPACE>? Row)* <SPACE>? ']'
   
   Row = Item (<SPACE> Item)*
   Item = ID('/' Hint)? | Layout
   ID = #'[a-zA-Z][a-zA-Z0-9_-]*'
   Hint = #'[^\\s>\\]\\|]+'
   <SPACE> = #'\\s+'")

(def parser (insta/parser ui-grammar))

(defn get-header []
  {:success true
   :code "<!DOCTYPE html>
<html>
<head>
    <title>{% block title %}{% endblock %}</title>
    <style>
        /* Essential structural CSS for UI DSL layouts */
        .column {
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .row {
            display: flex;
            gap: 20px;
        }
        /* Grid layout structure - essential for DSL grid layouts */
        .grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 10px;
        }
        .grid-row {
            display: contents;
        }
        /* Note: Visual styling (colors, shadows, etc.) should be added by the template designer */
    </style>
</head>
<body>
    {% block content %}{% endblock %}
</body>
</html>"
   :notes "This header provides essential structural CSS for the UI DSL layouts. The included CSS defines the core layout behavior (flex, grid) that makes the DSL's layout structure work. Visual styling should be added by the template designer."
   :warnings ""})

(defn slot-div [id hint]
  (let [jinja-id (str/replace id #"-" "_")
        comment (when hint (format "<!-- type: %s -->" hint))]
    (str (when comment (str comment "\n"))
         (format "<div id=\"%s\">{{ %s }}</div>" id jinja-id))))

(defn gen-jinja2 [tree]
  (letfn [(walk [node]
            (cond
              (string? node) node
              (vector? node)
              (let [[tag & children] node]
                (case tag
                  :S (walk (second node))
                  :Layout (walk (second node))
                  :HorizontalLayout (str "<div class=\"row\">"
                                         (apply str (map walk (filter vector? children)))
                                         "</div>")
                  :VerticalLayout (str "<div class=\"column\">"
                                      (apply str (map walk (filter vector? children)))
                                      "</div>")
                  :ResponsiveLayout (str "<div class=\"responsive-row\">"
                                         (apply str (map walk (filter vector? children)))
                                         "</div>")
                  :GridLayout (str "<div class=\"grid\">"
                                  (apply str (map walk (filter vector? children)))
                                  "</div>")
                  :Row (str "<div class=\"grid-row\">"
                            (apply str (map walk (filter vector? children)))
                            "</div>")
                  :Item (let [id-node (first (filter #(= (first %) :ID) children))
                              hint-node (first (filter #(= (first %) :Hint) children))
                              layout-node (first (filter #(= (first %) :Layout) children))]
                          (if id-node
                            (let [id (second id-node)
                                  hint (when hint-node (second hint-node))]
                              (slot-div id hint))
                            (walk layout-node)))
                  :ID (slot-div (second node) nil)
                  (apply str (map walk children))))
              :else ""))]
    (walk tree)))

(defn compile-to-jinja2 [dsl-input]
  (let [result (parser dsl-input)]
    (if (insta/failure? result)
      (let [failure (insta/get-failure result)
            line (:line failure)
            column (:column failure)
            reason (:reason failure)
            text (:text failure)
            full-text (str "Parse error at line " line ", column " column ":\n"
                          "Expected one of " reason "\n"
                          "But found: " text "\n"
                          "Context: " (subs dsl-input (max 0 (- column 20)) (min (count dsl-input) (+ column 20))))]
        {:success false
         :code ""
         :notes ""
         :warnings ""
         :error full-text})
      {:success true
       :code (gen-jinja2 result)
       :notes "The generated HTML structure uses the essential layout classes from the header. Each item's type hint (if provided) is included as an HTML comment above its div."
       :warnings ""})))

(defn eyeball-jinja2 [code]
  (let [issues (cond-> []
                 (not (str/includes? code "<div"))
                 (conj "Missing div elements")
                 (not (str/includes? code "{{"))
                 (conj "Missing Jinja2 template variables")
                 (not (str/includes? code "}}"))
                 (conj "Missing closing Jinja2 template variables")
                 (not (str/includes? code "class="))
                 (conj "Missing CSS classes")
                 (not (str/includes? code "id="))
                 (conj "Missing element IDs"))]
    {:status (if (empty? issues) "seems ok" "issues")
     :issues issues
     :notes "Checks for basic HTML structure, Jinja2 variables, and required attributes"})) 