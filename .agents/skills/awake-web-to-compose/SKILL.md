---
name: awake-web-to-compose
description: Port a website or raw HTML/CSS into Awake Compose. Routes Tailwind and shadcn sources to focused translation skills; do not use to add browser runtimes to the engine.
---

# Web Reference → Awake Compose

Use a website as a **visual and behavioural specification**, never as a runtime dependency or a
markup format to reproduce. The outcome is a Compose-native screen built from typed tokens,
reusable recipes, and Awake layout/interaction primitives.

## Establish the port brief

Before implementing a port, read [reference intake and fidelity](references/reference-intake.md).
It separates pixel-parity, design adaptation, and functional approximation; records asset rights,
platform inputs, and observed states; and prevents a single desktop screenshot from becoming an
invented responsive specification. Copy [the port-brief template](assets/web-port-brief.md) into
the task or product documentation when the port is substantial.

## Translate in three passes

### 1. Extract a product style sheet

Before writing a page, inventory repeated visual decisions: colour roles, typography roles,
spacing scale, radii, borders, elevation, icon treatment, content widths, and responsive
thresholds. Put product-specific values in that product's complete theme/token value. Do not
make a website's brand part of the engine-wide Shadcn theme.

Repeated visual patterns become product recipes (for example `AcmeHero`, `AcmeFeatureCard`),
not copied modifier/style blocks in every screen. Generic Shadcn-compatible look, variants, or
tokens belong in the design-system layer; mechanics a differently skinned product still needs
belong below it. The canonical ownership rule is in
[`docs/reference/ui-ownership.md`](../../docs/reference/ui-ownership.md).

Read [component extraction](../awake-ui-design-audit/references/component-extraction.md) before
creating a recipe. Prefer an existing shared recipe when it fits, extract a product-local recipe
for a repeated branded pattern, and retain a deliberate one-off only when its composition is
unique. A reference without its own design system is valid input: establish the smallest
product-local token/recipe layer that its repeated patterns prove, warn about the resulting debt,
and do not force Shadcn branding onto it.

For any implementation using `Modifier`, `Style`, or animation, read
[Compose primitives for a web port](references/compose-primitives.md) before translating CSS
declarations or transitions. It states the current Compose-native API and its deliberate gaps;
do not rely on legacy `ui:*` examples while the migration is in progress.

For a responsive reference, also read [adaptive layout](references/adaptive-layout.md). A CSS
media query is not automatically an Awake breakpoint: choose explicit product layout modes from
the observed reference and verify each supported viewport.

When a reference leaves composition choices open and the brief requests design adaptation, read
[visual composition](references/visual-composition.md). Its golden-ratio guidance is optional and
must never override a supplied reference capture.

When asked to score, rank, or improve a port's design quality, read
[`awake-ui-design-audit`](../awake-ui-design-audit/SKILL.md). It classifies evidence-backed
findings without confusing visual polish with fidelity proof.

### 2. Map structure and layout

Map intent, not tags or class names:

| Web reference | Awake Compose target |
|---|---|
| DOM hierarchy | composable/layout hierarchy |
| `flex` row/column | `Row` / `Column` |
| grid | explicit product row/column composition for known counts; a Foundation gap for arbitrary responsive grids |
| `gap` / sibling margins | parent arrangement spacing |
| container padding | container modifier padding |
| `max-width` content shell | width constraints on the enclosing layout |
| CSS media query | explicit window-size/adaptive state and alternative layout |
| CSS visual state | typed recipe variant/state style |

HTML elements are semantic clues, not API names. A `button` generally maps to a named
design-system button recipe; a `div` maps only to the smallest layout primitive needed. Do not
create a generic HTML element layer, CSS cascade, class-string interpreter, or per-child margin
API to make the port look literal.

When the source contains Tailwind utilities, read
[`awake-tailwind-to-compose`](../awake-tailwind-to-compose/SKILL.md). It owns Tailwind-scale and
utility translation; apply its conclusions through the Compose-native primitives above.

### 3. Map behaviour and accessibility

Treat Base UI/Radix/shadcn as an anatomy and state-machine reference:

- trigger, content, portal, overlay, focus handling, and dismiss rules reveal the interaction
  contract;
- visual variants and sizes reveal design-system recipe inputs;
- browser-only implementation details do not dictate Awake architecture.

Use an existing Compose/Foundation primitive when it has equivalent behaviour. If behaviour is
missing, first classify it: reusable mechanics belong in the lower behaviour layer; a branded
appearance or named variant belongs in the product/design-system recipe. Record a genuine
Foundation gap rather than hiding it in a one-off website screen.

Web semantic/ARIA data may inform a future web-host accessibility adapter. It is not a reason to
make DOM or HTML the shared rendering tree for Vulkan, desktop, iOS, or other hosts.

## Source-specific guidance

### Raw HTML and CSS

Read semantic grouping, ordering, and interactive controls from the HTML; read measurements and
visual state from CSS. CSS shorthand is not Compose API: distinguish caller layout padding from a
component's internal content padding, and translate CSS margins to the parent layout.

### Tailwind and shadcn

- Source Tailwind utilities → read
  [`awake-tailwind-to-compose`](../awake-tailwind-to-compose/SKILL.md).
- Source official shadcn recipes or component anatomy → read
  [`awake-shadcn-to-compose`](../awake-shadcn-to-compose/SKILL.md).
- Base UI/Radix without a shadcn recipe remains a behaviour reference here; do not give it
  Shadcn visual identity by association.

Both child skills are source translators. They do not authorize importing Tailwind, React, DOM,
or browser event APIs into the product.

## Deliverable shape

Unless the brief requests a one-off mock, deliver typed product theme/tokens, reusable product
recipes, and page composition using them. Include only the assets, responsiveness, interaction,
and capture coverage justified by the selected fidelity target.

For a common screen shape, adapt [layout archetypes](assets/layout-archetypes.md) instead of
inventing a new page skeleton. They are structural templates, not a product visual style or a
replacement for the supplied website reference.

Use a named Shadcn recipe for ordinary controls rather than application-authored control styling.
Read `awake-ui-authoring` before adding a widget or token, `awake-shadcn-recipe-consuming` for a
screen consuming recipes, and `awake-ui-verification` before asserting visual fidelity.

## Completion check

- The page contains no HTML, CSS, Tailwind class strings, React, or browser runtime dependency.
- Product identity is centralized in a theme/tokens and recipes, not scattered through screens.
- Shared behaviour is not reimplemented per website.
- Validation matches the selected fidelity target; unobserved responsive or interactive behaviour
  is called out rather than claimed as matched.
