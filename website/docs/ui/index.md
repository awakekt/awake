# UI design systems

Awake’s UI modules provide retained HUDs, overlays, and tool interfaces for engine applications.
The neutral UI runtime supplies layout, drawing, input, and modifier behavior; component modules
add visual policy on top.

## Component families

- `ui:shadcn` provides Shadcn-style themes, tokens, and component recipes.
- `ui:material3` provides Material 3 color and component contracts.
- `ui:headless` provides unstyled primitives for custom design systems.

Choose one component family for a screen. Keep generic behavior in the Compose/Foundation layer so
visual policy remains replaceable.
