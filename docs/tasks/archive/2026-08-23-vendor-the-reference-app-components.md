# 2026-08-23: the parity reference is hand-copied, and 11 of 26 have drifted

Status: **fixed 2026-08-23** — generator `7ba0c8b0d`, re-vendor `7d81b794b`. See what it exposed, below.

## What the reference app actually is

`tools/shadcn-reference-app/` renders real shadcn components so `capture_shadcn_local.py` can
screenshot them, and those screenshots are the reference every parity comparison is measured
against.

It is a self-contained Vite app. It imports **nothing** from `third_party/shadcn-ui-ref/` — the 26
files in `src/ui/*.tsx` were **copied in by hand**.

## The finding

Compared against the pinned checkout, normalising only shadcn's path aliases
(`@/registry/new-york-v4/ui/…` → `./…`, `@/lib/utils` → `../lib/utils`):

| | Count |
|---|---|
| Differ by import alias only — mechanically equivalent | **15** |
| **Real content drift** | **11** |

Drifted: `alert` `avatar` `breadcrumb` `button-group` `collapsible` `dropdown-menu` `kbd` `popover`
`skeleton` `textarea` `toggle`.

Two examples, both material:

- **`skeleton.tsx`** — ours: `bg-muted`. Upstream: `bg-accent`. A **different colour token**, so every
  skeleton parity number is measured against the wrong background.
- **`toggle.tsx`** — ours is missing `whitespace-nowrap`, `transition-[color,box-shadow]`,
  `outline-none` and the whole `aria-invalid:*` group. A stale copy of an older shadcn.

## Why this matters more than it looks

The token pipeline is machine-extracted from a pinned commit and gated
(`verify_shadcn_reference.sh`). The **component** reference is hand-copied and gated by nothing.
Same project, same upstream, two completely different standards of evidence — and the ungated half
is the one the screenshots come from.

A parity number computed against a drifted reference is worse than no number: it looks like
evidence.

## The fix, and it is the "template" idea pointed at the right thing

Do not template the app. Template the **vendoring**.

15 of the 26 already differ only by import alias, which proves the transform is mechanical:

```
@/registry/new-york-v4/ui/<name>   ->  ./<name>
@/lib/utils                        ->  ../lib/utils
@/registry/new-york-v4/lib/utils   ->  ../lib/utils
```

So:

1. A generator copies `src/ui/*.tsx` from the pinned checkout, applying that rewrite.
2. `verify_generated.py` gains a row for it, so a drifted vendor fails like a stale token table does.
3. The app becomes what it should have been: boilerplate plus generated components, with only
   `cases.tsx`, `App.tsx` and the config hand-written.

That also removes the "vendored by hand" caveat from the pipeline doc, and puts the component
reference on the same footing as the token reference.

## What has to be decided, not just done

**Re-vendoring changes the reference, which changes every parity number that uses it.** That is a
visual decision with its own review, exactly like bumping `PINNED_SHA`. The generator should land
first and the re-vendor second, so the diff is reviewable as "what the reference was wrong about"
rather than arriving mixed into a tooling change.

Which is why this is recorded rather than fixed in passing.

## What the re-vendor exposed

Done: generator in `7ba0c8b0d`, re-vendor + recapture in `7d81b794b`. That commit said the parity
tests could not be run against the new captures because `samples:ui-showcase`'s test source did not
compile; `17e3d2fc6` fixed that, so here is what they actually say.

**49 tests, 3 failures — and two of them are the drift, now visible.**

| Test | Reference before | Reference after | Awake | Cause |
|---|---|---|---|---|
| `kbdStatesStyleMatchesShadcn` | radius **4** | radius **6** | 4 | **the drift.** Passed before by agreeing with a wrong reference |
| `toggleButtonGeometryMatchesShadcn` | `px-3` (12px) | `px-2` (**8px**) | 12px | **the drift.** Upstream's toggle is narrower; ours is stale |
| `dropdownMenuGeometryMatchesShadcn` | has `.trigger` | has `.trigger` | **emits none** | **not the re-vendor.** Awake-side gap; dropdown's diff is className text and `maxHeight` only, no geometry |

This is the doc's own claim demonstrated rather than argued: `kbd` and `toggle` were **green against
a reference that was wrong**. Fixing the reference is what turned them red. That is the gate working
— a parity number computed against a drifted reference looked like evidence, and was not.

The third is unrelated and was merely invisible: the module has not compiled since `464cf8c13`, so
these tests have not run at all, and a pre-existing Awake-side failure had nowhere to surface.

**None of the three is fixed.** They are three real component gaps (`kbd` radius, `toggle` padding,
`dropdown` trigger semantics), each a `ui-designsystem` change with its own visual review. Do not
re-baseline any of them — the reference is now the correct one.

## Not in scope

- `cases.tsx`. Its 28 case ids must match `shadcn_reference_cases.json`, and the capture script
  already fails loudly when they do not, so the duplication is at least armed. Generating it is a
  separate, smaller win.
- Whether the 11 drifts were deliberate. Some may have been worked around for a reason that was
  never written down; the re-vendor review is where that gets established.
