# Speak DSL

The Speak DSL is a simple domain-specific language for generating Haxe classes that can "speak" messages.

## Syntax

The Speak DSL follows a very simple pattern:

```
Name says Message
```

Where:
- `Name` is a word (letters only, no spaces)
- `Message` is any text (can contain spaces and punctuation)

## Examples

### Basic Usage

```
Alice says Hello World
```

This generates a Haxe class named `Alice` that implements the `ISpeaker` interface:

```haxe
class Alice implements ISpeaker {
   public function new() {}
   public function speak():Void {
      trace("Alice says Hello World");
   }
}
```

### More Examples

```
Bob says Welcome to our application
```

```
Greeter says Good morning, everyone!
```

```
Announcer says The show will begin in 5 minutes
```

## Generated Code Structure

Each generated class will have:

1. **Class Declaration**: Implements the `ISpeaker` interface
2. **Constructor**: A public `new()` method
3. **Speak Method**: A `speak()` method that prints the message using `trace()`

## Required Interface

All generated classes implement the `ISpeaker` interface:

```haxe
interface ISpeaker {
    public function speak():Void;
}
```

## Usage in Haxe

To use a generated speaker class:

```haxe
var speaker = new Alice();
speaker.speak(); // Outputs: "Alice says Hello World"
```

## Error Handling

The DSL will return an error if:
- The input doesn't follow the "Name says Message" pattern
- The Name contains invalid characters (only letters allowed)
- The input is empty or malformed

## Target Language

Currently supports:
- **Haxe**: Generates Haxe classes with trace output 