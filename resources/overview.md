# MCP DSL Server Overview

This server provides a set of tools for working with a number of domain-specific languages (DSLs).

The overall goal is to explore a new paradigm for software development that combines the power of AI-assisted coding with formal, domain-specific languages (DSLs).

The core idea is that while AI can help us write code, much of what we write in our programming languages is still "boilerplate" that can be eliminated if we express requirements both concisely AND unambiguously, using small, focused DSLs. These can include notations for data schemas, UI layouts, wire protocols, state-machines etc.

The DSL lets us specify the requirements very precisely. And a formal compiler can free the AI from the burden of actually turning that part of the requirement into code. While ensuring correctness and consistency.

By making the compilers for these DSLs into MCP tools available to the AI, we integrate use of the DSLs seamlessly into a chat-driven collaboration between human and agentic AI programming assistant.

### Typical Workflow

As part of a chat between the user and AI coding-assistant, the user will give the AI a snippet written in one of these DSLs.

The AI sends the snippet to the appropriate "compiler" tool and will get either an error message, or a piece of code back.

If there was no error, the AI now slots this code into the larger code-base being worked on.

For example, the user may give a fragment of a data-schema DSL which the compiler can turn into a number of Java class definitions. The AI will get these definitions back from the compiler, and then integrate them into the larger program being worked on.

The AI will also check the "header" tool for the DSL and target language. This tool provides extra information about required dependencies or contexts for the code produced by the compiler.

For example, the data-schema has been compiled into Java classes, but those classes may depend on a particular libary. The header tool would note this fact and offer an example of the necessary include statements that would need to be added to the file.

Finally, the AI should send the file it has created back to the MCP server to an "eyeball" tool. This is a specific kind of linter provided by the DSL makers, which can identify potential or common issues that arise when a language model has integrated the code fragment into the main codebase. This is not a comprehensive linting of the file. But can provide some extra warnings and advice to help the smooth integration of generated code into the codebase. 


### On the server

Therefore, for each DSL on the server, we provide 3 MCP tools :

- **Compile**: Transforms DSL input into target language code.
  - Returns:
    - `success`: Boolean indicating if compilation succeeded
    - `code`: The generated code
    - `notes`: Additional information about the generated code
    - `warnings`: Any warnings about the generated code
    - `error`: Error message if compilation failed

- **Header**: Provides necessary precondition / header code for DSLs.
  - Returns:
    - `success`: Boolean indicating if header generation succeeded
    - `code`: The required header code
    - `notes`: Additional information about the header code

- **Eyeball**: Checks the integration of the generated code into a main file, for common issues.
  - Returns:
    - `status`: Either "seems ok" or "issues"
    - `issues`: List of issues found, if any
    - `notes`: Additional information about the code check


For more details, refer to the individual tool descriptions. 