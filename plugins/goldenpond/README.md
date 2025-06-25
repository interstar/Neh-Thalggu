# GoldenPond DSL

The GoldenPond DSL is a domain-specific language for generating musical chord progressions and compositions using the GoldenPond library. You can read more about it at [https://gilbertlisterresearch.com/goldenpond.html](https://gilbertlisterresearch.com/goldenpond.html) 


## Syntax

The GoldenPond DSL follows a structured format with three main sections:

```
Root Scale BPM
ChordSequence
Channel Velocity Pattern
Channel Velocity Pattern
...
```

### Global Settings (First Line)
- **Root**: MIDI note number (e.g., 48 for C3, 60 for C4)
- **Scale**: Musical scale ("Major", "Minor", "MelodicMinor", "HarmonicMinor")
- **BPM**: Tempo in beats per minute

### Chord Sequence (Second Line)
A string describing the chord progression using GoldenPond's chord notation:
- Numbers represent chord degrees (1-7)
- Prefixes like `-` indicate inversions
- Suffixes like `i` indicate minor chords
- Special characters like `>` and `<` indicate transpositions

### Instrument Lines (Remaining Lines)
Each line defines a musical part with:
- **Channel**: MIDI channel (0-16)
- **Velocity**: Note velocity (0-127)
- **Pattern**: Rhythm pattern in one of three formats:
  - **Simple**: `5/8 c 1` (fractional notation)
  - **True**: `4%8 1 4` (percentage notation)
  - **Explicit**: `1.>. 2` (explicit rhythm notation)

## Examples

### Basic Composition

```
48 Major 120
71,76,72,75,71,76,72,75i
0 100 5/8 c 1
1 100 7/12 > 2
2 100 4/8 1 4
```

This generates a GoldenData summary showing:
- Root note C3 (48), Major scale, 120 BPM
- Chord progression: 71,76,72,75,71,76,72,75i
- Three instrument lines:
  - Channel 0: 5/8 pattern with velocity 100
  - Channel 1: 7/12 ascending pattern with velocity 100
  - Channel 2: 4/8 pattern with velocity 100

### Minor Scale Example

```
60 Minor 90
71,74,-94,73,9(5/2),72,-75,91,!m,71,74,-94,73,9(5/2),72,-75,-95,!M
0 80 1/4 c 1
1 60 8/12 > 1
2 90 4/8 1 4
```

### Complex Composition

```
72 HarmonicMinor 140
71,76,72,-75,71,76,72,-75i,77,73,76,<12,77ii,>12,71,96,74ii,75
0 100 5/8 c 1
1 80 7/12 > 2
2 90 4/8 1 4
3 70 3/8 r 1
```

## Generated Summary Structure

The DSL generates a GoldenData summary that includes:

1. **Root note and scale information** - The musical key and mode
2. **Chord sequence configuration** - The chord progression details
3. **Line patterns and instrument settings** - Each musical part configuration
4. **Generated note counts and timing information** - Statistics about the composition

### Example Generated Summary

```
GoldenData {
  root: 48 (C3)
  mode: 0 (Major)
  chordSequence: "71,76,72,75,71,76,72,75i"
  bpm: 120
  chordDuration: 4
  lines: [
    Line { pattern: "5/8 c 1", channel: 0, velocity: 100 },
    Line { pattern: "7/12 > 2", channel: 1, velocity: 100 },
    Line { pattern: "4/8 1 4", channel: 2, velocity: 100 }
  ]
  generatedNotes: 45
  totalDuration: 32 beats
}
```

## Pattern Types

### Simple Patterns
Format: `numerator/denominator type density`
- Example: `5/8 c 1` - 5 notes in 8 beats, chord type, density 1

### True Patterns
Format: `numerator%denominator type density`
- Example: `4%8 1 4` - 4 notes in 8 beats, type 1, density 4

### Explicit Patterns
Format: `rhythm density`
- Example: `1.>. 2` - Explicit rhythm pattern with density 2

## How It Works

The DSL compilation process:

1. **Parses the input** using Instaparse grammar
2. **Creates a GoldenData object** from the GoldenPond library
3. **Configures the object** with root note, scale, BPM, chord sequence
4. **Adds instrument lines** with their patterns and MIDI contexts
5. **Calls toString()** on the GoldenData object to get the summary

## Required Dependencies

The DSL requires:
- **goldenpond.jar** - The GoldenPond library containing the Haxe-generated Java classes
- **Java Runtime** - Standard Java environment for loading the JAR

## Error Handling

The DSL will return an error if:
- The input doesn't follow the required format
- Invalid scale names are used
- Channel numbers are outside the 0-16 range
- Velocity values are outside the 0-127 range
- Pattern syntax is malformed

## Target Languages

Currently supports:
- **Summary**: Generates GoldenData summary using the GoldenPond library

## About GoldenPond

GoldenPond is a musical composition library originally written in Haxe that provides:
- Chord progression generation
- Rhythm pattern processing
- MIDI note generation
- Multiple output formats (Java, Python, JavaScript, C++)

The library is designed for live-coding and algorithmic composition, making it perfect for AI-assisted music generation.

For more information, see the [GoldenPond repository](https://github.com/interstar/golden-pond). 