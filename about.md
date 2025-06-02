# MCP DSL Server Project

## Project Overview

This project explores a new paradigm for software development that combines the power of AI-assisted coding with formal, domain-specific languages (DSLs). The core idea is that while AI can help us write code, much of what we write is still "boilerplate" and "unnecessary complexity." We can compress this significantly by using small, focused DSLs that capture the essential aspects of what we want to express.

The project implements this vision through a Model-Context Protocol (MCP) server that provides access to various DSL compilers. These compilers transform concise DSL inputs into working code in target languages (currently Haxe, but extensible to others).

## Current Architecture

### Server Structure
- `src/dsl_mcp_server/core.clj`: Main server implementation
  - Defines and initializes the DSL registry
  - Sets up HTTP server and routes
  - Loads and manages prompt files
  - Provides root endpoint for tool discovery

- `src/dsl_mcp_server/registry.clj`: Registry management
  - Handles DSL registration and updates
  - Generates tool descriptions and routes
  - Manages prompt file mappings
  - Provides handlers for DSL compilation and headers

- `src/dsl_mcp_server/dsls/speak.clj`: Implementation of the example "speak" DSL
  - Grammar definition for the "Name says Message" syntax
  - Compiler that generates Haxe classes
  - Header generator for required interfaces/dependencies

### Registry Pattern
The registry system follows a clear separation of concerns:
1. `core.clj` owns the registry state and server setup
2. `registry.clj` provides pure functions for:
   - Generating tool descriptions and routes
   - Managing prompt file mappings
   - Handling DSL registration
3. Each DSL implementation provides:
   - Compilation function
   - Header generation function
   - Prompt file references

### DSL Implementation Pattern
Each DSL in the system follows a pattern of providing:
1. A header endpoint (`/header-{dsl-name}`) that returns:
   - Required interfaces
   - Import statements
   - Other dependencies
2. A compile endpoint (`/compile-{dsl-name}`) that:
   - Takes DSL input
   - Returns generated code
   - Ensures generated code implements required interfaces

### Example: The "Speak" DSL
The "speak" DSL demonstrates this pattern:
- Input: `"Name says Message"`
- Header: Provides the `Speaker` interface
- Output: Generates Haxe classes that implement `Speaker`

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
   - Generate individual class/component code
   - Combine header and generated code appropriately
   - Add any necessary "glue" code (like the main class in our example)

4. **Error Handling**
   - Check `success` field in responses
   - Handle parse errors gracefully
   - Provide clear error messages to users

### Example Workflow
1. Get header:
   ```bash
   curl http://localhost:3000/header-speak
   ```

2. Generate classes:
   ```bash
   curl -X POST http://localhost:3000/compile-speak \
        -H "Content-Type: application/json" \
        -d '{"dsl": "Name says Message"}'
   ```

3. Combine results in appropriate order:
   - Header code first
   - Generated classes next
   - Any additional code last

## Future Directions

1. **Additional DSLs**
   - UI layout DSLs
   - Data schema DSLs
   - Protocol definition DSLs

2. **Enhanced Features**
   - Version control for DSL definitions
   - Multiple target language support
   - DSL composition and interaction

3. **Tooling**
   - Better error reporting
   - Validation tools
   - Development environment integration

## Getting Started

1. Start the server:
   ```bash
   lein run
   ```

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

