# 06 — Focus and text input

Stage 1 needs the focus *source* (a node can be focusable, and hit-testing can move focus). Caret,
selection and IME land in Stage 3 with the `ui-headless` text-field port.

## Focus

Today focus is a single `String?` id on `UiRuntimeCoordinator`, with `requestFocus(id)` /
`isFocused(id)`. There is no traversal order, because there is no tree to derive one from.

With a tree: `Modifier.focusable()` marks a node, and traversal order is the placed order of
focusable nodes. Tab moves forward, Shift+Tab back, and a modal `Layer` bounds the ring — see
`07-overlay-layering.md`.

Focus stays *node*-identified rather than string-identified, which removes the collision failure
`mirror-map.md` documents for string ids.

## Input dispatch happens first

```kotlin
dispatchInput(input)   // against LAST frame's placed tree
reconcile(root) { … }
```

Input is dispatched before the tree is rebuilt, so a click is resolved against geometry that
actually exists. Immediate mode has to hit-test against bounds it is computing as it goes, which is
the source of the one-frame-lag class.

## Open for Stage 3

- Caret and selection model for `TextField` / `Textarea`.
- IME and soft-keyboard routing into `UiFrameOutput.effects.requestKeyboard`.
- How `UiInputOwnership.isTextInputFocused` is derived from the focused node rather than tracked
  separately — note `blocksGameplayKeys` already depends on it, so gameplay key gating must keep
  working through the change.
