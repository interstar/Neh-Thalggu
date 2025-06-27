# wchnt DSL

The wchnt DSL is a domain-specific language for defining data schemas that compile to Haxe classes.

## Syntax

The wchnt DSL uses a simple syntax inspired by B-N Format and Haskell types. Each line defines a class and its components:

```
ClassName = Component1 Component2/altName Component3
```

- `ClassName`: The name of the class to generate
- `Component1`: A component of type `Component1` (default name: `component1`)
- `Component2/altName`: A component of type `Component2` with alternative name `altName`
- Multiple components are separated by spaces

## Examples

### Basic Usage

```
Game = PlayArea Ball Paddle/paddle1 Paddle/paddle2
PlayArea = Rect
Ball = int/x int/y int/dx int/dy int/rad
Paddle = int/x int/y
Rect = int/x int/y int/width int/height
```

This generates:

```haxe
class Game {
    public var playArea: PlayArea;
    public var ball: Ball;
    public var paddle1: Paddle;
    public var paddle2: Paddle;

    public function new(playArea: PlayArea, ball: Ball, paddle1: Paddle, paddle2: Paddle) {
        this.playArea = playArea;
        this.ball = ball;
        this.paddle1 = paddle1;
        this.paddle2 = paddle2;
    }
}

class PlayArea {
    public var rect: Rect;

    public function new(rect: Rect) {
        this.rect = rect;
    }
}

// ... etc for Ball, Paddle, Rect
```

### More Examples

```
Person = String/name int/age Address/home
Address = String/street String/city String/state
```

## Generated Code Structure

Each generated Haxe class:
- Has public fields for all components
- Has a constructor that takes all components as parameters
- Is immutable (no setters)
- Uses default naming (lowercase first letter) unless alternative name specified

## Usage

1. Define your schema using the wchnt DSL syntax
2. Use the MCP server to compile to Haxe classes
3. Include the generated classes in your Haxe project
4. Create instances by passing all required components to constructors

## Error Handling

The DSL will return an error if:
- Invalid syntax (missing '=', invalid names, etc.)
- Parse failures
- Compilation errors

## Target Language

Currently supports:
- **Haxe**: Generates immutable Haxe classes with public fields and constructors 