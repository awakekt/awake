# Detekt real findings, 2026-08-30

Detekt was failing 18 modules. The findings were two unrelated kinds sharing one red build: three
actual defects plus stale KDoc, and a long tail of size and complexity debt. This logs the defects
and what turned them up, so the same signal is not dismissed as lint noise next time.

Landed in `e12985da7`, merged as `779c44bb7`.

## The defects

### 1. Growing a Vulkan UI mesh freed buffers the GPU was still reading

`awake/backend/vulkan/.../ui/DynamicMesh.kt`

Grow-on-demand replaced the buffer in **every** frame slot, but waited on only the current frame's
fence first. A slot an in-flight frame still referenced was freed underneath it.

The symptom does not point at the cause: nothing fails at the growth. Corruption or a lost device
turns up a frame or two later, in whatever draws next.

Found because `UnusedParameter` flagged the `frame` argument. Reallocation never consulted it —
which is precisely the bug, since `frame` is the only thing that could have narrowed the work to
one slot. Fixed by waiting on the device before touching the slots; `frame` is gone.

**The KDoc argued the code was safe on grounds the code did not meet.** `growTo` was introduced in
`7417f4265` (this session's grow-on-demand work, so the defect is mine) documented as:

> Safe to free here because the buffers are per-frame-in-flight and the caller has already waited
> on this slot's fence [...] Doing this anywhere else would free memory a queued frame is still
> reading.

The body freed `frameResources.forEach { slot -> ... }` — every slot, not "this slot". The unused
`frame` parameter is the fossil of the single-slot design the comment describes. A safety argument
naming a specific precondition is worth checking against the body; this one had been read past
several times, and only an unused-parameter warning caught it.

### 2. Textured draw runs kept a clip every other primitive drops

`awake/engine/render/passes2d/.../DrawRunCoalescer.kt`

`buildTextureRun` was the one primitive not consulting `canSkipExactClip`. A textured run kept an
exact clip its neighbours had already dropped, which breaks the batch they would otherwise have
merged into. Quads, rounded quads and glyphs all did the check.

### 3. Two showcase stubs computed something and dropped it

`samples/ui-showcase/.../ShowcaseShell.kt` built a `Modifier` and never applied it, so the hero had
no width or minimum height. `.../pages/layout/AspectRatioPage.kt` took a `state` it never read.

Both from `UnusedPrivateMember`/`UnusedParameter`. Neither crashes; both silently render wrong.

## What the other 17 findings were

Stale KDoc: parameters renamed, added, or documented under the wrong tag. Detekt's
`OutdatedDocumentation` rule is stricter than "document every parameter" — the rule it actually
enforces is now written up in [kdoc-guidelines.md](../kdoc-guidelines.md) §4B, because it took
several wrong guesses to pin down:

> A primary-constructor parameter is `@property` iff it is `val`/`var` **and not private**, and
> `@param` otherwise. Type parameters take `@param`. All tags appear in one merged
> declaration-order sequence, not grouped by tag.

`private val` reading as `@param` is the non-obvious half, and is why `PerFrameUniformSlots` kept
failing after the terrain config classes passed.

## Why the rest went to baselines

The remaining 90 findings are `LongMethod`, `LongParameterList`, `TooManyFunctions`,
`CyclomaticComplexMethod`, `LargeClass` and similar. Real debt, but not defects, and refactoring
them under a red build would have mixed genuine fixes into a mechanical sweep.

Eleven baselines were regenerated and seven modules that never had one now do. Several **shrank**
(`core:text` 23 → 11, `samples:ui-showcase` 8 → 4): they held signatures for code that has since
changed, and that staleness is why those modules were failing at all — a stale entry suppresses
nothing while the finding it was written for goes unsuppressed.

`ForbiddenComment` on the `AspectRatio` stub is baselined rather than reworded. It marks genuinely
unfinished work; rephrasing it to dodge the rule would delete the signal and keep the debt.

## Verification

`./gradlew detekt` exits 0.

A green build after adding baselines proves nothing on its own — a baseline that swallowed
everything would look identical. Control: a freshly added `// TODO` fails `:awake:core:text:detekt`
(exit 1); removing it passes (exit 0). New findings still fail.

Two measurement traps hit along the way, both worth remembering:

* The first list of failing modules was read from `build/reports/detekt/detekt.xml` files left over
  from earlier runs. Up-to-date tasks do not rewrite their report, so the stale ones named modules
  that already passed. Delete the reports before trusting a sweep.
* `diff` under the rtk wrapper reported two baseline files identical when `git diff` showed 4 added
  and 3 removed lines. Do not ground a correctness claim on it.
