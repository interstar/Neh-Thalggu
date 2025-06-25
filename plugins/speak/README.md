# Speak DSL

The Speak DSL is a simple "Hello World" example of a domain-specific language that generates Java and Python classes that can "speak" messages.

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

This generates different code depending on the target language:

#### Java Target
```java
public class Alice implements ISpeaker {
   public Alice() {}
   public void speak() {
      System.out.println("Alice says Hello World");
   }
}
```

#### Python Target
```python
class Alice(ISpeaker):
    def __init__(self):
        pass
    
    def speak(self):
        print("Alice says Hello World")
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

### Java Classes
1. **Class Declaration**: Implements the `ISpeaker` interface
2. **Constructor**: A public constructor with the same name as the class
3. **Speak Method**: A `speak()` method that prints the message using `System.out.println()`

### Python Classes
1. **Class Declaration**: Inherits from the `ISpeaker` abstract base class
2. **Constructor**: An `__init__()` method
3. **Speak Method**: A `speak()` method that prints the message using `print()`

## Required Interfaces

### Java Interface
All generated Java classes implement the `ISpeaker` interface:

```java
public interface ISpeaker {
    void speak();
}
```

### Python Abstract Base Class
All generated Python classes inherit from the `ISpeaker` abstract base class:

```python
from abc import ABC, abstractmethod

class ISpeaker(ABC):
    @abstractmethod
    def speak(self):
        pass
```

## Usage Examples

### In Java
```java
ISpeaker speaker = new Alice();
speaker.speak(); // Outputs: "Alice says Hello World"
```

### In Python
```python
speaker = Alice()
speaker.speak()  # Outputs: "Alice says Hello World"
```

## Error Handling

The DSL will return an error if:
- The input doesn't follow the "Name says Message" pattern
- The Name contains invalid characters (only letters allowed)
- The input is empty or malformed

## Target Languages

Currently supports:
- **Java**: Generates Java classes with System.out.println output
- **Python**: Generates Python classes with print() output 