# Translating shadcn into Awake

Extends `SKILL.md`. Read when porting a shadcn class string into a `ui-designsystem` recipe.

## Always read all three sources before writing a component

Not "pick the best one". Three, every time, because each answers a question the others cannot — and
skipping one has produced a different failure each time.

| Source | Where | Answers |
|---|---|---|
| **1. shadcn/ui upstream** | `tools/shadcn/reference-app/src/ui/*.tsx`, vendored from the pin | What the component *is* — exact values, variant set, anatomy. The only authority on "what is correct" |
| **2. `shadcn-compose`** | [github.com/ronjunevaldoz/shadcn-compose](https://github.com/ronjunevaldoz/shadcn-compose) | How the same thing was already solved *in Kotlin*, by the same author, against a Compose-shaped API. A solved translation, not a spec |
| **3. Awake's existing** | `ui-headless` / `ui-designsystem` | What we already know that neither of them does — the bugs this repo actually shipped and fixed |

**What each one catches that the others miss**, from real cases in this port:

- **Upstream only.** `kbd`'s radius is 6 and `toggle`'s padding is `px-2`. Awake had 4 and `px-3`,
  and both parity tests were *green* against a stale vendored copy. Only upstream settles a value.
- **`shadcn-compose` only.** Its resizable group has keyboard resizing; `ui-headless`'s never did,
  so a resizable layout was unreachable without a mouse. Nothing in the CSS says "add arrow keys" —
  a Kotlin implementation that already faced the question does.
- **Awake's existing only.** The slider's track is inset by half a thumb because a thumb centred at
  fraction 0 was being clipped — reported as "knob cut when reach start or end". Neither upstream nor
  a fresh port tells you that; only the code that already hit it does.

**And read them in that order.** Upstream first fixes the values, so a difference in either of the
others is then visible as a *decision* rather than absorbed as the baseline. Reading Awake's version
first is how a stale value becomes the thing you match.

**Record the comparison in the file you write**, briefly — what each source said and why you chose
what you chose. `ShadcnResizableMath` is the model: it states that `shadcn-compose`'s two-panel model
makes conservation structural and strictly better, then why it does not apply here (N panels, pinned
by `ResizablePanelDragConservationTest`). Without that note the next person redoes the comparison and
may reach the other answer.

A difference you cannot explain is a finding, not noise. All three disagreeing about the same number
means one of them is a bug — that is how the `kbd` and `toggle` drift surfaced at all.

Two rules, both learned from real parity drift, both about translating a source class
*faithfully* rather than eyeballing a number.

## 1. Use `Tw.Spacing.sN`, not the raw `Dp` it happens to equal

shadcn's `p-1` is Tailwind spacing step 1. `Tw.Spacing.s1` says that; `4f.dp` says nothing and
forces the next reader to rediscover where the number came from. Same for `gap-2` →
`Arrangement.spacedBy(Tw.Spacing.s2)`.

**It is under-applied, and measured 2026-08-23 the gap is discipline rather than a missing scale.**
`ui/designsystem/styles/` holds 99 raw `Nf.dp` literals, and **73 of them have an exact `Tw` step
that already exists**:

| Literal | Count | Step |
|---|---|---|
| `1f.dp` | 26 | **none** — see below |
| `0f.dp` | 16 | `Tw.Spacing.s0` |
| `8f.dp` | 11 | `Tw.Spacing.s2` |
| `6f.dp` | 8 | `Tw.Spacing.s1_5` |
| `4f.dp` | 5 | `Tw.Spacing.s1` |
| `16`/`12`/`24`/`36`/`40`/`2` | 16 | `s4`/`s3`/`s6`/`s9`/`s10`/`sm` |

`Tw` covers 34 spacing steps up to 384px, so it is not the thing that is missing. Reach for it.

**The one real gap: Tailwind's border widths have no scale.** `Tw` emits `Spacing`, `Radius` and
`Text` only, so `border-0`/`border`/`border-2`/`border-4`/`border-8` have no named home -- which is
what all 26 `1f.dp` literals are. Until the generator emits them, a literal with the class named
beside it is the honest form; see `ShadcnCard`'s `CardBorderWidth`.

Usage today is 8 files in `ui-designsystem` and **zero** in `ui-headless` or `samples`.

Do not mass-rewrite the existing literals as a drive-by -- a blind sweep launders wrong values into
looking intentional. Convert as each file is ported, where the upstream class is in front of you.

## 2. CSS `border-box` means a border CONSUMES layout space; Awake's does not

A shadcn container that is `p-1` plus a `border` insets its content by **border + padding = 5px**,
not 4px. Awake's border is paint-only: `surfaceCore` insets content by
`resolved.contentPadding` alone and never by `borderWidth`.

That is deliberate and **Compose-faithful** — Compose's `Modifier.border` is a draw
modifier, not a layout one, which is exactly why Compose code chains
`.border(...).padding(...)` to inset content. So do **not** "fix" this in `ui-core`; doing
so would break the Compose parity the engine is built on.

The correct translation is at the recipe: fold the border width into that recipe's own
`contentPadding`.

```kotlin
private val DropdownBorderWidth = 1f.dp
border(DropdownBorderWidth, values.colors.border)
contentPadding(Tw.Spacing.s1 + DropdownBorderWidth)   // shadcn `p-1` + border, border-box
```

Real cost of getting this wrong: `shadcnDropdownMenu` rendered its items at x=4 width=152
inside a 160px surface instead of the reference's x=5 width=150, and the surface measured
136 tall instead of 138 — exactly 1px per side on both axes, invisible by eye, caught only
by the parity report. Ten bordered styles share this shape; `ShadcnBorderBoxInsetTest`
pins the rule for the one with captured reference evidence.

## Ported code takes the canonical name, never the `Ui` alias

`core:graphics2d` already declares the real types and keeps `Ui*` as aliases over them —
`UiPath = DrawPath`, `UiPathCommand = PathCommand`, `UiShapeSpec = DrawShape`, and sixteen more. The
variants of `UiDrawPrimitive` are declared on `DrawCommand`, so `DrawCommand.RoundedQuad` works
today.

So a rewritten recipe takes the canonical name. This is not a separate cleanup: it is
`2026-08-22-ui-prefix-rename-plan.md`'s own rule — *rename what a file owns as you touch it, never
introduce a new `Ui` name* — and a port is exactly the moment a file is touched. Doing it here costs
nothing and shrinks a 978-usage sweep by hand.

Keep the alias only where an API still forces it (`Painter.paint` returns `List<UiDrawPrimitive>`),
and say so at the point of use so it reads as a constraint rather than an oversight.

`python3 tools/shadcn/port_progress.py` lists any `Ui*` name a ported file still uses. Comments are
stripped first — a doc comment naming the legacy type it replaced is prose doing its job.

## Where a designsystem constant lives

The size rule in `SKILL.md` governs whether `ui-headless` may have a fallback at all. Once you are
in `ui-designsystem` supplying a real branded value, the question becomes *where it lives*, and
that has its own answer — see "Which home a number belongs in" in `awake-shadcn-recipe-authoring`.

Short form: a Tailwind step names the step (`Tw.Spacing.s1`, not `4f.dp`) even when used once, a
component's own geometry goes on its `Size` enum, and related values are derived from one
another rather than restated. As of 2026-08-21 that module holds 75 unnamed inline literals, so
the pattern is aspirational in most files — follow it in new code rather than matching what is
already there.
