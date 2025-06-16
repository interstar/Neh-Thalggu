(ns ui.dsl
  (:require [instaparse.core :as insta]
            [clojure.string :as str]))

(def grammar
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

(def parser (insta/parser grammar))

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
                  :Hint (second node)
                  (apply str (map walk (filter vector? children)))))
              :else ""))]
    (walk tree)))


(defn get-plugin [tag-path]
  {:name "ui"
   :description "A DSL for generating UI layouts"
   :version "1.0.0"
   :author "DSL MCP Team"
   :grammar
   {:rules {"S" "Layout"
            "Layout" "HorizontalLayout | VerticalLayout | ResponsiveLayout | GridLayout"
            "HorizontalLayout" "'<' <SPACE>? Item (<SPACE> Item)* <SPACE>? '>'"
            "VerticalLayout" "'[' <SPACE>? Item (<SPACE> Item)* <SPACE>? ']'"
            "ResponsiveLayout" "'<?' <SPACE>? Item (<SPACE> Item)* <SPACE>? '>'"
            "GridLayout" "'[#' <SPACE>? Row (<SPACE>? '|' <SPACE>? Row)* <SPACE>? ']'"
            "Row" "Item (<SPACE> Item)*"
            "Item" "ID('/' Hint)? | Layout"
            "ID" "#'[a-zA-Z][a-zA-Z0-9_-]*'"
            "Hint" "#'[^\\s>\\]\\|]+'"
            "<SPACE>" "#'\\s+'"}
    :start "S"}
   :targets
   {"jinja2"
    {:description "Generate Jinja2 templates for UI layouts"
     :compile-fn (fn [s]
                   (let [parse-tree (parser s)]
                     (if (insta/failure? parse-tree)
                       (let [failure (insta/get-failure parse-tree)
                             line (:line failure)
                             column (:column failure)
                             reason (:reason failure)
                             text (:text failure)
                             full-text (str "Parse error at line " line ", column "
                                            column ":\n"
                                            "Expected one of " reason "\n"
                                            "But found: " text "\n"
                                            )]
                         {:success false
                          :code []
                          :notes ""
                          :warning ""
                          :error full-text})
                       {:success true
                        :code (gen-jinja2 parse-tree)
                        :notes "The generated HTML structure uses the essential layout classes from the header. Each item's type hint (if provided) is included as an HTML comment above its div."
                        :warning ""})))
     :header-fn (fn []
                  {:success true
                   :code (str "<!DOCTYPE html>\n"
                              "<html>\n"
                              "<head>\n"
                              "    <title>{% block title %}{% endblock %}</title>\n"
                              "    <style>\n"
                              "        /* Essential structural CSS for UI DSL layouts */\n"
                              "        .column {\n"
                              "            display: flex;\n"
                              "            flex-direction: column;\n"
                              "            gap: 10px;\n"
                              "        }\n"
                              "        .row {\n"
                              "            display: flex;\n"
                              "            gap: 20px;\n"
                              "        }\n"
                              "        /* Grid layout structure - essential for DSL grid layouts */\n"
                              "        .grid {\n"
                              "            display: grid;\n"
                              "            grid-template-columns: repeat(3, 1fr);\n"
                              "            gap: 10px;\n"
                              "        }\n"
                              "        .grid-row {\n"
                              "            display: contents;\n"
                              "        }\n"
                              "        /* Note: Visual styling (colors, shadows, etc.) should be added by the template designer */\n"
                              "    </style>\n"
                              "</head>\n"
                              "<body>\n"
                              "    {% block content %}{% endblock %}\n"
                              "</body>\n"
                              "</html>")
                   :notes "Base HTML template with essential CSS for UI layouts"
                   :warning ""})
     :eyeball-fn (fn [code]
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
                      :notes "Checks for required HTML elements and attributes"}))
     :prompts {
               "compile" "Compiles UI layout DSL input to Jinja2 template code.\n\nArguments:\n- dsl: The UI DSL input describing the layout structure using angle brackets for horizontal layouts, square brackets for vertical layouts, and grid layouts (required)\n\nExample:\nInput: [header <main aside> footer]\nOutput: <div class=\"column\"><div id=\"header\">{{ header }}</div><div class=\"row\"><div id=\"main\">{{ main }}</div><div id=\"aside\">{{ aside }}</div></div><div id=\"footer\">{{ footer }}</div></div>\n\nNotes:\n- The UI DSL uses angle brackets (<>) for horizontal layouts\n- Square brackets ([]) for vertical layouts\n- Responsive layouts use <?> syntax\n- Grid layouts use [# row1 | row2 | ...] syntax\n- IDs are used as slot names in the generated Jinja2 template\n- This is the Jinja2 target implementation of the UI DSL\n",
               "header" "Gets the required header code for UI DSL templates targeting Jinja2.\n\nExample Output:\n<!DOCTYPE html>\n<html>\n<head>\n    <title>{% block title %}{% endblock %}</title>\n    <style>\n        /* Essential structural CSS for UI DSL layouts */\n        .column {\n            display: flex;\n            flex-direction: column;\n            gap: 10px;\n        }\n        .row {\n            display: flex;\n            gap: 20px;\n        }\n        /* Grid layout structure - essential for DSL grid layouts */\n        .grid {\n            display: grid;\n            grid-template-columns: repeat(3, 1fr);\n            gap: 10px;\n        }\n        .grid-row {\n            display: contents;\n        }\n        /* Note: Visual styling (colors, shadows, etc.) should be added by the template designer */\n    </style>\n</head>\n<body>\n    {% block content %}{% endblock %}\n</body>\n</html>\n\nNotes:\n- This header provides the essential structural CSS required for the UI DSL layouts to function correctly\n- The included CSS defines the core layout behavior (flex, grid) that makes the DSL's layout structure work\n- The header does NOT include visual styling (colors, shadows, borders, etc.) - these should be added by the template designer\n- The structural CSS ensures that:\n  - Column layouts stack vertically with proper spacing\n  - Row layouts arrange items horizontally with proper spacing\n  - Grid layouts maintain proper column alignment and spacing\n- When using this header, you should:\n  1. Keep the structural CSS intact to maintain layout functionality\n  2. Add your own visual styling to match your design requirements\n  3. Ensure any additional CSS doesn't override the essential layout properties\n- This separation ensures the DSL's layout intent is preserved while allowing design flexibility\n"
               }
     }
    }
   })
                      
                      
