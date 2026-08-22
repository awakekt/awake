### Claude Code Project Profile

### Load skills context on initialization
--system-prompt-file=".claude/AGENTS.md"

### Default flags
--compact
--verbose=false

### Ignore generated and vendor directories
--ignore="**/build/**"
--ignore="**/.gradle/**"
--ignore="**/vendor/**"
--ignore="**/third_party/**"

### Read first
- `docs/architecture.md`
- `docs/reference/ai-collaboration.md`
- `docs/reference/agent-catalog.md`
- `docs/reference/ui-ownership.md`
- `docs/reference/ui-validation.md`
- `docs/reference/game-structure.md`
- `docs/reference/framework-game-boundary.md`
- `docs/mvp-plan.md`

### Read before writing engine code
Mandatory for the domain you are touching — each encodes a bug this repo actually shipped:
- `skills/awake-core-math/SKILL.md` — before any `Vec3`/`Mat4`/camera math, or any code inside
  a `System.update`. Covers the mutating-vs-allocating naming contract (`normalize()` mutates,
  `normalized()` allocates), per-frame allocation rules, and the shared camera-basis rule.
- `skills/awake-ecs-authoring/SKILL.md` — before adding a component, writing a `System`, or
  building entities with the `scene { }` DSL. Covers `Poolable.reset()` completeness, why
  reflective component construction breaks on iOS/wasmJs, structural-change churn, entity
  ownership on teardown, and `@DslMarker` on nested builders.
- `skills/awake-ecs-scene-runtime/SKILL.md` — consuming the scene runtime from a sample/demo.
- `skills/awake-render-pipeline/SKILL.md` — before adding a render feature/pass, wiring a new
  `RenderPipeline`, touching draw-call sorting in `RendererDraw3D`, changing `GameApplication`/`Game` wiring,
  or writing any vertex attribute layout. Covers dynamic `VertexFormat` attribute derivation and the
  mandatory cross-backend commonization rule (`render:contract` / `render:passes`) to prevent duplicate backend code.
- `skills/awake-render-vulkan/SKILL.md` — before modifying Vulkan swapchain creation/resizing,
  GPU resource allocations, command recording, JNI bindings, or Android Vulkan verification.
- `skills/awake-render-webgpu/SKILL.md` — before modifying WebGPU pipelines, wgpu4k/Dawn integration,
  WASM browser canvas resizing, or buffer upload paths.
- `skills/awake-physics-jolt/SKILL.md` — before modifying physics simulation steps, rigid bodies,
  colliders, contact listeners, raycasting, or ECS physics synchronization.
- `skills/awake-ui-authoring/SKILL.md` — before adding or changing any UI widget, adding a
  size/spacing constant, or naming a primitive. Covers which of `ui-core`/`ui-headless`/
  `ui-designsystem` owns what, the derivable-size rule (an headless default becomes the spec),
  Dp-not-pixels, and the Radix-canonical naming policy.
- `docs/reference/compose-engine/README.md` — before touching `:awake:compose:*`. Design set for
  the retained, `Constraints`-based layout engine that replaces `ui-core`'s trial-measure model.
  The README's status section says what has actually landed; `11-refinements.md` is a review gate
  for any deliberate divergence from Compose.
- `skills/awake-ui-shadcn-consuming/SKILL.md` — before adding or changing any screen in a
  sample, game, or tool that renders UI (not limited to `samples:studio`). Consumer code
  renders visible UI only through `shadcn*` recipes, never imports `ui-core`, never authors
  its own `Style{}`; `ui-headless` layout/state (`column`/`row`/`Modifier`/`remember*`) stays
  fine to import for structure. `ui-core` ≈ `compose-ui`; `ui-headless` is Foundation-shaped
  for layout/text/draw but Material-shaped for its ~21 controls with the styling removed, so
  check a control's API against Radix/Base UI anatomy and layout APIs against Foundation
  (see `docs/reference/ui-ownership.md`); `ui-designsystem` ≈ Material's role, as shadcn.
- `skills/awake-ui-shadcn-styling/SKILL.md` — maintainer guide for building or extending Shadcn
  components in `ui-designsystem`. Covers `Style.then` state-rule merges, card trigger padding,
  collapsible decoupling, and animation tweening.
- `skills/awake-ui-icons/SKILL.md` — before adding or editing any `UiImageVector`/icon
  path data. One hard rule: icon vectors are generated from SVG sources via
  `tools/svg_to_ui_image_vector.py`, never hand-transcribed or derived by rotating another
  glyph's coordinates.
- `skills/awake-ui-performance/SKILL.md` — before writing anything inside a `row`/`column`/`surface`
  content lambda, before adding a per-frame allocation, and before claiming a UI perf improvement.
  Covers the per-frame allocation rule UI lacked, why trial passes multiply every cost, skipping
  work a trial discards, `cacheKey`'s opt-in semantics and its silent-staleness risk, and the
  measurement traps (scene shape, re-profiling, desktop JVM being the forgiving platform).
- `skills/awake-ui-verification/SKILL.md` — before claiming UI fidelity or modifying snapshot tests.
- `skills/awake-framework-boundary/SKILL.md` — before promoting sample/game code or adding server, network, persistence, or MMO-oriented abstractions.

### Comment rules
- **No agent-persona or tool tags in source.** Never write `ponytail:`, `caveman:`, `claude:` or
  any similar prefix in a comment, commit or doc. They name the tool that wrote the line, which
  tells a future reader nothing and dates the code. Write the constraint plainly instead: state the
  ceiling and the upgrade path (`16 bits caps a dimension at 65534px; widen to the focus-bucket
  scheme if a larger viewport ever exists`), not who decided it.
- Standard `TODO:`/`FIXME:` are fine — they are conventions the tooling and every reader already
  understand.
- Keep comments short and reader-focused. No investigation narration, no quoted bug reports, no
  restating what the code says.
