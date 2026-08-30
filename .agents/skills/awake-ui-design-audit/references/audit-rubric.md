# UI Audit Rubric

Score each assessed dimension from `0` to `4`. Leave it unmeasured when the evidence cannot
support a judgment; do not award a neutral score for missing evidence.

| Score | Meaning |
|---|---|
| 0 | Broken or absent; prevents task completion, safe use, or comprehension. |
| 1 | Major weakness; works only with substantial friction or inconsistency. |
| 2 | Functional baseline; important gaps or uneven execution remain. |
| 3 | Clear, consistent, and usable for the stated task. |
| 4 | Deliberate, resilient, and verified across relevant states/hosts. |

## Weighted dimensions

| Dimension | Weight | Inspect |
|---|---:|---|
| Task clarity and hierarchy | 15 | Primary task, scan order, action emphasis, navigation clarity. |
| Content and typography | 10 | Density, grouping, line length/height, type-role consistency, long content. |
| Design-system consistency | 15 | Named tokens, recipe reuse, repeated controls, spacing/radius/type scale. |
| Surfaces and color roles | 10 | Nested cards, border/shadow competition, semantic color use, contrast. |
| Light/dark theme semantics | 10 | Role-based colors and interactive states in each provided theme. |
| Adaptive layout | 15 | Compact/expanded task flow, clipping, overflow, viewport threshold evidence. |
| Interaction and state | 10 | Discoverability, loading/empty/error/disabled/selected states, feedback. |
| Accessibility | 10 | Readability, contrast where measurable, keyboard/touch/focus evidence, motion alternatives. |
| Motion and performance | 5 | Purposeful motion, reduced-motion behavior, unnecessary visual/tree cost. |

`weighted score = Σ(weight × score / 4) / Σ(assessed weights) × 100`

Report assessed-weight coverage: `Σ(assessed weights) / 100`. A score under 80% coverage is
**provisional**. Do not compare two audits with materially different coverage.

## Finding priorities

| Priority | Meaning | Examples |
|---|---|---|
| P0 | Unsafe or release-blocking | Primary action impossible, critical content invisible, severe accessibility failure. |
| P1 | Core task or accessible operation is materially impaired | No keyboard path for a required control, compact layout hides an action, unreadable essential text. |
| P2 | Systemic quality or consistency defect | Product uses unrelated spacing scales, theme roles collapse in dark mode, every card nests another card. |
| P3 | Local polish improvement | Optical alignment, decorative density, a less effective proportional split. |

## Classification guide

| Symptom | Classify as | What to inspect |
|---|---|---|
| “Too much text” | Content and typography | Task-oriented grouping, headings, line length, progressive disclosure, empty whitespace. |
| “Not proportional” | Spacing/proportion or hierarchy | Content constraints, focal region, repeated alignment, compact branch; golden ratio only when the source leaves a split open. |
| “Too many colors” | Surfaces and color roles | Whether every color maps to a semantic role; competing accent/primary/destructive use. |
| “Not a design system” | Design-system consistency | Duplicate literals, variants, recipes, inconsistent controls, missing token owner. |
| “Too many cards” | Surfaces and color roles | Whether each surface establishes a genuine grouping or merely adds chrome. Flatten nested containers before adding more border/shadow. |
| “Dark mode looks wrong” | Light/dark theme semantics | Background, foreground, border, overlay, disabled, hover, focus, destructive, and image treatment—not only base colors. |
| “Not responsive” | Adaptive layout | Root viewport modes, content order, overflow, touch/keyboard alternatives, evidence at threshold edges. |

## Evidence levels

- **Measured**: a test, semantic bounds, contrast calculation, capture diff, or inspected source
  establishes the fact.
- **Observed**: directly visible in the supplied screen or inspected interaction.
- **Inferred**: a reasonable hypothesis from incomplete evidence; phrase it conditionally and name
  the missing evidence.
- **Unmeasured**: not enough evidence; exclude from the score.

An audit finding must propose the smallest correction that addresses its root cause. For example,
replace scattered color literals with semantic roles rather than individually tuning each color.
