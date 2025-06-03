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
   Item = ID | Layout
   ID = #'[a-zA-Z][a-zA-Z0-9_-]*'
   
   <SPACE> = #'\\s+'")

(def parser (insta/parser ui-grammar))

(defn get-header []
  {:success true
   :jinja2Code "<!DOCTYPE html>
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
</html>"})

(defn slot-div [id]
  (let [jinja-id (str/replace id #"-" "_")]
    (format "<div id=\"%s\">{{ %s }}</div>" id jinja-id)))

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
                  :Item (walk (second node))
                  :ID (slot-div (second node))
                  (apply str (map walk children))))
              :else ""))]
    (walk tree)))

(defn compile-to-jinja2 [dsl-input]
  (let [result (parser dsl-input)]
    (if (insta/failure? result)
      {:success false
       :error (insta/get-failure result)}
      {:success true
       :jinja2Code (gen-jinja2 result)}))) 