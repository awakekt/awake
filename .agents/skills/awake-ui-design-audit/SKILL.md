---
name: awake-ui-design-audit
description: Audit or remediate an Awake Compose screen, screenshot, or web reference for hierarchy, content density, design-system consistency, theme handling, responsive behavior, accessibility, and visual quality. Use when asked to review, improve, remediate, fix, score, or rank a UI; do not use to claim pixel fidelity without reference evidence.
---

# Awake UI Design Audit

Turn a UI review into evidence-backed findings and prioritized improvements. Do not reduce a
screen to “ugly” or declare a design objectively good from taste alone: distinguish measured
failures, visible inconsistencies, and composition recommendations.

## Modes and trigger keywords

Use **audit mode** for: `audit UI`, `review UI`, `design review`, `score UI`, `rank UI`,
`why is this ugly`, `too much text`, `too many cards`, `not responsive`, `dark mode`, or
`design-system consistency`.

Use **remediate mode** for: `remediate UI`, `fix UI`, `clean up UI`, `improve this screen`,
`resolve audit findings`, `make this responsive`, `fix dark mode`, `reduce duplicate UI`, or
`make it reusable`.

Remediate mode begins with the same evidence and classification pass, then implements the
smallest corrections supported by that evidence. Fix P0/P1 findings first, then the requested
P2/P3 items. Do not silently redesign a reference, change unobserved states, or promote a
product pattern into the shared design system. Report any deferred finding and the verification
run after the correction.

## Evidence first

Collect the available screen/source evidence, intended task and audience, viewport(s), theme(s),
input modes, and product stage. A screenshot proves one visible state only. Do not infer a dark
theme, responsive behavior, keyboard operation, or loading state that was not provided or
inspected.

Read [the audit rubric](references/audit-rubric.md) before assigning a score. For a reusable
report, copy [the audit template](assets/ui-design-audit-report.md). When the request is a web
port, read `awake-web-to-compose` as well; when a finding requires a fidelity claim or a new
baseline, read `awake-ui-verification` before making that claim.

## Audit repetition and component ownership

Inspect the screen or reference for repeated controls, cards, content sections, and copied visual
rules. Read [component extraction](references/component-extraction.md) before recommending a
new component. Two matching occurrences are an extraction candidate, not automatic permission to
grow the engine-wide design system.

When a source has no coherent design system, report that fact with its evidence and a smallest
product-local repair. Do not reject a faithful one-page port or impose Shadcn branding on it. Use
`P2` once inconsistency or repeated rules affect the experience; use `P3` for a contained static
one-off whose debt is explicitly recorded. Escalate only for the actual task or accessibility
impact.

## Classify every finding

Each finding must state:

- category: task/hierarchy, content/typography, spacing/proportion, design system, surfaces,
  color/theme, adaptive layout, interaction/state, accessibility, motion, performance, or assets;
- priority: `P0` release blocker, `P1` task/accessibility blocker, `P2` systemic inconsistency,
  `P3` polish;
- evidence: measured, directly observed, or inferred;
- user impact, the smallest effective correction, and the verification needed.

Use a specific observation: “three primary-colored actions compete above the fold” rather than
“the colors are too much.” Name what works as well as what needs change.

## Score with coverage, not false precision

Score only categories that can be assessed from the supplied evidence. The weighted score is a
diagnostic baseline, not release approval; report its coverage beside it. Below 80% coverage,
label the score provisional rather than assigning a grade.

Never let a high composition score offset a P0/P1 task or accessibility defect. Fix blockers in
priority order, then system consistency, then visual polish.

## Completion check

- Findings are actionable and do not duplicate the same root cause.
- Inferred findings are labelled as such and unobserved states remain unmeasured.
- A recommended token/recipe change has a clear owner; a one-off page change does not become an
  accidental global rule.
- Repeated UI has an explicit reuse decision: existing shared recipe, product-local recipe, or
  deliberate one-off composition.
- Any claim of reference fidelity names its provenance and uses the appropriate verification path.
