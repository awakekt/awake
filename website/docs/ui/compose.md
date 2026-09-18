# Compose-shaped UI runtime

Awake Compose is a retained UI runtime with a Compose-shaped Kotlin API. It is designed to share a
frame with simulation and rendering rather than to replace the platform’s application UI toolkit.

## Runtime layers

- Runtime composition and retained state.
- Layout, modifiers, drawing, input, focus, and semantics.
- Foundation layouts, text, fields, scrolling, and gestures.
- Headless testing utilities for layout and emitted draw primitives.

UI input is reported alongside gameplay input so an application can decide how captured, focused,
and unconsumed events affect the game.

The [Design system](index.md) page explains the component families built on this runtime.
