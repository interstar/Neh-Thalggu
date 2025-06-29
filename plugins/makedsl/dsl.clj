(ns makedsl.dsl
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clostache.parser :refer [render]]
            [instaparse.core :as insta]))

;; Define the actual grammar for the makedsl DSL
(def makedsl-grammar
  "DSL_DEFINITION = HEADER_LINES GRAMMAR_TEXT
   HEADER_LINES = HEADER_LINE*
   HEADER_LINE = NAME_LINE | DESCRIPTION_LINE | BLANK_LINE
   NAME_LINE = 'Name:' SPACE NAME
   DESCRIPTION_LINE = 'Description:' SPACE DESCRIPTION
   NAME = #'[A-Za-z0-9_]+'
   DESCRIPTION = #'[^\\n]+'
   GRAMMAR_TEXT = #'[\\s\\S]*'
   BLANK_LINE = SPACE*
   <SPACE> = #'\\s+'")

(def makedsl-parser (insta/parser makedsl-grammar))

;; Template text constants
(def header-code
  ";; Required dependencies for DSL plugins:
;; [instaparse \"1.4.12\"]
;; [clostache \"1.0.1\"]
;;
;; Example DSL definition format:
;; Name: mydsl
;; Description: A DSL for generating database models
;; S = Entity SPACE 'has' SPACE Property
;; Entity = #'[A-Za-z]+'
;; Property = #'[A-Za-z]+'
;; <SPACE> = #'\\\\s+'")

(def foreign-header-code
  ";; Required dependencies for foreign JAR wrapper DSL plugins:
;; [instaparse \"1.4.12\"]
;; [clojure.data.json \"1.0.0\"]
;;
;; Example foreign DSL definition format:
;; Name: myforeign
;; Description: A DSL wrapper for a Java library
;; S = Command SPACE Parameter
;; Command = #'[A-Za-z]+'
;; Parameter = #'[A-Za-z0-9]+'
;; <SPACE> = #'\\\\s+'
;;
;; Note: Foreign plugins receive a load-java-class function as the second parameter
;; to get-plugin, allowing them to load classes from the JAR file.")

(def compile-prompt
  "Compiles DSL definitions into Clojure DSL plugin files.

Arguments:
- dsl: The DSL definition in the format with comments and EDN grammar (required)

Example:
Input: ;; DSL Name: mydsl
;; Description: A DSL for generating database models
{:grammar \"S = Entity SPACE 'has' SPACE Property
          Entity = #'[A-Za-z]+'
          Property = #'[A-Za-z]+'
          <SPACE> = #'\\\\s+'\"}

Output:
// Generated dsl.clj file content
// Generated test_dsl.clj file content

Notes:
- The DSL definition must start with comment lines specifying DSL name and description
- The grammar is specified in EDN format
- Returns two code strings: dsl.clj and test_dsl.clj
- This is the Clojure target implementation of the makedsl DSL")

(def foreign-compile-prompt
  "Compiles DSL definitions into foreign JAR wrapper DSL plugin files.

Arguments:
- dsl: The DSL definition in the format with comments and EDN grammar (required)

Example:
Input: ;; DSL Name: myforeign
;; Description: A DSL wrapper for a Java library
{:grammar \"S = Command SPACE Parameter
          Command = #'[A-Za-z]+'
          Parameter = #'[A-Za-z0-9]+'
          <SPACE> = #'\\\\s+'\"}

Output:
// Generated dsl.clj file content for JAR wrapper
// Generated test_dsl.clj file content
// Generated README.md file content

Notes:
- The DSL definition must start with comment lines specifying DSL name and description
- The grammar is specified in EDN format
- Returns three code strings: dsl.clj, test_dsl.clj, and README.md
- The generated dsl.clj will be a JAR wrapper template
- This is the foreign target implementation of the makedsl DSL")

(def header-prompt
  "Gets the required dependencies and format information for the makedsl DSL.

Example Output:
;; Required dependencies for DSL plugins:
;; [instaparse \"1.4.12\"]
;; [clostache \"1.0.1\"]
;; Example DSL definition format...

Notes:
- This header provides required dependencies for DSL plugin generation
- Shows the expected format for DSL definitions
- This is the Clojure target implementation of the makedsl DSL header")

(def foreign-header-prompt
  "Gets the required dependencies and format information for foreign JAR wrapper DSL plugins.

Example Output:
;; Required dependencies for foreign JAR wrapper DSL plugins:
;; [instaparse \"1.4.12\"]
;; [clojure.data.json \"1.0.0\"]
;; Example foreign DSL definition format...

Notes:
- This header provides required dependencies for foreign JAR wrapper plugin generation
- Shows the expected format for DSL definitions
- Explains the load-java-class function parameter
- This is the foreign target implementation of the makedsl DSL header")

(def eyeball-prompt
  "Performs sanity checks on generated DSL plugin code.

Checks:
- Namespace declaration present
- get-plugin function present
- TARGET_LANGUAGE placeholder present
- Required functions (compile-fn, header-fn, eyeball-fn) present

Example:
Input: Generated DSL plugin code
Output: Status and any issues found

Notes:
- Ensures generated code follows the required DSL plugin structure
- Verifies all necessary components are present
- This is the Clojure target implementation of the makedsl DSL eyeball function")

(def foreign-eyeball-prompt
  "Performs sanity checks on generated foreign JAR wrapper DSL plugin code.

Checks:
- Namespace declaration present
- get-plugin function with load-java-class parameter present
- JAR loading and class instantiation patterns present
- Required functions (compile-fn, header-fn, eyeball-fn) present
- Error handling for JAR loading present

Example:
Input: Generated foreign DSL plugin code
Output: Status and any issues found

Notes:
- Ensures generated code follows the required foreign JAR wrapper plugin structure
- Verifies JAR loading and Java class interaction patterns
- Checks for proper error handling
- This is the foreign target implementation of the makedsl DSL eyeball function")

(defn parse-grammar-rules [grammar-string]
  (let [lines (str/split-lines grammar-string)
        rules (for [line lines
                    :when (and (not (str/blank? line))
                              (not (str/starts-with? line "   ")) ; Skip indented lines
                              (str/includes? line "="))]
                (let [[rule-name rule-body] (str/split line #" = " 2)]
                  [(str/trim rule-name) (str/trim rule-body)]))]
    (into {} rules)))

(defn parse-dsl-definition [input]
  (try
    (let [lines (str/split-lines input)
          
          ;; Extract DSL name and description from header lines
          name-line (first (filter #(str/starts-with? (str/trim %) "Name:") lines))
          desc-line (first (filter #(str/starts-with? (str/trim %) "Description:") lines))
          
          dsl-name (when name-line
                     (-> name-line
                         (str/replace #".*Name:\s*" "")
                         str/trim))
          description (when desc-line
                        (-> desc-line
                            (str/replace #".*Description:\s*" "")
                            str/trim))
          
          ;; Find the start of the grammar (first line that's not a header)
          grammar-lines (->> lines
                            (drop-while #(or (str/starts-with? (str/trim %) "Name:")
                                           (str/starts-with? (str/trim %) "Description:")
                                           (str/blank? (str/trim %))))
                            (str/join "\n"))
          grammar (str/trim grammar-lines)]
      
      ;; Validate required fields
      (cond
        (or (nil? dsl-name) (str/blank? dsl-name))
        {:success false
         :error "DSL name is required (use 'Name: name')"}
        
        (or (nil? description) (str/blank? description))
        {:success false
         :error "DSL description is required (use 'Description: description')"}
        
        (or (nil? grammar) (str/blank? grammar))
        {:success false
         :error "Grammar is required (provide grammar rules after the header)"}
        
        :else
        ;; Validate the grammar text as Instaparse grammar
        (try
          (require 'instaparse.core)
          ((resolve 'instaparse.core/parser) grammar)
          {:success true
           :dsl-name dsl-name
           :description description
           :grammar grammar}
          (catch Exception e
            {:success false
             :error (str "Invalid Instaparse grammar: " (.getMessage e))}))))
    (catch Exception e
      {:success false
       :error (str "Failed to parse DSL definition: " (.getMessage e))})))

(defn load-template [template-name]
  (slurp (str "plugins/makedsl/templates/" template-name ".mustache")))

(defn generate-dsl-clj [dsl-name description grammar]
  (render (load-template "dsl.clj") 
          {:dsl-name dsl-name
           :description description
           :grammar grammar}))

(defn generate-test-clj [dsl-name]
  (render (load-template "test.clj")
          {:dsl-name dsl-name}))

(defn generate-readme-md [dsl-name description]
  (render (load-template "README.md")
          {:dsl-name dsl-name
           :description description}))

(defn generate-foreign-dsl-clj [dsl-name description grammar]
  (render (load-template "foreign_dsl.clj") 
          {:dsl-name dsl-name
           :description description
           :grammar grammar}))

(defn generate-foreign-test-clj [dsl-name]
  (render (load-template "foreign_test.clj")
          {:dsl-name dsl-name}))

(defn generate-foreign-readme-md [dsl-name description]
  (render (load-template "foreign_README.md")
          {:dsl-name dsl-name
           :description description}))

(defn compile-makedsl [input tag-path-fn]
  (try
    (let [parsed (parse-dsl-definition input)]
      (if (:success parsed)
        (let [dsl-clj (generate-dsl-clj (:dsl-name parsed) (:description parsed) (:grammar parsed))
              test-clj (generate-test-clj (:dsl-name parsed))
              readme-md (generate-readme-md (:dsl-name parsed) (:description parsed))]
          {:success true
           :code [dsl-clj test-clj readme-md]
           :notes (str "Generated DSL plugin for " (:dsl-name parsed))
           :warning "This is a template - you'll need to implement the actual compilation logic"})
        {:success false
         :code ["" "" ""]
         :notes "Failed to parse DSL definition"
         :warning "See :error"
         :error (:error parsed)}))
    (catch Exception e
      {:success false
       :code ["" "" ""]
       :notes "Error during DSL generation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn compile-foreign-makedsl [input tag-path-fn]
  (try
    (let [parsed (parse-dsl-definition input)]
      (if (:success parsed)
        (let [dsl-clj (generate-foreign-dsl-clj (:dsl-name parsed) (:description parsed) (:grammar parsed))
              test-clj (generate-foreign-test-clj (:dsl-name parsed))
              readme-md (generate-foreign-readme-md (:dsl-name parsed) (:description parsed))]
          {:success true
           :code [dsl-clj test-clj readme-md]
           :notes (str "Generated foreign JAR wrapper DSL plugin for " (:dsl-name parsed))
           :warning "This is a template - you'll need to implement the actual JAR integration logic"})
        {:success false
         :code ["" "" ""]
         :notes "Failed to parse DSL definition"
         :warning "See :error"
         :error (:error parsed)}))
    (catch Exception e
      {:success false
       :code ["" "" ""]
       :notes "Error during foreign DSL generation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn get-metadata []
  {:name "makedsl"
   :type :native
   :description "A meta-DSL for generating DSL plugins"
   :version "1.0.0"
   :author "DSL MCP Team"})

(defn get-plugin [tag-path load-fns]
  (let [dslname "makedsl"]

    {:metadata (get-metadata)
     :grammar
     {:rules (parse-grammar-rules makedsl-grammar)
      :start "DSL_DEFINITION"}
     :targets
     {"clojure"
      {:description "Generate Clojure DSL plugin files"
       :compile-fn (fn [s] (compile-makedsl s tag-path))
       :header-fn (fn []
                    {:success true
                     :code header-code
                     :notes "Required dependencies and format for DSL definitions"
                     :warning ""})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"ns " code))
                                    (conj "Missing namespace declaration")
                                    (not (re-find #"defn get-plugin" code))
                                    (conj "Missing get-plugin function")
                                    (not (re-find #"TARGET_LANGUAGE" code))
                                    (conj "Missing TARGET_LANGUAGE placeholder")
                                    (not (re-find #"compile-fn" code))
                                    (conj "Missing compile-fn")
                                    (not (re-find #"header-fn" code))
                                    (conj "Missing header-fn")
                                    (not (re-find #"eyeball-fn" code))
                                    (conj "Missing eyeball-fn")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required DSL plugin structure"}))
       :prompts {
                 :compile compile-prompt
                 :header header-prompt
                 :eyeball eyeball-prompt
                 }
       }
      "foreign"
      {:description "Generate foreign JAR wrapper DSL plugin files"
       :compile-fn (fn [s] (compile-foreign-makedsl s tag-path))
       :header-fn (fn []
                    {:success true
                     :code foreign-header-code
                     :notes "Required dependencies and format for foreign JAR wrapper DSL definitions"
                     :warning ""})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"ns " code))
                                    (conj "Missing namespace declaration")
                                    (not (re-find #"defn get-plugin.*load-java-class" code))
                                    (conj "Missing get-plugin function with load-java-class parameter")
                                    (not (re-find #"load-java-class" code))
                                    (conj "Missing JAR class loading pattern")
                                    (not (re-find #"compile-fn" code))
                                    (conj "Missing compile-fn")
                                    (not (re-find #"header-fn" code))
                                    (conj "Missing header-fn")
                                    (not (re-find #"eyeball-fn" code))
                                    (conj "Missing eyeball-fn")
                                    (not (re-find #"try.*catch" code))
                                    (conj "Missing error handling pattern")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required foreign JAR wrapper plugin structure"}))
       :prompts {
                 :compile foreign-compile-prompt
                 :header foreign-header-prompt
                 :eyeball foreign-eyeball-prompt
                 }
       }
      }
     }))

;; Return both functions as a map - this is the last expression in the file
{:get-metadata get-metadata
 :get-plugin get-plugin} 