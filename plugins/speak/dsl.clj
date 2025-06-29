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

(defn compile-to-java [input tag-path-fn]
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
public class {{name}} implements ISpeaker {
   public {{name}}() {}
   public void speak() {
      System.out.println(\"{{name}} says {{message}}\");
   }
}
" {:name name :message message})]
           :notes "Generated Java class implementing ISpeaker interface"
           :warning ""})))
    (catch Exception e
      {:success false
       :code [""]
       :notes "Error during compilation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn compile-to-python [input tag-path-fn]
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
class {{name}}(ISpeaker):
    def __init__(self):
        pass
    
    def speak(self):
        print(\"{{name}} says {{message}}\")
" {:name name :message message})]
           :notes "Generated Python class implementing ISpeaker interface"
           :warning ""})))
    (catch Exception e
      {:success false
       :code [""]
       :notes "Error during compilation"
       :warning "See :error"
       :error (.getMessage e)})))

(defn get-metadata []
  {:name "speak"
   :type :native
   :description "A simple DSL for generating things that say Hello"
   :version "1.0.0"
   :author "DSL MCP Team"})

(defn get-plugin [tag-path load-fns]
  (let [dslname "speak"]

    {:metadata (get-metadata)
     :grammar
     {:rules {"S" "Name 'says' Message"
              "Name" "#'[A-Za-z]+'"
              "Message" "#'[^\\n]+'"
              "<SPACE>" "#'\\s+'"}
      :start "S"}
     :targets
     {"java"
      {:description "Generate Java code for saying Hello"
       :compile-fn (fn [s] (compile-to-java s tag-path))
       :header-fn (fn []
                    {:success true
                     :code (str "public interface ISpeaker {\n"
                                "    void speak();\n"
                                "}")
                     :notes "Required interface for speaker classes"
                     :warning ""})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"implements ISpeaker" code))
                                    (conj "Class does not implement ISpeaker interface")
                                    (not (re-find #"public void speak\(\)" code))
                                    (conj "Missing speak() method")
                                    (not (re-find #"public [A-Za-z]+\(\)" code))
                                    (conj "Missing public constructor")
                                    (not (re-find #"System\.out\.println" code))
                                    (conj "Missing System.out.println statement")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required interface implementation, speak() method, constructor, and System.out.println statement"}))
       :prompts {
                 :compile "Compiles sentences of the form 'Name says Message' into Java classes with a speak() method that prints the Message.

Arguments:
- dsl: The DSL input in the format 'Name says Message' (required)

Example:
Input: Bob says Hello Teenage America
Output:
// DSL: Bob says Hello Teenage America
public class Bob implements ISpeaker {
    public Bob() {}
    public void speak() {
        System.out.println(\"Bob says Hello Teenage America\");
    }
}

Notes:
- The generated Java class will implement the ISpeaker interface
- The class will have a default constructor and a speak() method
- The speak() method will print the message using System.out.println()
- This is the Java target implementation of the speak DSL"
                 :header "Gets the required Java interface and dependencies for the speak DSL.

Example Output:
// Interface for all speakers
public interface ISpeaker {
    void speak();
}

Notes:
- This header provides the Speaker interface required by all speak DSL generated classes
- The interface defines a speak() method that returns void
- This is the Java target implementation of the speak DSL header"
                 :eyeball "Performs sanity checks on generated Java code for the speak DSL.

Checks:
- Class implements ISpeaker interface
- Class has a speak() method
- Class has a public constructor
- Method uses System.out.println for output

Example:
Input: Generated Java code
Output: Status and any issues found

Notes:
- Ensures generated code follows the required structure
- Verifies all necessary components are present
- This is the Java target implementation of the speak DSL eyeball function"
                 }
       }
      "python"
      {:description "Generate Python code for saying Hello"
       :compile-fn (fn [s] (compile-to-python s tag-path))
       :header-fn (fn []
                    {:success true
                     :code (str "from abc import ABC, abstractmethod\n\n"
                                "class ISpeaker(ABC):\n"
                                "    @abstractmethod\n"
                                "    def speak(self):\n"
                                "        pass\n")
                     :notes "Required abstract base class for speaker classes"
                     :warning ""})
       :eyeball-fn (fn [code]
                     (let [issues (cond-> []
                                    (not (re-find #"class.*ISpeaker" code))
                                    (conj "Class does not inherit from ISpeaker")
                                    (not (re-find #"def speak\(self\)" code))
                                    (conj "Missing speak() method")
                                    (not (re-find #"def __init__\(self\)" code))
                                    (conj "Missing __init__ constructor")
                                    (not (re-find #"print\(" code))
                                    (conj "Missing print statement")
                                    )]
                       {:status (if (empty? issues) "seems ok" "issues")
                        :issues issues
                        :notes "Checks for required inheritance from ISpeaker, speak() method, constructor, and print statement"}))
       :prompts {
                 :compile "Compiles sentences of the form 'Name says Message' into Python classes with a speak() method that prints the Message.

Arguments:
- dsl: The DSL input in the format 'Name says Message' (required)

Example:
Input: Bob says Hello Teenage America
Output:
# DSL: Bob says Hello Teenage America
class Bob(ISpeaker):
    def __init__(self):
        pass
    
    def speak(self):
        print(\"Bob says Hello Teenage America\")

Notes:
- The generated Python class will inherit from ISpeaker abstract base class
- The class will have an __init__ constructor and a speak() method
- The speak() method will print the message using print()
- This is the Python target implementation of the speak DSL"
                 :header "Gets the required Python abstract base class and dependencies for the speak DSL.

Example Output:
from abc import ABC, abstractmethod

class ISpeaker(ABC):
    @abstractmethod
    def speak(self):
        pass

Notes:
- This header provides the ISpeaker abstract base class required by all speak DSL generated classes
- The abstract base class defines a speak() method that must be implemented
- This is the Python target implementation of the speak DSL header"
                 :eyeball "Performs sanity checks on generated Python code for the speak DSL.

Checks:
- Class inherits from ISpeaker abstract base class
- Class has a speak() method
- Class has an __init__ constructor
- Method uses print() for output

Example:
Input: Generated Python code
Output: Status and any issues found

Notes:
- Ensures generated code follows the required structure
- Verifies all necessary components are present
- This is the Python target implementation of the speak DSL eyeball function"
                 }
       }
      }
     }))

;; Return both functions as a map - this is the last expression in the file
{:get-metadata get-metadata
 :get-plugin get-plugin}
