---
name: awake-compose-authoring
description: >
  Author Compose-shaped UI on Awake's retained :awake:compose runtime. Use before adding or changing
  Compose UI in an editor, tool, sample, or app, and before choosing CompositionLocal, slots,
  remembered state, or a state boundary. It prevents upstream Compose APIs from being assumed here.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-08-28'
  keywords: Awake Compose, Composer, CompositionLocal, CompositionLocalProvider, slot API, state hoisting, remember, editor UI, SubcomposeLayout, zIndex, drawWithCache, nestedScroll
---

# Awake Compose Authoring

Awake Compose is a retained, frame-driven UI engine with a Compose-shaped API. It is not AndroidX
Compose: it has no compiler plugin, snapshot isolation, `@Composable`,
`rememberSaveable`, `ViewModel`, `LaunchedEffect`, or `DisposableEffect`. Do not copy an upstream
Compose snippet without translating its lifetime and state assumptions.

Read [the Compose engine README](../../awake/compose/README.md) and [the detailed design specs](../../docs/reference/compose-engine/README.md) before authoring.
Then read the focused references below when their decision is involved:

| Need | Read |
|---|---|
| Ambient UI dependency | [`03-composition-locals.md`](../../docs/reference/compose-engine/03-composition-locals.md) |
| Modifier state or a new modifier | [`02-modifier.md`](../../docs/reference/compose-engine/02-modifier.md) and [`17-modifier-parity.md`](../../docs/reference/compose-engine/17-modifier-parity.md) |
| Repeated, subcomposed, or lazy content | [`08-lazy-lists.md`](../../docs/reference/compose-engine/08-lazy-lists.md) and `SubcomposeLayout` / `BoxWithConstraints` |
| Gestures, multi-touch, or nested scroll | `Modifier.transformable`, `Modifier.nestedScroll`, [`12-gestures.md`](../../docs/reference/compose-engine/12-gestures.md) |
| Custom caching or sibling layer ordering | `Modifier.drawWithCache`, `Modifier.zIndex`, [`10-graphics-layer.md`](../../docs/reference/compose-engine/10-graphics-layer.md) |
| Decide whether a gap belongs to a component, its parent, or a distinct layout relationship | [`references/structural-spacing.md`](references/structural-spacing.md) |
| Choose fixed versus adaptive size, preview frames, Spacer, arrangement, or alignment | [`awake-ui-layout-guidance`](../awake-ui-layout-guidance/SKILL.md) |
| A deliberate API divergence | [`11-refinements.md`](../../docs/reference/compose-engine/11-refinements.md) and [`15-compose-parity.md`](../../docs/reference/compose-engine/15-compose-parity.md) |
| Before building a new draw primitive, `DrawScope` method, or modifier capability | [`15-compose-parity.md`](../../docs/reference/compose-engine/15-compose-parity.md) (what's already **Built** vs a real **Gap**), [`11-refinements.md`](../../docs/reference/compose-engine/11-refinements.md) (whether a divergence has the evidence to be legitimate), and [`10-graphics-layer.md`](../../docs/reference/compose-engine/10-graphics-layer.md) (what's a known, documented non-fixed limitation rather than an oversight) — check all three before designing something new. |

For application-root, scene-session, or `ComposeHost` work, also use `awake-app-composition`.
For Store/Contract, intents, effects, or application/editor state, also use
`awake-state-management`.
For visible application/editor/tool controls, also use `awake-shadcn-recipe-consuming`. For a
shared recipe, use `awake-shadcn-recipe-authoring`. This skill governs retained-Compose structure
and state; it does not choose design-system ownership.

## Calling convention and lifetime

Compose functions use a `Composer` context parameter:

```kotlin
context(_: Composer)
fun EditorPanel(/* ... */) { /* ... */ }
```

Every UI tree is reconciled each frame, all of it. There is no skipping and no observable state:
no `recomposeScope`, no `mutableStateOf`, no `$changed` masks, no snapshot isolation. A value that
must outlive a frame is a plain class with plain `var`s, held across passes by `remember` --
`ScrollState`, `TextFieldState` and `InteractionSource` are all that shape. `remember` retains it
in the matching retained node; it is not persisted state or an asynchronous lifecycle. App,
session, document, and provider state stay in explicit caller-owned contracts. Async work remains
outside the engine and its result is read on a later frame.

**Read state in the phase that uses it.** Composition captures a value once; layout and paint run
every frame regardless. Anything that changes between frames -- a field's text, a hover flag --
must be read at measure or draw time, not captured into a local at composition. Capturing it there
is what made typing, hover and focus all silently stop repainting; see `TextMeasurePolicy.text`
for the shape that fixes it.

Never put state in a `Modifier` link. Links are rebuilt every pass; a stateful link loses gesture
or interaction state between frames. Keep retained state on the layout node through `remember`, in
an `InteractionSource`, or in a caller-owned object as the relevant API requires.

## Dependency delivery: explicit boundary, scoped UI access

Construct services, sessions, providers, and renderer-aware resources explicitly at the application
boundary. Do not let a panel discover them through a global service container.

Use `CompositionLocalProvider` when an explicit dependency is needed by multiple descendants and
threading it through intermediate layout functions would only be prop drilling:

```kotlin
context(_: Composer)
fun EditorContent(session: EditorSession, providers: EditorProviders) {
    CompositionLocalProvider(
        LocalEditorSession provides session,
        LocalEditorProviders provides providers,
    ) {
        AwakeEditor()
    }
}
```

Use regular parameters when a dependency is local to one component or is part of that component's
public behaviour. A composition local is UI-scoped access, not ownership: construction, disposal,
tests, and non-UI contracts remain explicit. Do not use locals for transient paint state such as
alpha or transforms; those are node/modifier concerns.

## State and slots

Hoist shared state to the lowest UI ancestor that reads or changes it. Expose new reusable controls
as controlled APIs: value/state in, event callbacks out. Keep only truly private, transient UI
state in `remember` at the node that owns it.

`BasicTextField` is an existing deliberate state-object API: its caller owns a `TextFieldState`,
usually with `rememberTextFieldState`, and commits or synchronises `state.text` through the
surrounding explicit contract. Do not wrap it in a guessed AndroidX-style `value`/
`onValueChange` overload merely for API resemblance.

For fixed-height text controls, preserve Compose's line-limit distinction: single-line fields use
an unwrapped horizontal run, keep the caret in the viewport, center the authored line box within the
control, and use a line-box-sized caret; multiline fields keep their text at the top of the content
box and grow/wrap according to their line limits. Keep this behavior in `BasicTextField`, not in a
shadcn recipe-local y offset. `TextFieldAlignmentTest` is the regression oracle for text placement,
caret height, and single-line overflow.

Use slots when callers own a visible region. A single primary region is the final `content` lambda;
multiple regions use named slots; add a scoped receiver only when callers genuinely need the parent
layout scope. Do not add text/icon/loading/position parameters that duplicate a caller-owned slot.
Use a composition local instead of a slot only for deep, cross-cutting UI context.

### Where a `remember` lives, and which node claims it

`remember` stores its slots on the **nearest enclosing node** and indexes them by **call order**
within that node. A `key(...)` gives a *node* its identity; it does not move a memo's slot. The two
are separate mechanisms and a correct list needs both.

The rule that follows: **nothing may `remember` after a variable-length sequence in the same node.**
Add or remove one item and every memo after it shifts by one.

Two failure modes, and the quiet one is worse:

- Same type on both sides -- two sections' expanded flags, say -- and the state silently belongs to
  the wrong item. Nothing throws. The scene editor shipped this: adding a component swapped which
  inspector section was open.
- Different types, and it is a `ClassCastException` from the slot table, usually named after a class
  with no obvious relationship to the panel you were editing.

What to do instead:

- Give each repeated item **its own node** -- wrap its body in a `Column`/`Box` -- so its memos live
  in that node rather than in the parent's list.
- **And** key it, so an inserted sibling does not adopt the next node by position and read back its
  remembered state.
- Never make a subtree that remembers conditionally composed. An `if` around it shifts every slot
  after it on the frame the branch flips. Draw the control always and disable it, or hoist the state
  above the branch.

A conditional subtree with no `remember` in it is fine. It is the memo, not the node, that moves.

## State placement guide

| State | Owner |
|---|---|
| Selection, document, Edit/Play mode, provider progress | Explicit editor/session state outside the UI tree; provide it to UI scope when needed |
| A value shared by editor sibling panels | Lowest shared editor shell; pass controlled state/events into children |
| A field's value | Caller-owned model/state and events; `BasicTextField` receives caller-owned `TextFieldState`, never hidden field state |
| Tooltip/open-popover/temporary focus or local animation | The owning node via `remember`, if no sibling or session needs it |
| Renderer resources, preview caches, presentation | Renderer-aware host or provider, never a Compose panel |

## Before finishing

- The calling convention is `context(_: Composer)`, not an unarmed `@Composable` annotation.
- Shared dependencies are constructed explicitly and use `CompositionLocalProvider` only inside the
  UI tree where deep scope is useful.
- Shared state is hoisted; reusable controls take values and callbacks; local transient state alone
  uses `remember`.
- Slots express caller-owned content; stable `key(...)` values protect repeated/reorderable state.
- Modifier links are stateless.
- Read [`references/structural-spacing.md`](references/structural-spacing.md) before deciding
  between component padding, a uniform parent gap, and a `Spacer`.
- Visible controls are design-system recipes, not app-authored `Style` or replacement widgets.
- Read `awake-ui-performance` before adding work inside a content lambda and
  `awake-ui-verification` before claiming visual fidelity or changing snapshots.
- Component verified with `composeFrame(...) { ... }.captureImage(...)` in its desktop test suite
  to prove pure-JVM visual rendering alongside semantics assertions (`onNodeWithTag`).
- Keep Compose-facing visual components as PascalCase nouns (`ShadcnButton`, `ShadcnInput`). A
  lowercase `shadcn*` function is a deprecated compatibility shim only; it delegates to the
  PascalCase API and must not become a second implementation.
- Use named content slots for caller-owned visible regions. A convenience label/icon overload may
  delegate to the same slot path, but must not create a parallel layout or modifier-order path.
