# Widget identity, and the showcase catalog

Extends `SKILL.md`. Read before adding a stateful widget, a loop that emits widgets, or a
showcase page.

## Every stateful widget needs a real, unique `id` -- collisions are silent without the check below

`id` is the lookup key into `WidgetState` (hover/active/animation/scroll/caret state).
Two widgets that end up with the same `id` string silently share one state slot --
hovering one visually reacts on the other, one's animation glitches, clicking one can
toggle the other's checked state. This has shipped as a real bug multiple times (see
`docs/audits/2026-08-17-ui-refactor-vs-recreate-audit.md`'s 2026-08-20 re-audit section
and the fixes in commits `c1a6dab10`/`b6e2759ae`).

The two failure shapes to watch for, both already fixed once each but easy to
reintroduce:

1. **A defaulted/optional `id` that feeds a *different* child widget's required-id
   slot.** `shadcnAvatarGroup(id: String = "avatar")` used to interpolate
   `"$id.$index"` into each child avatar's id -- two `shadcnAvatarGroup`s on one
   screen without an explicit `id` collided. Fix: every widget that constructs a
   child widget's id from its own id must take a **required** `id`, not a defaulted
   one.
2. **A loop or sibling-call site that doesn't derive a unique id per iteration.**
   `shadcnButtonGroupSeparator()` called inside `forEachIndexed` with no `id` fell
   back to the same orientation-derived string every iteration -- a single button
   group with 3+ members collided with *itself*. Fix: pass a derived id
   (`"$id.sep.$index"`) at every call site inside a loop or repeated composition,
   never rely on a shared default.

**Safety net, not a substitute for getting it right:** `UiContext` throws immediately
if the same `id` is claimed twice in one real (non-measuring) frame
(`UiContextFrameState.recordSemantic`, `awake/ui/ui-core/.../context/UiContextFrameState.kt`).
This turns the silent-bug class above into a loud crash during development/tests
instead of a shipped visual glitch -- but it only fires once you actually render two
colliding instances together, so it doesn't replace picking real, unique ids up front.
If you hit this throw, the message names the colliding literal id; the fix is almost
always shape #1 or #2 above.

Note the gap this throw does **not** cover: a bare `remember*` hook never reaches
`recordSemantic`, so two hooks sharing an id collide silently. See the "State hooks" section of
[`compose-parity.md`](compose-parity.md).

## The showcase catalog is the only catalog

`samples:ui-showcase` publishes one list -- `ShowcasePages` in `ShowcaseCatalog.kt`. The app
renders it and every preview/layout-signature fixture is derived from it. Do not maintain a
second list of preview entries in test code.

Three rules keep it honest:

1. **One file per page**, under `ui/pages/<category>/`. The page object owns its own metadata
   *and* its `hero`/`variants`/`states` renderers. A preview function with no page object is
   dead code, not a hidden feature.
2. **`showcasePageOrNull` returns null for an unknown id.** Never reintroduce a fallback to
   `ShowcasePages.first()`. The old fallback let five test fixtures fingerprint the
   Introduction page while claiming to cover Range Slider, State, Shimmer, and Field Demo --
   green tests, zero coverage.
3. **Preview size lives on the page** (`previewWidth`/`previewHeight`), not in a JVM
   annotation. Reading fixture metadata by reflection forces a skip on iOS and wasmJs, so the
   same test silently passes there without asserting anything.

A component shadcn ships that Awake cannot build yet gets a `showcasePlaceholder(...)` entry
naming the missing primitive, not silence.
