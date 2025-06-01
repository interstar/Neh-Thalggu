(ns dsl-mcp-server.dsls.speak
  (:require [instaparse.core :as insta]))

(def speak-grammar
  "S = Name <SPACE> 'says' <SPACE> Message
   Name = #'[A-Za-z][A-Za-z0-9]*'
   Message = #'.+'
   SPACE = #'\\s+'")

(def parser (insta/parser speak-grammar))

(defn get-header []
  {:success true
   :haxeCode "// Interface for all speakers
interface Speaker {
    public function speak():Void;
}"})

(defn compile-to-haxe [dsl-input]
  (let [result (parser dsl-input)]
    (if (insta/failure? result)
      {:success false
       :error (insta/get-failure result)}
      (let [[_ [_ name] _ [_ message]] result
            code (format "// DSL: %s\nclass %s implements Speaker {\n    public function new() {}\n    public function speak():Void {\n        trace(\"%s\");\n    }\n}"
                         dsl-input name message)]
        {:success true
         :haxeCode code})))) 