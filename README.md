# Neh-Thalggu - An MCP DSL Server

## Project Overview

This project explores a new paradigm for software development that combines the power of AI-assisted coding with formal, domain-specific languages (DSLs). The core idea is that while AI can help us write code, much of what we write is still "boilerplate". We can compress this significantly by using small, focused DSLs that capture the essential aspects of what we want to express. Then the AI can delegate much of the work of creating code to dedicated compilers of these DSLs.

The project implements this vision through a Model-Context Protocol (MCP) server that provides access to various DSL compilers. These compilers transform concise DSL inputs into working code in target languages.

Typical examples of DSLs might be languages to define data-schemas. Or UI layouts. Or state-machines. Or grammars. Etc.

In a sense, the idea here is "Language Oriented Programming", which has long been an ideal in the Lisp through to Racket communities. The purpose of Neh-Thalggu is to bring this idea to the era of "vibe coding" and the modern tool stack of chat, coding assistents and MCP servers.

### Quick Start

The code is written in Clojure and managed with Leiningen

```
./go.sh
```

This will launch the Neh-Thalggu server with an MCP listener (for access by coding agents) on port 3000, and human readable web interface on port 3001

Point your browser at http://localhost:3001 to see what languages are currently available.

DSLs are provided in the "plugins" directory, and are dynamically loaded into the server when it starts. You can create your own DSL plugins by adding new files to the plugins directory 

```
myplugin/
  - dsl.clj
  - test/test_myplugin.clj
  - README.md 
```

The existing makedsl plugin helps make new DSL templates.

Clojure is a good language for writing small DSL compilers. We use Instaparse as our parser library. 

The currently installed DSLs are :

* speak : a "Hello World" type application that interprets language statements of the form `Name says greeting` and creates either Java or Python classes which say the greeting.
* ui : a simple UI DSL for expressing nested frames or boxes which can be layed out horizontally or vertically. This language currently targets Jinja2 templates and a self-written Haxe framework  (not included here and not yet released)
* makedsl : helps us bootstrap new languages. Given a name, description and Instaparse grammar, the makedsl DSL outputs the various boilerplate templates for a new DSL based on the grammar. You just need to write the generator function and adjust documentation and prompts
* goldenpond : an example of a DSL made by wrapping an existing little-language provided in a Java JAR file. In this case it's "[Goldenpond](https://github.com/interstar/golden-pond)" a music composition library. Goldenpond is included here simply as an example of using a DSL from a Java library.

### Typical Workflow.

1) the user asks the AI to help write an application and presents a specification of the data-schema for the application in the form of a snippet of DSL and indicates the target language.

2) the AI recognises that it should use the compiler available as an MCP tool, rather than trying to interpret the snippet itself.

3) so the AI makes an MCP call to the appropriate "compile" tool for the DSL, passing the snippet and desired target language.. It receives back a larger chunk of code in the target language (say Java) which contains multiple class definitions.

4) the AI ALSO calls the MCP server asking for the "header" for this DSL and target language. The header contains information which is needed by the generated code. For example the compiler might return four new class definitions from the snippet of DSL. The header will contain information such as dependencies, and example "include statements" for the appropriate dependencies. Were the target to be Python, the header might include example requirements from PyPI.

5) The AI then figures out how to slot both these class definitions, and information from the header, into the codebase it is working on with the user

6) when the AI has successfully incorporated the generated code into the code file, it sends that entire file back to an "eyeball" function on the MCP server. This is a basic linter / "sanity checker" function provided by the makers of the DSL. Its purpose is not to be a comprehensive analyst, but to check for obvious issues such as the AI failing to have incorporated the output of the compiler into the codebase. 

### On the server

For each DSL and target on the server, we provide 3 MCP tools :

- **Compile**: Transforms DSL input into target language code.
  - Returns JSON corresponding to this EDN schema
```
 (def compile-result-schema
  [:map
   [:success boolean?]
   [:code [:sequential string?]]
   [:notes string?]
   [:warning string?]
   [:error {:optional true} string?]])
```

- **Header**: Provides necessary precondition / header code for DSLs.
  - Returns JSON corresponding to this EDN schema :
```
(def header-result-schema
  [:map
   [:success boolean?]
   [:code string?]
   [:notes string?]
   [:warning string?]]) 
```

- **Eyeball**: Checks the integration of the generated code into a main file, for common issues.
  - Returns JSON corresponding to this EDN schema :
 
```(def eyeball-result-schema
  [:map
   [:status [:enum "seems ok" "issues"]]
   [:issues [:vector string?]]
   [:notes string?]])
```


### Server Structure
- `src/dsl_mcp_server/core.clj`: Main server implementation
  - Defines and initializes the DSL registry
  - Sets up two HTTP servers and routes
    - one server is a web interface for human users.
    - the other is an MCP server for AI-agent users 
  - Provides root endpoint for tool discovery


### Registry Pattern
The registry system follows a clear separation of concerns:
1. `core.clj` owns the registry state and server setup
2. `plugin-loader.clj` provides functions for loading DSLs at runtime from a plugins directory
3. `registry.clj` provides pure functions for assembling the information from the external plugin code into the registry data-structure

3. Each DSL implementation provides:
   - Compilation function
   - Header generation function
   - "Eyeball" function to run some heuristic linting on the final code generated by the AI
   - Prompts

### DSL Implementation Pattern
The DSLs are now provided by "plugins" that are read with the plugin-loader.clj file. 

They are defined in the plugins/ directory

### Example: The "Speak" DSL
The "speak" DSL demonstrates this pattern:
- Input: `"Name says Message"`
- Header: Provides the `ISpeaker` interface
- Output: Generates Haxe classes that implement `ISpeaker`

## Using the MCP Server

### For Language Models

When using this MCP server, language models should:

1. **Check Available Tools**
   - First query the root endpoint (`/`) to get available tools
   - Review tool descriptions to understand input/output formats

2. **Handle Dependencies**
   - Always check if a DSL has a header endpoint
   - Get and include header code before using compiled code
   - Ensure generated code implements required interfaces

3. **Code Generation Workflow**
   - Get header code first
   - Generate code from the DSL snippet using the compile tool
   - Combine header and generated code appropriately
   - Add any necessary "glue" code (like the main class in our example)
   - pass the final file back to the "eyeball" tool of MCP server for extra linting and diagnostics

4. **Error Handling**
   - Check `success` field in responses
   - Handle parse errors gracefully
   - Provide clear error messages to users


## Getting Started

1. Start the server:
   ```bash
   lein run -p plugins/ -m 3000 -w 3001
   ```

   (plugins/ is the plugins directory, 3000 is the port of the MCP server, 3001 is the port of the web server)

2. Test the server:
   ```bash
   curl http://localhost:3000
   ```

3. Try the speak DSL:
   ```bash
   curl http://localhost:3000/header-speak
   curl -X POST http://localhost:3000/compile-speak \
        -H "Content-Type: application/json" \
        -d '{"dsl": "TestSpeaker says Hello"}'
   ```

## Plugin System

The MCP server uses a plugin-based architecture to support multiple DSLs. This allows for runtime extensibility and easy addition of new languages.

### Plugin Structure
Each plugin is a directory containing:
```
myplugin/
  - dsl.clj
  - test/test_myplugin.clj
  - README.md 
``` 

### Benefits
- Runtime extensibility
- Clear plugin interface
- Easy to add new DSLs
- No recompilation needed
- Simple implementation pattern

