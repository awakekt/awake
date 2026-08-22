# UI / shadcn Parity Audit and Remediation Plan

Date: 2026-08-20  
Scope: Awake's `ui-core`, `headless`, `ui-designsystem`, and `samples:ui-showcase`, compared with the repository's pinned official shadcn/ui checkout (`6261bd89f72d794aea491482cc2acfd8dc3d63e2`).

## Executive summary

Awake has a broad, working shadcn-inspired component layer and its desktop UI test suites pass. It does **not** currently have 100% parity verification with official shadcn/ui.

The primary problem is not an absence of tests. It is that the existing evidence has different scopes and strengths:

- token tests can prove a resolved token equals the pinned reference;
- geometry tests can compare selected semantic bounds with a reference DOM rectangle;
- screenshots detect an Awake regression, not correctness;
- the current pixel comparison does not establish full visual fidelity because some captures are misframed and every renderer/font differs;
- behavior and motion have only local tests, not a browser-oracle comparison.

The generated parity dashboards are currently unreliable: both generators still search the deleted `awake/engine/ui/...` module tree. They consequently report `0/46` components, no dark captures, and absent functionality that is present in the active `awake/ui/...` tree. Do not use either generated percentage as a release decision until that is repaired.

## Evidence run

| Check | Result | Meaning |
|---|---|---|
| `tools/fetch_shadcn_reference.sh` | pass | The local reference is the pinned official shadcn/ui source. |
| `tools/extract_shadcn_tokens.py` | pass | Reference token data was extracted from that checkout. |
| `:awake:ui:ui-core:desktopTest` | pass | Core layout/render/runtime regression tests pass. |
| `:awake:ui:headless:desktopTest` | pass | Headless behavior regression tests pass. |
| `:awake:ui:designsystem:desktopTest` | pass | Recipe and design-system regression tests pass. |
| `:samples:ui-showcase:desktopTest` | pass | Showcase parity and preview test suite passes. |
| Design-system naming / duplicate / Headless-backed audits | pass | 21 component files have valid naming, 20 recipe files are Headless-backed, and duplicate recipes were not found. |
| `:awake:ui:designsystem:check` | fail | Detekt reports 19 pre-existing quality violations. This is unrelated to the passing functional UI tests, but prevents a clean full `check`. |

Passing regression tests mean Awake still behaves as its accepted baseline. They do not prove the accepted baseline matches shadcn.

## Coverage assessment

| Dimension | Current evidence | Audit verdict | What prevents 100% |
|---|---|---|---|
| Component inventory | Recipes exist across the design-system module; the inventory generator is broken. | Unknown, not certifiable | Repair the source-path and symbol scanner, then maintain a component-by-component manifest. |
| Spacing and padding | Geometry tests derive reference content bounds from CSS padding for selected nodes. | Partial | No exhaustive component × variant × state × constrained/dynamic-size matrix. |
| Width / height | 26 official-reference geometry fixtures cover selected components. | Partial | Allowances reach 76px for breadcrumb, 25px for alert, 14px for slider, and 10px for textarea/popover. Those are useful regression limits, not strict parity. |
| Dynamic size / text fit | Core matrices and a subset of reference geometry cases cover intrinsic/fill behavior. | Partial | No official matrix across short/long content, min/max constraints, density, and every size variant. |
| Colors | `ShadcnReferenceTokenExpandedTest` compares all seven base colors in light and dark. | Partial | Neutral matches reference. The six non-neutral palettes contain intentional `knownDrifted` locks, meaning the test preserves their current divergence instead of proving them correct. Accent overrides are outside the official-token oracle. |
| Borders / radius | Style parity reads reference CSS and checks selected rounded quads. | Partial | Border width is read but not asserted; color comparison is qualitative (light/dark threshold), not exact RGBA/OKLCH equality. Shadows are absent from recipes. |
| Focus ring | Local focus behavior exists. | Missing parity | No real shadcn ring geometry, color, blur, or state comparison. |
| Dark mode | 26 official local dark PNG captures and matching JSON captures exist. | Partial | The official-pair manifest gates only selected dark cases; no exhaustive dark state/variant/motion matrix. The generated dashboard incorrectly says there are none. |
| Static pixels | Reference screenshot comparisons exist for selected cases. | Regression only | Pixel percentages are not a full-fidelity oracle; font rasterization differs and slider/tooltip captures are excluded for framing defects. |
| Popup / overlay | Dialog, dropdown menu, popover, tooltip, drawer/sheet code and local behavior tests exist. | Partial | No complete keyboard, focus-trap, portal/layering, escape, positioning, collision, and animation parity matrix. |
| Keyboard / focus | Button Space activation and some local focus mechanics are covered. | Missing parity | No Tab traversal, Escape dismissal contract, arrow navigation, Enter activation matrix, roving focus, or official browser behavior oracle. |
| Animation / motion | Progress, spinner, skeleton, sheet, collapsible, and popup mechanics have local tests. | Partial implementation; missing parity | No official sampled rest/in-flight/settled frames, duration/easing comparison, or GPU validation. Toast is explicitly a hard show/hide rather than Sonner-style fade motion. |
| Accessibility / semantics | Local semantic roles and widget tests exist. | Partial | No browser/assistive-tech or official ARIA parity audit. |
| GPU output | CPU rasterizer and some backend checks exist. | Partial | No systematic Vulkan/WebGPU reference comparison. |

## Specific audit findings

### P0 — parity dashboards are false sources of truth

The retired `tools/generate_parity_report.py` and the then-current `tools/generate_ui_status.py`
used `awake/engine/ui/...` paths. The active modules live in `awake/ui/...`. The old parity
generator's symbol matcher also failed to discover real recipe declarations. This historical
finding is addressed by the manifest-backed parity report and repaired status-generator roots.

Impact: component coverage, token-drift count, dark-capture count, and capability status cannot be used to prioritize release work or claim parity.

### P0 — no complete parity contract

There is no manifest that says, for every supported shadcn component and Awake variant, which official fixture, semantic nodes, styles, behavior sequences, and animation samples are required. Without it, a passing suite can leave an entire variant, popup state, dynamic size, or dark state unmeasured.

### P1 — geometry proof is selected and sometimes intentionally loose

The geometry harness is the strongest existing layout oracle: it compares reference DOM rectangles and derived content widths. However, it covers selected fixtures rather than the full component surface and permits large component-specific variance. It also calculates content height but does not include that delta in the failure condition. Padding is therefore only partially proven.

### P1 — style proof is not exact enough for color, border, or shadow parity

The style harness checks rounded-quad radius on selected primitives, but treats a present border-width field as an assertion without comparing it. Background color checks only decide whether a surface is broadly light or dark. It does not compare foreground color, border color, opacity, shadow, focus ring, or computed state styles exactly.

### P1 — color parity has declared divergence

The seven-base-color test is valuable, but it deliberately locks known drift for Stone, Zinc, Mauve, Olive, Mist, and Taupe. These should be reported as non-parity until the palette becomes a per-token mapping equivalent to the pinned reference. Neutral is the only fully matching base-color palette.

### P1 — dark mode is captured but not fully gated

Official local capture assets include 26 light and 26 dark PNGs plus 46 JSON captures. That is a good starting point, but pair manifests and assertions do not span every component/state/variant. The report must distinguish “dark reference exists” from “dark parity verified.”

### P1 — interaction behavior is locally tested, not browser-parity tested

The behavior suite covers click activation, Space activation, checkbox/switch toggles, dropdown selection, and outside-dialog dismissal. It does not prove the broader Radix/shadcn interaction contract: Tab and shift-Tab order, Escape, Enter, arrows, typeahead, roving focus, focus restoration, nested dismissable layers, and portal behavior.

### P2 — motion is not compared to official shadcn

Awake has animation mechanics and local rest/in-flight/settled tests for some widgets. It has no official timing/easing or frame-by-frame oracle. This is particularly visible for toast, which documents an immediate show/hide lifetime rather than Sonner's animated transition.

### P2 — full `check` remains red

The design-system `check` task fails on 19 Detekt findings. Resolve those independently of parity so a release gate can be trusted as a single command.

## Definition of “100% checked”

The target should be a complete *supported-surface* contract, not a single pixel percentage:

1. Every supported official component has a declared Awake mapping, or an explicit unsupported rationale.
2. Every exposed variant, size, enabled/disabled/selected/open/focused state, and light/dark theme has an official reference fixture.
3. Static components have exact geometry assertions for outer bounds, content bounds, padding, gap, radius, border width, and border color; pixel review remains a secondary regression signal.
4. Dynamic components add short/long text, constrained/fill/intrinsic dimensions, min/max size, density 1x/2x, scrolling/resizing, and layout-direction cases where applicable.
5. Overlay components add anchoring, clipping/collision, portal/layer order, outside click, Escape, focus trap/restore, keyboard navigation, and nested-layer cases.
6. Animated components add rest, in-flight, settled, and interrupted samples, plus declared duration and easing comparisons.
7. All of the above run on the default official theme in light and dark. Non-official Awake presets are tested for internal consistency, never described as official shadcn parity.
8. A clean `check` plus the full official-reference parity suite is required before claiming completion.

## Remediation plan

### Phase 0 — make reporting truthful

1. Move both generators to `awake/ui/...` paths and repair their Kotlin declaration extraction.
2. Regenerate the reports only after the generators locate live source correctly.
3. Add generator unit tests with known recipe fixtures so another module move cannot silently produce `0/46`.
4. Make the report show three distinct numbers: implemented, reference-covered, and fully gated. Never label any of them simply “parity.”

Exit criterion: the generated component inventory lists live recipes, dark-capture counts are correct, known token drift is reported, and a deliberately broken path test fails.

### Phase 1 — establish the parity manifest and reference corpus

1. Create one data manifest per official component mapping Awake recipe, variants/sizes/states, fixture IDs, semantic node IDs, and verification dimensions.
2. Expand the official local reference app/capture list to every supported component and state. Keep a separate explicit unsupported list for components Awake intentionally does not provide.
3. Capture light and dark references from the pinned checkout; recapture slider and tooltip to fix their bounding boxes.
4. Add a manifest completeness test: every supported recipe must have a reference; every reference must have an Awake preview; every preview must have semantic IDs.

Exit criterion: every supported component-state pair is either executable in the manifest or explicitly unsupported with a reason.

### Phase 2 — close deterministic static parity

1. Tighten geometry to a maximum 1px allowance except documented renderer/font-only subnodes. Split text advance from container geometry so typography cannot justify loose container bounds.
2. Assert both content width and content height, individual padding edges, gaps, border radius, and border width exactly from reference JSON.
3. Replace qualitative light/dark color checks with precise computed-color comparisons using a documented color-space tolerance.
4. Add border color, foreground color, opacity, shadow, and focus-ring fields to captured reference styles and compare the corresponding Awake primitives.
5. Fix known 40dp defaults and wire named shadcn sizes from design-system metrics; verify small/default/large/icon controls against the official fixtures.

Exit criterion: each static manifest row passes exact layout/style assertions in light and dark without broad component-level allowances.

### Phase 3 — dynamic layout and overlays

1. Add data-driven matrices for short/long content, wrap/fill/intrinsic dimensions, fixed/min/max bounds, density 1x/2x, scrolling, resize handles, and constrained parents.
2. Add an overlay matrix for popup, select, dropdown, tooltip, popover, dialog, sheet, drawer, and context menu: anchor position, edge collision, clipping, z-order, nested overlays, outside click, and dismissal.
3. Implement and test full keyboard/focus contracts: Tab/shift-Tab, Enter, Space, Escape, arrows, roving focus/typeahead where Radix defines them, focus trap, and focus restoration.

Exit criterion: all dynamic and overlay rows pass their official reference and local behavior assertions.

### Phase 4 — motion, rendering, and accessibility

1. Add official capture/measurement support for rest, in-flight, settled, and interrupted motion states; assert duration/easing with an allowed frame-time error.
2. Bring toast/Sonner behavior to the declared target or explicitly mark it as a non-parity component.
3. Run selected representative parity cases through Vulkan and WebGPU in addition to the CPU rasterizer.
4. Add semantic/ARIA contract tests for role, label, state, and keyboard behavior; document any platform limitations.

Exit criterion: every animated or accessible manifest row includes its required states and backend/semantic evidence.

### Phase 5 — release gate and maintenance

1. Resolve the 19 Detekt findings so the full design-system `check` task passes.
2. Add the reference refresh, manifest completeness, official geometry/style/behavior/motion parity, and dashboard-consistency checks to CI.
3. Require a pinned-upstream bump workflow: refresh source, tokens, captures, manifest, tests, reviewed diffs, and report in one change.

Exit criterion: CI has a single green parity gate, and release documentation can accurately state which official component surface is 100% verified.

## Recommended order

Do Phase 0 before any visual tuning. Otherwise, teams will optimize against reports that deny existing coverage and hide known drift. Then do Phase 1 and Phase 2 together for one component family at a time: button/input/selection controls first, overlays second, navigation and complex layout third, motion last.
