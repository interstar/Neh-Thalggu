(ns dsl-mcp-server.dsls.ui-test
  (:require [clojure.test :refer :all]
            [dsl-mcp-server.dsls.ui :as ui]
            [instaparse.core :as insta]))

(deftest grammar-tests
  (testing "Basic horizontal layout"
    (is (not (insta/failure? (ui/parser "<a b c>")))))
  
  (testing "Basic vertical layout"
    (is (not (insta/failure? (ui/parser "[d e f]")))))
  
  (testing "Responsive layout"
    (is (not (insta/failure? (ui/parser "<? g h i j>")))))
  
  (testing "Grid layout"
    (is (not (insta/failure? (ui/parser "[# a b c | d e f]")))))
  
  (testing "Nested layouts"
    (is (not (insta/failure? (ui/parser "[text1 <p1 p2 p3> [# x y z | a b <d [e f g]>] ]")))))
  
  (testing "Kebab-case IDs"
    (is (not (insta/failure? (ui/parser "<header-section main-content footer-bar>"))))
    (is (not (insta/failure? (ui/parser "[nav-menu <user-profile settings-panel>]")))))
  
  (testing "Single item layouts"
    (testing "single item in horizontal layout"
      (is (not (insta/failure? (ui/parser "<abc>")))))
    (testing "single item in vertical layout"
      (is (not (insta/failure? (ui/parser "[abc]")))))
    (testing "single item in responsive layout"
      (is (not (insta/failure? (ui/parser "<?abc>")))))
    (testing "single item in grid layout"
      (is (not (insta/failure? (ui/parser "[# abc]"))))))
  
  (testing "Invalid layouts"
    (is (insta/failure? (ui/parser "<a b")))  ; Missing closing bracket
    (is (insta/failure? (ui/parser "[a b")))  ; Missing closing bracket
    (is (insta/failure? (ui/parser "[# a b"))) ; Incomplete grid
    (is (insta/failure? (ui/parser "<? a b"))) ; Incomplete responsive
    ))

(deftest compilation-tests
  (testing "Basic compilation"
    (let [result (ui/compile-to-jinja2 "<a b c>")]
      (is (:success result))
      (is (string? (:jinja2Code result)))
      (is (= (:jinja2Code result)
             "<div class=\"row\"><div id=\"a\">{{ a }}</div><div id=\"b\">{{ b }}</div><div id=\"c\">{{ c }}</div></div>"))))

  (testing "Vertical layout"
    (let [result (ui/compile-to-jinja2 "[x y]")]
      (is (:success result))
      (is (= (:jinja2Code result)
             "<div class=\"column\"><div id=\"x\">{{ x }}</div><div id=\"y\">{{ y }}</div></div>"))))

  (testing "Responsive layout"
    (let [result (ui/compile-to-jinja2 "<? foo bar>")]
      (is (:success result))
      (is (= (:jinja2Code result)
             "<div class=\"responsive-row\"><div id=\"foo\">{{ foo }}</div><div id=\"bar\">{{ bar }}</div></div>"))))

  (testing "Grid layout"
    (let [result (ui/compile-to-jinja2 "[# a b | c d]")]
      (is (:success result))
      (is (= (:jinja2Code result)
             "<div class=\"grid\"><div class=\"grid-row\"><div id=\"a\">{{ a }}</div><div id=\"b\">{{ b }}</div></div><div class=\"grid-row\"><div id=\"c\">{{ c }}</div><div id=\"d\">{{ d }}</div></div></div>"))))

  (testing "Nested layouts"
    (let [result (ui/compile-to-jinja2 "[header <main aside> footer]")]
      (is (:success result))
      (is (= (:jinja2Code result)
             "<div class=\"column\"><div id=\"header\">{{ header }}</div><div class=\"row\"><div id=\"main\">{{ main }}</div><div id=\"aside\">{{ aside }}</div></div><div id=\"footer\">{{ footer }}</div></div>"))))

  (testing "Invalid input compilation"
    (let [result (ui/compile-to-jinja2 "<a b")]
      (is (not (:success result)))
      (is (:error result))))) 