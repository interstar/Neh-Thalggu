(ns ui.test.ui-test
  (:require [clojure.test :refer :all]
            [neh-thalggu.plugin-loader :as loader]
            [malli.core :as m]
            [neh-thalggu.schema :as schema]
            [instaparse.core :as insta]
            [clojure.string :as string]
            ))

(def plugin-dir "plugins")
(def ui-dsl (loader/load-plugin plugin-dir "ui"))

(def compile-fn (-> ui-dsl :targets (get "jinja2") :compile-fn))
(def haxe-compile-fn (-> ui-dsl :targets (get "haxe") :compile-fn))

(deftest two-function-pattern
  (testing "UI plugin follows two-function pattern"
    (let [plugin (loader/load-plugin plugin-dir "ui")]
      (is (m/validate schema/plugin-schema plugin)))))

(deftest schema-checking
  (testing "Schema check"
    (is (m/validate schema/plugin-schema ui-dsl ))))

(defn compile-success? [compile-result] (:success compile-result))
(defn compile-failure? [compile-result] (not (:success compile-result)))

(deftest grammar-tests
  (testing "Basic horizontal layout"
    (is (compile-success? (compile-fn "<a b c>"))))
  
  (testing "Basic vertical layout"
    (is (compile-success?
         (compile-fn "[d e f]"))))
  
  (testing "Responsive layout"
    (is (compile-success?
         (compile-fn "<? g h i j>"))))
  
  (testing "Grid layout"
    (is (compile-success?
         (compile-fn "[# a b c | d e f]"))))
  
  (testing "Nested layouts"
    (is (compile-success?
         (compile-fn
          "[text1 <p1 p2 p3> [# x y z | a b <d [e f g]>] ]"))))
  
  (testing "Kebab-case IDs"
    (is (compile-success?
         (compile-fn
          "<header-section main-content footer-bar>")))
    (is (compile-success?
         (compile-fn
          "[nav-menu <user-profile settings-panel>]"))))
  
  (testing "Single item layouts"
    (testing "single item in horizontal layout"
      (is (compile-success?
           (compile-fn "<abc>"))))
    (testing "single item in vertical layout"
      (is (compile-success? (compile-fn "[abc]"))))
    (testing "single item in responsive layout"
      (is (compile-success?
           (compile-fn "<?abc>"))))
    (testing "single item in grid layout"
      (is (compile-success?
           (compile-fn "[# abc]")))))
  
  (testing "Invalid layouts"
                                        ; Missing closing bracket
    (is (compile-failure? (compile-fn "<a b")))
                                        ; Missing closing bracket
    (is (compile-failure? (compile-fn "[a b")))
                                        ; Incomplete grid
    (is (compile-failure? (compile-fn "[# a b")))
                                        ; Incomplete responsive
    (is (compile-failure? (compile-fn "<? a b"))) 
    ))

  (testing "Complex nested layout with type hints and parameters"
    (testing "Play area with scales"
      (let [dsl "<play-area/screen 
[chromatic major minor diminished pentatonic1 debussy]>"
            result (compile-fn dsl)]
        (is (not (insta/failure? result)) "Parser should accept type hint on play-area")))
    
    (testing "Grid with type hints"
      (let [dsl "[# red-speed/horizontal-slider red-instrument red-volume/horizontal-slider | green-speed/horizontal-slider green-instrument green-volume/horizontal-slider | blue-speed/horizontal-slider blue-instrument blue-volume/horizontal-slider(min=0,max=127)]"
            result (compile-fn dsl)]
        (is (not (insta/failure? result)) "Parser should accept grid with type hints")))
    
    (testing "Full nested layout"
      (let [dsl "[<play-area [chromatic major minor diminished pentatonic1 debussy]> [# red-speed/horizontal-slider red-instrument red-volume/horizontal-slider | green-speed/horizontal-slider green-instrument green-volume/horizontal-slider | blue-speed/horizontal-slider blue-instrument blue-volume/horizontal-slider(min=0,max=127)]]"
            result (compile-fn dsl)]
        (is (not (insta/failure? result)) "Parser should accept full nested layout")
        (when (insta/failure? result)
          (is false "Should not have parse failure")))))

(deftest compilation-tests
  (testing "Basic compilation"
    (let [result (compile-fn "<a b c>")]
      (is (:success result))
      (is (string? (:code result)))
      (is (= (:code result)
             "<div class=\"row\"><div id=\"a\">{{ a }}</div><div id=\"b\">{{ b }}</div><div id=\"c\">{{ c }}</div></div>"))))

  (testing "Vertical layout"
    (let [result (compile-fn "[x y]")]
      (is (:success result))
      (is (= (:code result)
             "<div class=\"column\"><div id=\"x\">{{ x }}</div><div id=\"y\">{{ y }}</div></div>"))))

  (testing "Responsive layout"
    (let [result (compile-fn "<? foo bar>")]
      (is (:success result))
      (is (= (:code result)
             "<div class=\"responsive-row\"><div id=\"foo\">{{ foo }}</div><div id=\"bar\">{{ bar }}</div></div>"))))

  (testing "Grid layout"
    (let [result (compile-fn "[# a b | c d]")]
      (is (:success result))
      (is (= (:code result)
             "<div class=\"grid\"><div class=\"grid-row\"><div id=\"a\">{{ a }}</div><div id=\"b\">{{ b }}</div></div><div class=\"grid-row\"><div id=\"c\">{{ c }}</div><div id=\"d\">{{ d }}</div></div></div>"))))

  (testing "Nested layouts"
    (let [result (compile-fn "[header <main aside> footer]")]
      (is (:success result))
      (is (= (:code result)
             "<div class=\"column\"><div id=\"header\">{{ header }}</div><div class=\"row\"><div id=\"main\">{{ main }}</div><div id=\"aside\">{{ aside }}</div></div><div id=\"footer\">{{ footer }}</div></div>"))))

  (testing "Invalid input compilation"
    (let [result (compile-fn "<a b")]
      (is (not (:success result)))
      (is (:error result)))))

(deftest haxe-grammar-tests
  (testing "Haxe: Basic horizontal layout"
    (let [result (haxe-compile-fn "<a b c>")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"class UIViewGraph" code))
      (is (re-find #"HLayout" code))))

  (testing "Haxe: Basic vertical layout"
    (let [result (haxe-compile-fn "[d e f]")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"VLayout" code))))

  (testing "Haxe: Responsive layout"
    (let [result (haxe-compile-fn "<? g h i j>")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"ResponsiveLayout" code))))

  (testing "Haxe: Grid layout"
    (let [result (haxe-compile-fn "[# a b c | d e f]")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"GridLayout" code))))

  (testing "Haxe: Nested layouts"
    (let [result (haxe-compile-fn "[text1 <p1 p2 p3> [# x y z | a b <d [e f g]>] ]")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"VLayout" code))
      (is (re-find #"HLayout" code))
      (is (re-find #"GridLayout" code)))))

(deftest haxe-invalid-layouts
  (testing "Haxe: Invalid layouts should fail"
    (is (not (:success (haxe-compile-fn "<a b"))))
    (is (not (:success (haxe-compile-fn "[a b"))))
    (is (not (:success (haxe-compile-fn "[# a b"))))
    (is (not (:success (haxe-compile-fn "<? a b"))))))

(deftest haxe-leaf-declarations
  (testing "Haxe: Leaf declarations present"
    (let [result (haxe-compile-fn "[<a [b <c1 c2 c3>]> <d [e f]>]")
          code (string/join "\n" (:code result))]
      (is (:success result))
      (is (re-find #"var box_a_\d+" code))
      (is (re-find #"var box_b_\d+" code))
      (is (re-find #"var box_c1_\d+" code))
      (is (re-find #"var box_c2_\d+" code))
      (is (re-find #"var box_c3_\d+" code))
      (is (re-find #"var box_d_\d+" code))
      (is (re-find #"var box_e_\d+" code))
      (is (re-find #"var box_f_\d+" code)))))

(deftest haxe-eyeball-function
  (testing "Haxe: Eyeball function validation"
    (let [eyeball-fn (-> ui-dsl :targets (get "haxe") :eyeball-fn)
          valid-code "class UIViewGraph { private var root:IBox; public function getRoot():IBox { return root; } public function getLeaves():Map<String, FixedBox> { return leaves; } }"
          invalid-code "class SomeOtherClass { }"
          valid-result (eyeball-fn valid-code)
          invalid-result (eyeball-fn invalid-code)]
      (is (fn? eyeball-fn))
      (is (= "issues" (:status valid-result)))
      (is (= "issues" (:status invalid-result)))
      (is (vector? (:issues valid-result)))
      (is (vector? (:issues invalid-result)))))) 