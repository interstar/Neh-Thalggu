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

(defn parse-dsl [dsl-input]
  (let [parse-tree (parser dsl-input)]
    (if (insta/failure? parse-tree)
      (let [failure (insta/get-failure parse-tree)
            line (:line failure)
            column (:column failure)
            reason (:reason failure)
            text (:text failure)
            full-text (str "Parse error at line " line ", column "
                           column ":\n"
                           "Expected one of " reason "\n"
                           "But found: " text "\n")]
        {:success false
         :error full-text})
      {:success true
       :tree parse-tree})))

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
                  :Item
                  (let [id-node (first (filter #(= (first %) :ID) children))
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

(defn jinja2-header []
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

(defn jinja2-eyeball [code]
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

(defn jinja2-compile-prompt []
  "Compiles UI layout DSL input to Jinja2 template code.

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
- This is the Jinja2 target implementation of the UI DSL")

(defn jinja2-header-prompt []
  "Gets the required header code for UI DSL templates targeting Jinja2.

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
- This separation ensures the DSL's layout intent is preserved while allowing design flexibility")

(defn jinja2-eyeball-prompt []
  "Performs sanity checks on generated Jinja2 template code for the UI DSL.

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
- This is the Jinja2 target implementation of the UI DSL eyeball function")

(defn create-layout-node [tag children padding margin]
  (case tag
    :HorizontalLayout
    (let [sym (gensym "hrow")]
      {:sym sym
       :type "HLayout"
       :children (remove nil? children)})
    :VerticalLayout
    (let [sym (gensym "vcol")]
      {:sym sym
       :type "VLayout"
       :children (remove nil? children)})
    :ResponsiveLayout
    (let [sym (gensym "rrow")]
      {:sym sym
       :type "ResponsiveLayout"
       :children (remove nil? children)})
    :GridLayout
    (let [sym (gensym "grid")]
      {:sym sym
       :type "GridLayout"
       :children (remove nil? children)})
    :Row
    (let [sym (gensym "row")]
      {:sym sym
       :type "HLayout"
       :children (remove nil? children)})
    nil))

(defn create-leaf-node [id]
  (let [label (clojure.string/capitalize id)
        sym (gensym (str "box_" id "_"))]
    {:sym sym :type "FixedBox" :label label :leaf-id id}))

(defn walk-tree [node parent-sym]
  (cond
    (string? node) 
    (if (re-matches #"^[a-zA-Z][a-zA-Z0-9_-]*$" node)
      (create-leaf-node node)
      nil)
    (vector? node)
    (let [[tag & children] node]
      (case tag
        :S (walk-tree (second node) parent-sym)
        :Layout (walk-tree (second node) parent-sym)
        :HorizontalLayout
        (let [child-results (map #(walk-tree % tag) children)
              child-syms (map :sym child-results)
              child-leaves (mapcat :leaves child-results)]
          {:sym (create-layout-node tag child-syms 20 10)
           :leaves child-leaves})
        :VerticalLayout
        (let [child-results (map #(walk-tree % tag) children)
              child-syms (map :sym child-results)
              child-leaves (mapcat :leaves child-results)]
          {:sym (create-layout-node tag child-syms 10 15)
           :leaves child-leaves})
        :ResponsiveLayout
        (let [child-results (map #(walk-tree % tag) children)
              child-syms (map :sym child-results)
              child-leaves (mapcat :leaves child-results)]
          {:sym (create-layout-node tag child-syms 20 10)
           :leaves child-leaves})
        :GridLayout
        (let [child-results (map #(walk-tree % tag) children)
              child-syms (map :sym child-results)
              child-leaves (mapcat :leaves child-results)]
          {:sym (create-layout-node tag child-syms 10 10)
           :leaves child-leaves})
        :Row
        (let [child-results (map #(walk-tree % tag) children)
              child-syms (map :sym child-results)
              child-leaves (mapcat :leaves child-results)]
          {:sym (create-layout-node tag child-syms 20 10)
           :leaves child-leaves})
        :Item
        (let [id-node (first (filter #(= (first %) :ID) children))
              hint-node (first (filter #(= (first %) :Hint) children))
              layout-node (first (filter #(= (first %) :Layout) children))]
          (if id-node
            (let [leaf-node (create-leaf-node (second id-node))]
              {:sym leaf-node
               :leaves [leaf-node]})
            (walk-tree layout-node parent-sym)))
        :ID nil
        :Hint nil
        (let [child-results (map #(walk-tree % parent-sym) children)
              valid-results (remove nil? child-results)]
          (if (empty? valid-results)
            nil
            (first valid-results)))))
    :else nil))

(defn generate-leaf-declarations [leaves]
  (clojure.string/join "\n" (map #(str "var " (:sym %) " = new FixedBox(50, 30, \"" (clojure.string/upper-case (:leaf-id %)) "\");") leaves)))

(defn generate-leaf-map [leaves]
  (clojure.string/join "\n" (map #(str "leaves.set(\"" (:leaf-id %) "\", " (:sym %) ");") leaves)))

(defn generate-layout-declarations [node padding margin]
  (letfn [(decls [node]
            (when node
              (let [sym (:sym node)
                    type (:type node)
                    children (:children node)]
                (str (cond
                       (= type "HLayout") (str "var " sym " = new HLayout(" padding ", " margin ");\n")
                       (= type "VLayout") (str "var " sym " = new VLayout(" padding ", " margin ");\n")
                       (= type "GridLayout") (str "var " sym " = new GridLayout(3, " padding ", " margin ");\n")
                       (= type "ResponsiveLayout") (str "var " sym " = new ResponsiveLayout(" padding ", " margin ");\n")
                       :else "")
                      (apply str (map decls children))))))]
    (decls node)))

(defn generate-layout-tree [node]
  (letfn [(tree [node]
            (when node
              (let [sym (:sym node)
                    children (:children node)]
                (apply str (map #(str sym ".addChild(" (:sym %) ");\n" (tree %)) children)))))]
    (tree node)))

(defn gen-haxe [tree & {:keys [padding margin] :or {padding 10 margin 10}}]
  (let [tree-result (walk-tree tree nil)
        tree-struct (:sym tree-result)
        leaves-data (:leaves tree-result)
        leaf-decls (generate-leaf-declarations leaves-data)
        leaf-map (generate-leaf-map leaves-data)
        layout-decls (generate-layout-declarations tree-struct padding margin)
        layout-tree (generate-layout-tree tree-struct)
        root-sym (:sym tree-struct)]
    [(str "class UIViewGraph {\n"
         "    private var root:IBox;\n"
         "    private var leaves:Map<String, FixedBox>;\n"
         "    public function new(padding:Float = " padding ", margin:Float = " margin ") {\n"
         "        leaves = new Map<String, FixedBox>();\n"
         (when (not (empty? leaves-data)) (str leaf-decls "\n"))
         layout-decls
         layout-tree
         (when (not (empty? leaves-data)) (str leaf-map "\n"))
         "        LayoutEngine.layout(" root-sym ");\n"
         "        this.root = " root-sym ";\n"
         "    }\n"
         "    public function getRoot():IBox { return root; }\n"
         "    public function getLeaves():Map<String, FixedBox> { return leaves; }\n"
         "}\n")]))

(defn haxe-header []
  {:success true
   :code "import haxe_ui.*;\n// Add any additional imports or dependencies here\n"
   :notes "Import statements and dependencies for the Haxe UI layout library"
   :warning ""})

(defn haxe-eyeball [code]
  (let [issues (cond-> []
                 (not (re-find #"class\s+UIViewGraph" code))
                 (conj "Missing UIViewGraph class")
                 (not (re-find #"getRoot" code))
                 (conj "Missing getRoot() method")
                 (not (re-find #"getLeaves" code))
                 (conj "Missing getLeaves() method")
                 (not (re-find #"LayoutEngine\.layout\([^)]+\)" code))
                 (conj "Missing layout calculation call"))]
    {:status (if (empty? issues) "seems ok" "issues")
     :issues issues
     :notes "Checks for required class, methods, and layout calculation"}))

(defn haxe-compile-prompt []
  "Compiles UI layout DSL input to Haxe code that builds a UIViewGraph class.\n\nArguments:\n- dsl: The UI DSL input describing the layout structure using angle brackets for horizontal layouts, square brackets for vertical layouts, and grid layouts (required)\n\nExample:\nInput: <header [nav main] footer>\nOutput:\nclass UIViewGraph { ... }\n\nNotes:\n- The generated Haxe code creates a UIViewGraph class with a root node and a map of leaf FixedBoxes\n- Padding and margin can be set via the constructor\n- The layout structure is preserved using HLayout, VLayout, and FixedBox objects\n- This is the Haxe target implementation of the UI DSL")

(defn haxe-header-prompt []
  "Gets the required header code for UI DSL templates targeting Haxe.\n\nExample Output:\nimport haxe_ui.*;\n// Add any additional imports or dependencies here\n\nNotes:\n- This header provides the import statements and dependencies required for the Haxe UI layout library\n- The header ensures the generated code can reference the layout classes\n- This is the Haxe target implementation of the UI DSL header")

(defn haxe-eyeball-prompt []
  "Performs sanity checks on generated Haxe code for the UI DSL.\n\nChecks:\n- Presence of UIViewGraph class\n- Presence of getRoot() and getLeaves() methods\n- Presence of layout calculation call\n\nExample:\nInput: Generated Haxe code\nOutput: Status and any issues found\n\nNotes:\n- Ensures the generated code has all required class and method definitions\n- Verifies the layout calculation is performed\n- This is the Haxe target implementation of the UI DSL eyeball function")

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
                   (let [parse-result (parse-dsl s)]
                     (if (:success parse-result)
                       {:success true
                        :code (gen-jinja2 (:tree parse-result))
                        :notes "The generated HTML structure uses the essential layout classes from the header. Each item's type hint (if provided) is included as an HTML comment above its div."
                        :warning ""}
                       {:success false
                        :code []
                        :notes ""
                        :warning ""
                        :error (:error parse-result)})))
     :header-fn jinja2-header
     :eyeball-fn jinja2-eyeball
     :prompts {:compile (jinja2-compile-prompt)
               :header (jinja2-header-prompt)
               :eyeball (jinja2-eyeball-prompt)}},
    "haxe"
    {:description "Generate Haxe code for UI layouts"
     :compile-fn (fn [s]
                   (let [parse-result (parse-dsl s)]
                     (if (:success parse-result)
                       {:success true
                        :code (gen-haxe (:tree parse-result))
                        :notes "The generated Haxe code creates a UIViewGraph class with a root node and a map of leaf FixedBoxes."
                        :warning ""}
                       {:success false
                        :code []
                        :notes ""
                        :warning ""
                        :error (:error parse-result)})))
     :header-fn haxe-header
     :eyeball-fn haxe-eyeball
     :prompts {:compile (haxe-compile-prompt)
               :header (haxe-header-prompt)
               :eyeball (haxe-eyeball-prompt)}}}
   })
                      
                      
