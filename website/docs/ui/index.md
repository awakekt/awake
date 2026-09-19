# UI design systems

Awake’s UI modules provide retained HUDs, overlays, and tool interfaces for engine applications.
The neutral UI runtime supplies layout, drawing, input, and modifier behavior; component modules
add visual policy on top.

## Compose Foundation and Shadcn

Compose Foundation provides neutral layout, drawing, input, and modifier behavior. The published
Shadcn module adds themes, tokens, and component recipes on top. Keep application-specific visual
policy in the theme layer so it remains replaceable.

Add [Compose Foundation and the component family you need](../getting-started.md#ui) to your
application. The [Button reference](../guides/components/button.md) shows a compiled Shadcn usage
example and a reviewed render.
