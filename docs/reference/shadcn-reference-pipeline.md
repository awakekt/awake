# Shadcn Reference Pipeline

This document is the canonical source for how Awake pins and extracts real shadcn/ui ground
truth, instead of relying on memory, screenshots, or a third party's reimplementation.

## Why

Awake's design-system claims to target shadcn/ui's visual language, but for a long time nothing
mechanically checked that claim -- comparisons were either eyeballed or copied from another
project's own doc comments (see the history note in `ShadcnReferenceTokenTest.kt`). The
`/tmp/shadcn-compose-ref` path referenced by older docs was never pinned or scripted: an
ephemeral manual clone, gone the moment the machine that made it was gone. This pipeline
replaces both problems with a real, reproducible, gitignored checkout of the actual
`shadcn-ui/ui` repository plus a deterministic extractor.

## Pieces

1. **`tools/shadcn/fetch_shadcn_reference.sh`** -- clones/updates `shadcn-ui/ui` at a pinned commit SHA
   into `third_party/shadcn-ui-ref/` (gitignored -- see `.gitignore`'s `/third_party/` entry).
   Idempotent: safe to re-run any time, always resets the checkout to `PINNED_SHA`.
2. **`tools/shadcn/extract_shadcn_tokens.py`** (python3, stdlib only) -- parses
   `third_party/shadcn-ui-ref/apps/v4/registry/themes.ts`'s theme entries for every base color
   Awake ships (`neutral`, `stone`, `zinc`, `mauve`, `olive`, `mist`, `taupe` -- one entry per
   `ShadcnBaseColor` value, `THEME_NAMES` in the script) -- the registry object literals that
   generate shadcn's real `:root`/`.dark` CSS custom properties for the new-york-v4 style -- and
   writes a generated, test-only Kotlin object:
   `awake/ui/shadcn/src/commonTest/kotlin/com/awakekt/awake/ui/shadcn/ShadcnReferenceTokens.kt`
   (`BY_BASE_COLOR`, keyed by theme name, plus a `light`/`dark` convenience for `"neutral"`).
   Deterministic: re-running against an unchanged checkout produces byte-identical output. A
   base-color name missing from `themes.ts` fails the script loudly rather than silently
   skipping it.
3. **`ShadcnReferenceTokenExpandedTest.kt`** (same `commonTest` package) -- diffs `ShadcnTheme`'s
   resolved tokens against `ShadcnReferenceTokens`, for every base color in both light and dark,
   in OKLCH-derived sRGB with tolerance, using the `KNOWN_DRIFTED` mechanism documented in that
   file's class doc for tokens the audit found already diverged from spec (fixing those values is
   a separate, later change -- this pipeline's job is ground truth and detection, not
   correction). `ShadcnStylePresetVerificationTest.kt` covers the 8 `ShadcnStylePreset` bundles
   on their own terms (no upstream reference exists for them) -- see "What Is Actually Verified"
   below.

## Usage

```bash
tools/shadcn/fetch_shadcn_reference.sh          # clone/update the pinned checkout
python3 tools/shadcn/extract_shadcn_tokens.py   # regenerate ShadcnReferenceTokens.kt from it
./gradlew :awake:ui:shadcn:desktopTest --tests "*ShadcnReferenceToken*"
```

## Verifying

`tools/shadcn/verify_shadcn_reference.sh` runs the two checks this pipeline needs, and they are
deliberately different in severity.

**1. Is the generated table stale? — fails.** Re-runs the extractor and diffs against the committed
`ShadcnReferenceTokens.kt`. A hand-edit of a generated file breaks the chain back to upstream while
every test stays green, which is the exact failure this pipeline exists to remove.

That is not hypothetical. `OUT_FILE` pointed at `awake/ui/shadcn/...`, a module path
that no longer exists, and `mkdir(parents=True)` meant a run silently created the dead tree and
wrote there while the real file went untouched. Nobody noticed because git does not track empty
directories.

**2. Has upstream moved since `PINNED_SHA`? — reports only.** Never fails, never bumps. Moving the
pin changes what the theme has to match, which is a visual decision with its own review.

Verified 2026-08-23: the registry at the current pin is byte-identical to `main`.

**Trap:** `apps/v4/app/globals.css` is the docs site's own theme and *has* diverged from the
registry — its light `--foreground` is `oklch(0% 0 0)` where the registry says `oklch(0.145 0 0)`.
Checking it produces a false drift alarm. `apps/v4/registry/themes.ts` is what ships and what the
extractor reads.

## What Is A Gate, And What Is Not

| Piece | Runs | Fails a build? |
|---|---|---|
| `ShadcnReferenceTokenExpandedTest` | with the suite | **yes** |
| `verify_shadcn_reference.sh` check 1 (stale) | on demand | **yes**, exit 1 |
| `verify_shadcn_reference.sh` check 2 (upstream) | on demand | no, reports |
| `capture_shadcn_local.py`, `tools/shadcn/reference-app`, `shadcn_parity_*.json` | **manually, by a human** | **no** |

The pixel layer is **an investigation tool, not a gate**. It has no CI reference, no Gradle task and
no Kotlin test driving it, and that is stated here rather than left to be discovered: tooling that
looks like a gate and is not is worse than tooling that says it is manual. Use it to find out *why*
a component looks wrong; do not expect it to tell you *that* one does.

## Bumping The Pinned SHA

1. Pick a new commit on `shadcn-ui/ui`'s `main` (e.g. `git ls-remote https://github.com/shadcn-ui/ui.git HEAD`).
2. Edit `PINNED_SHA` in `tools/shadcn/fetch_shadcn_reference.sh`.
3. Re-run `tools/shadcn/fetch_shadcn_reference.sh` (moves the checkout), then
   `python3 tools/shadcn/extract_shadcn_tokens.py` (regenerates `ShadcnReferenceTokens.kt`).
4. Diff the regenerated file. If any token values changed upstream, re-run
   `ShadcnReferenceTokenExpandedTest` and reconcile `KNOWN_DRIFTED` -- a token that was locked
   drift may now match (delete its entry) or a previously-matching token may now need one added.
5. Re-run the wider parity/theme suites this pipeline feeds (see Depends On below) before
   relying on the bump.

## Depends On This Pipeline

- `ShadcnReferenceTokenExpandedTest.kt` / `ShadcnReferenceTokenTest.kt`
  (`:awake:ui:shadcn`) -- token-level OKLCH ground truth.
- `docs/reference/ui-validation.md`'s "Investigating Extra Space Reports" absolute-check step --
  points here for the shadcn reference location instead of a manual `/tmp` clone.
- Any future `ShadcnParityScreenshotTest`-style pixel-baseline work that wants a real rendered
  shadcn page to diff against, not just token values, should clone/build from the same pinned
  `third_party/shadcn-ui-ref/` checkout rather than a second ad hoc clone.

## Non-Goals

- This pipeline establishes ground truth and detects drift. It does not fix drifted production
  values -- see `ShadcnReferenceTokenExpandedTest.kt`'s `KNOWN_DRIFTED` worklist for what still
  needs a values-fix pass.

## What Is Actually Verified (2026-08-08)

Read this section before assuming "shipped" means "checked against real shadcn." It does not,
for the style presets -- and did not, for six of seven base colors, until this pass.

**Verified against the real pinned reference:**

- All 7 `ShadcnBaseColor` values (`Neutral`, `Stone`, `Zinc`, `Mauve`, `Olive`, `Mist`, `Taupe`),
  in both light and dark, are diffed token-by-token against `ShadcnReferenceTokens.BY_BASE_COLOR`
  in `ShadcnReferenceTokenExpandedTest.kt`. Before this pass only `Neutral` had ever been
  checked.
  - `Neutral` matches the reference with zero drift in both modes (hue=0, chroma=0 -- the
    wave-2a value-fix pass already aligned it).
  - The other 6 base colors all have real, previously-undetected drift, now locked via
    `KNOWN_DRIFTED` (not fixed -- see Non-Goals) so the suite stays green as a regression lock.
    Drifted tokens cluster consistently: `primary`/`secondary`/`muted`/`accent` (plus their
    `-foreground` pairs), `ring`, `card`/`popover`/`sidebar`, and the `sidebar-*` mirrors of the
    above -- root cause is `ShadcnTheme.kt`'s `createPalette` deriving every token for a base
    color from ONE fixed hue/chroma pair, while real shadcn hand-tunes a slightly different
    hue/chroma per token within the same base color. See `ShadcnReferenceTokenExpandedTest.kt`'s
    class doc for the full explanation and worked example.
- `ShadcnStylePreset.Vega`'s `baseRadius` is pinned directly to shadcn's real `--radius`
  (0.625rem = 10dp) in both `ShadcnReferenceTokenExpandedTest.kt` and
  `ShadcnStylePresetVerificationTest.kt`. Vega is the only preset with a correctness obligation
  to the reference (new-york-v4, `--radius: 0.625rem`) -- see below for why the other 7 have none.

**Verified structurally, but with NO upstream reference (there isn't one):**

- All 8 `ShadcnStylePreset` values (`Vega`, `Nova`, `Maia`, `Lyra`, `Mira`, `Luma`, `Sera`,
  `Rhea`) are Awake-original density/radius bundles with no shadcn counterpart --
  `ShadcnStylePresetVerificationTest.kt` checks what CAN be checked without inventing a fake
  reference:
  - the radius scale's additive rule (`sm = base-4`, `md = base-2`, `lg = base`, `xl = base+4`,
    clamped at 0) holds for all 8, not just Vega.
  - every preset's own metrics are positive and sensibly ordered (`fieldPaddingX >
    fieldPaddingY`, `badgePaddingX > badgePaddingY`, `fieldPaddingY >= inputPaddingY`).
  - no two presets are fully identical (`baseRadius` + metrics + `ringAlphaMultiplier`) --
    `Maia`/`Luma` do share identical metrics (differing only in `baseRadius`/
    `ringAlphaMultiplier`), flagged as a reported-not-fixed suspicious finding, not a failure.
  - all 8 presets x all 7 base colors x both modes (112 combinations) resolve a complete theme
    without throwing, with a few cheap well-formedness checks (radius token identity, alpha
    channels in range) on top.

**The component reference is hand-copied and ungated.** `tools/shadcn/reference-app/src/ui/*.tsx`
were copied in by hand, not extracted from the pinned checkout, and **11 of the 26 have drifted** --
`skeleton` uses `bg-muted` where upstream uses `bg-accent`, and `toggle` is missing a whole group of
classes. Every screenshot-based parity number is measured against that. Tokens are machine-extracted
and gated; components are neither. See
[`docs/tasks/2026-08-23-vendor-the-reference-app-components.md`](../tasks/2026-08-23-vendor-the-reference-app-components.md).

**NOT verified, and not claimed to be:**

- `ShadcnAccent` (the 17 non-`Base` accent overrides) has no reference-token coverage at all --
  those are hand-picked Tailwind palette hex values with their own doc-comment provenance, not
  derived from shadcn's theme registry, and this pipeline only covers `themes.ts`'s base-color
  entries.
- The `KNOWN_DRIFTED`-locked tokens above are locked to their CURRENT (wrong) values, not
  verified correct -- "asserted" here means "the drift is detected and pinned so it can't get
  worse silently," not "matches real shadcn."
- Pixel-level rendering (actual glyph/spacing/shadow output) is a separate concern from token
  values -- see the "Depends On This Pipeline" section above for where a future pixel-baseline
  effort would plug in; nothing here proves a component *renders* using these tokens correctly.
