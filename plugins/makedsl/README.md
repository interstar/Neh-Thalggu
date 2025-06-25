# MakeDSL

The MakeDSL is a meta-DSL (domain-specific language) for creating new DSLs. It generates the boilerplate code needed to implement a new DSL plugin for the MCP DSL Server.

## Syntax

The MakeDSL uses a simple header-based format:

```
Name: YourDSLName
Description: A brief description of what your DSL does

Grammar rules, in Instaparse EDN format go here...
```

## Structure

### Header Section
- **Name**: The name of your DSL (required)
- **Description**: A description of what your DSL does (required)

### Grammar Section
After the header, provide your grammar rules. The grammar should be in a format compatible with Instaparse.

## Examples

### Basic DSL Definition

```
Name: Calculator
Description: A simple DSL for mathematical expressions

S = Expr
Expr = Number | Add | Subtract
Add = Expr '+' Expr
Subtract = Expr '-' Expr
Number = #'[0-9]+'
```

### More Complex DSL

```
Name: ConfigParser
Description: A DSL for parsing configuration files

S = Config
Config = Section*
Section = '[' ID ']' Property*
Property = ID '=' Value
ID = #'[a-zA-Z][a-zA-Z0-9_-]*'
Value = #'[^\\n]+'
```

## Generated Output

The MakeDSL generates several files:

### 1. Main DSL Implementation (`dsl.clj`)
- Namespace declaration
- Grammar definition using Instaparse
- Compile function stub
- Plugin structure with all required functions
- Target language placeholders

### 2. Test File (`test/dsl_test.clj`)
- Basic test structure
- Plugin validation tests
- Compilation tests
- Header and eyeball function tests

### 3. Documentation (`README.md`)
- Basic DSL documentation template
- Syntax description placeholder
- Examples section
- Usage instructions
- Target language information

## Generated Code Structure

The generated DSL plugin will include:

1. **Namespace**: Proper Clojure namespace with required dependencies
2. **Grammar**: Instaparse grammar definition
3. **Compile Function**: Stub implementation for compilation
4. **Header Function**: Stub for generating required headers
5. **Eyeball Function**: Basic validation checks
6. **Plugin Structure**: Complete plugin map with all required fields

## Target Language

The generated DSLs are designed to target:
- **TARGET_LANGUAGE**: A placeholder that should be replaced with the actual target language

## Usage Workflow

1. **Define your DSL**: Write the DSL definition using the MakeDSL syntax
2. **Generate the plugin**: Use the MakeDSL compiler to generate the boilerplate
3. **Customize the implementation**: Replace the stub functions with actual logic
4. **Update target language**: Replace "TARGET_LANGUAGE" with your actual target
5. **Test your DSL**: Use the generated test file as a starting point

## Error Handling

The MakeDSL will return an error if:
- The Name field is missing or empty
- The Description field is missing or empty
- No grammar rules are provided
- The input format is malformed

## Example Generated Code

For the Calculator DSL above, it would generate:

```clojure
(ns Calculator.dsl
  (:require [instaparse.core :as insta]
            [clostache.parser :refer [render]])
  )

(def grammar
  "S = Expr
   Expr = Number | Add | Subtract
   Add = Expr '+' Expr
   Subtract = Expr '-' Expr
   Number = #'[0-9]+'")

(def parser (insta/parser grammar))

;; ... rest of generated code
```

## Next Steps

After generating a DSL:
1. Implement the actual compilation logic
2. Add proper header generation
3. Implement meaningful validation in the eyeball function
4. Replace TARGET_LANGUAGE with your actual target
5. Add comprehensive tests
6. Document your DSL's syntax and usage 