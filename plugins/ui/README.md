# UI DSL

The UI DSL is a domain-specific language for creating HTML layouts using a concise, bracket-based syntax. It generates Jinja2 templates with proper CSS structure or Haxe code in a work-in-progress Haxe library.

## Syntax

The UI DSL uses different bracket types to define layout structures:

### Layout Types

- **Horizontal Layout**: `<item1 item2 item3>` - Items arranged horizontally
- **Vertical Layout**: `[item1 item2 item3]` - Items arranged vertically  
- **Responsive Layout**: `<?item1 item2 item3>` - Responsive horizontal layout
- **Grid Layout**: `[# r1c1 r1c2 r1c3 | r2c1 r2c2 r2c3 | r3c1 r3c2 r3c3]` - Grid with multiple rows

### Items

Items can be:
- **Simple IDs**: `header`, `nav`, `main`
- **Items with type hints**: `header/title`, `nav/menu`, `main/content`

Note that while the layout is formally declared in this UI language, the type-hints are rendered as simple comments, suitable for the AI to do something more useful with.

## Examples

### Basic Horizontal Layout

```
[header nav main footer]
```

Generates:
```html
<div class="column">
    <div id="header">{{ header }}</div>
    <div id="nav">{{ nav }}</div>
    <div id="main">{{ main }}</div>
    <div id="footer">{{ footer }}</div>
</div>```

### Nested Layouts

```
[<icon <nav1 nav2> > main footer]
```

Generates:
```html
<div class="column">
    <div class="row">
        <div id="icon">{{ icon }}</div>
        <div class="row">
            <div id="nav1">{{ nav1 }}</div>
            <div id="nav2">{{ nav2 }}</div>
        </div>
    </div>
    <div id="main">{{ main }}</div>
    <div id="footer">{{ footer }}</div>
</div>
```

### Grid Layout

```
[# a b | c d | e]
```

Generates:
```html
<div class="grid">
    <div class="grid-row">
        <div id="a">{{ a }}</div>
        <div id="b">{{ b }}</div>
    </div>
    <div class="grid-row">
        <div id="c">{{ c }}</div>
        <div id="d">{{ d }}</div>
    </div>
    <div class="grid-row">
        <div id="e">{{ e }}</div>
    </div>
</div>
```

### Items with Type Hints

```
<header/title [nav/menu main/content] footer/info>
```

The hints (`/title`, `/menu`, etc.) are included as HTML comments for documentation.

## Generated Structure

The DSL generates:

1. **HTML Structure**: Proper div elements with IDs
2. **CSS Classes**: Layout-specific classes (`.row`, `.column`, `.grid`)
3. **Jinja2 Variables**: Template variables using the item IDs
4. **Responsive Design**: Flexbox and grid layouts

## CSS Classes

- `.row`: Horizontal flexbox layout
- `.column`: Vertical flexbox layout  
- `.responsive-row`: Responsive horizontal layout
- `.grid`: CSS Grid layout
- `.grid-row`: Grid row container

## Usage in Jinja2

The generated templates can be used in Jinja2:

```html
{% extends "base.html" %}
{% block content %}
<!-- Generated UI DSL content here -->
{% endblock %}
```

## Error Handling

The DSL will return an error if:
- Brackets are not properly matched
- Invalid characters are used in IDs
- The syntax doesn't follow the defined patterns

## Target Language

Currently supports:
- **Jinja2**: Generates HTML templates with Jinja2 variables 
- **Haxe**: This is code using a library I'm currently working on. The library is still under construction so not currenly released. This Haxe output is simply for demonstration purposes.