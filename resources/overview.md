# Neh-Thalggu - An MCP DSL Server

## Project Overview

This server provides a set of tools for working with a number of domain-specific languages (DSLs).


This project explores a new paradigm for software development that combines the power of AI-assisted coding with formal, domain-specific languages (DSLs). The core idea is that while AI can help us write code, much of what we write is still "boilerplate". We can compress this significantly by using small, focused DSLs that capture the essential aspects of what we want to express. Then the AI can delegate much of the work of creating code to dedicated compilers of these DSLs.

The project implements this vision through a Model-Context Protocol (MCP) server that provides access to various DSL compilers. These compilers transform concise DSL inputs into working code in target languages.

Typical examples of DSLs might be languages to define data-schemas. Or UI layouts. Or state-machines. Or grammars. Etc.

### Example workflow.

1) the user asks the AI to help write an application and presents a specification of the data-schema for the application in the form of a snippet of DSL and indicates the target language.

2) the AI recognises that it should use the compiler available as an MCP tool, rather than trying to interpret the snippet itself.

3) so the AI makes an MCP call to the appropriate "compile" tool (according to which DSL and which target language), passing the snippet. It receives back a larger chunk of code in the target language (say Java) which contains multiple class definitions.

4) the AI ALSO calls the MCP server asking for the "header" for this DSL and target language. The header contains information which is needed by the generated code. For example the compiler might return four new class definitions from the snippet of DSL. The header will contain information such as dependencies, and example "include statements" for the appropriate dependencies. Were the target to be Python, the header might include example requirements from PyPI.

5) The AI then figures out how to slot both these class definitions, and information from the header, into the codebase it is working on with the user

6) when the AI has successfully incorporated the generated code into the code file, it sends that entire file back to an "eyeball" function on the MCP server. This is a basic linter / "sanity checker" function provided by the makers of the DSL. Its purpose is not to be a comprehensive analyst, but to check for obvious issues such as the AI failing to have incorporated the output of the compiler into the codebase. 

### On the server

Therefore, for each DSL and target on the server, we provide 3 MCP tools :

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

For more details, refer to the individual tool descriptions. 
