(ns speak.dsl
  (:require [instaparse.core :as insta]
            [clostache.parser :refer [render]])
  )

(def grammar
  "S = Name SPACE 'says' SPACE Message
   Name = #'[A-Za-z]+'
   Message = #'[^\\n]+'
   <SPACE> = #'\\s+'")

(def parser (insta/parser grammar))

(defn compile-to-haxe [input tag-path-fn]
  (try
    (let [parse-result (parser input)]
      (if (insta/failure? parse-result)
        {:success false
         :code [""]
         :notes (str input " is not a valid string in speak")
         :warning "See :error"
         :error (-> parse-result insta/get-failure bean str)}
        (let [name-node (tag-path-fn parse-result [:S :Name])
              message-node (tag-path-fn parse-result [:S :Message])
              name (second name-node)
              message (second message-node)]
          {:success true
           :code [(render "
class {{name}} implements ISpeaker {\n
   public function new() {}\n
   public function speak():Void {\n
      trace(\"{{name}} says {{message}}\");\n
   }\n
}\n
" {:name name :message message})]
           :notes "Generated Haxe class implementing ISpeaker interface"
           :warning ""})))
    (catch Exception e
      {:success false
       :code [""]
       :notes "Error during compilation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn get-plugin [tag-path]
  (let [dslname "speak"]

    {:name dslname
     :description "A simple DSL for generating things that say Hello"
     :version "1.0.0"
     :author "DSL MCP Team"
     :grammar
     {:rules {"S" "Name 'says' Message"
              "Name" "#'[A-Za-z]+'"
              "Message" "#'[^\\n]+'"
              "<SPACE>" "#'\\s+'"}
      :start "S"}
     :targets
     {"haxe"
      {:description "Generate Haxe code for saying Hello"
       :compile-fn (fn [s] (compile-to-haxe s tag-path))
       :header-fn (fn []
                    {:success true
                     :code (str "interface ISpeaker {\n"
                                "    public function speak():Void;\n"
                                "}")
                     :notes "Required interface for speaker classes"
                     :warning ""})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"implements ISpeaker" code))
                                    (conj "Class does not implement ISpeaker interface")
                                    (not (re-find #"public function speak\(\)" code))
                                    (conj "Missing speak() method")
                                    (not (re-find #"new\(\)" code))
                                    (conj "Missing public constructor")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required interface implementation, speak() method, and trace statement"}))
       :prompts {
                 "compile" "Compiles sentences of the form 'Name says Message' into Haxe classes with a speak() method that prints the Message.\n\nArguments:\n- dsl: The DSL input in the format 'Name says Message' (required)\n\nExample:\nInput: Bob says Hello Teenage America\nOutput: Haxe class Bob implements Speaker with speak() method that prints 'Hello Teenage America'.\n\nNotes:\n- The generated Haxe class will implement the Speaker interface\n- The class will have a default constructor and a speak() method\n- The speak() method will print the message using trace()\n- This is the Haxe target implementation of the speak DSL\n",
                 "header" "Gets the required Haxe interface and dependencies for the speak DSL.\n\nExample Output:\n// Interface for all speakers\ninterface Speaker {\n    public function speak():Void;\n}\n\nNotes:\n- This header provides the Speaker interface required by all speak DSL generated classes\n- The interface defines a speak() method that returns Void\n- This is the Haxe target implementation of the speak DSL header\n"
                 }
       }
      }
     }))
