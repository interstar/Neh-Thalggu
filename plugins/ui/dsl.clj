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
               :compile "Compiles UI layout DSL input to Jinja2 template code.

Arguments:
- dsl: The UI DSL input describing the layout structure using angle brackets for horizontal layouts, square brackets for vertical layouts, and grid layouts (required)

Example:
Input: <header [nav main] footer>
Output:
<div class=\"row\">
    <div id=\"header\">{{ header }}</div>
    <div class=\"column\">
        <div id=\"nav\">{{ nav }}</div>
        <div id=\"main\">{{ main }}</div>
    </div>
    <div id=\"footer\">{{ footer }}</div>
</div>

Notes:
- The UI DSL uses angle brackets (<>) for horizontal layouts
- Square brackets ([]) for vertical layouts
- Responsive layouts use <?> syntax
- Grid layouts use [# row1 | row2 | ...] syntax
- IDs are used as slot names in the generated Jinja2 template
- The generated HTML uses flexbox and grid layouts
- Each item becomes a div with an ID matching the item name
- Items can have type hints using the syntax: item/type
- The layout structure is preserved using appropriate CSS classes
- This is the Jinja2 target implementation of the UI DSL"
               :header "Gets the required header code for UI DSL templates targeting Jinja2.

Example Output:
<!DOCTYPE html>
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
</html>

Notes:
- This header provides the essential structural CSS required for the UI DSL layouts to function correctly
- The included CSS defines the core layout behavior (flex, grid) that makes the DSL's layout structure work
- The header does NOT include visual styling (colors, shadows, borders, etc.) - these should be added by the template designer
- The structural CSS ensures that:
  - Column layouts stack vertically with proper spacing
  - Row layouts arrange items horizontally with proper spacing
  - Grid layouts maintain proper column alignment and spacing
- When using this header, you should:
  1. Keep the structural CSS intact to maintain layout functionality
  2. Add your own visual styling to match your design requirements
  3. Ensure any additional CSS doesn't override the essential layout properties
- This separation ensures the DSL's layout intent is preserved while allowing design flexibility"
               :eyeball "Performs sanity checks on generated Jinja2 template code for the UI DSL.

Checks:
- Presence of required HTML elements (div)
- Presence of Jinja2 template variables ({{ and }})
- Presence of required attributes (class and id)
- Proper nesting of layout elements

Example:
Input: Generated Jinja2 template code
Output: Status and any issues found

Notes:
- Ensures the generated template has all required structural elements
- Verifies proper use of Jinja2 template syntax
- Checks for essential layout attributes
- This is the Jinja2 target implementation of the UI DSL eyeball function"
               }
     }
    }
   })
                      
                      
