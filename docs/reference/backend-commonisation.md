# Backend commonisation: where the duplication actually is

Vulkan and WebGPU are hand-authored side by side. This is the measurement of how much, where,
and which parts can realistically be shared — so the next pass is chosen on evidence rather than
on which file happened to annoy someone.

Re-measured 2026-09-11 (was 2026-08-23). Kotlin lines in `commonMain`/`wasmJsMain`, tests and
`build/` excluded. Re-measure with `python3 tools/loc_survey.py`; do not trust these numbers after
a large refactor.

## Distribution

| package | Vulkan | WebGPU | combined | shareable? |
|---|---:|---:|---:|---|
| `renderer/` | 2,543 | 1,702 | **4,245** | mostly — same algorithm, two spellings |
| `pipeline/` | 1,545 | 1,297 | 2,842 | the *decisions* yes, the struct-building no |
| `mesh/` | 787 | 461 | 1,248 | packing yes, allocation no |
| `debug/` | 643 | 219 | 862 | yes — three more pipelines of the same shape |
| `ui/` | 1,008 | 553 | 1,561 | partly done already via `render:passes2d` |
| `texture/` + `material/` | 1,293 | 532 | 1,825 | mip/format logic yes, upload no |
| `application/` | 1,194 | 809 | 2,003 | partly — wiring, not device setup |
| `device/` + `swapchain/` + `commands/` | 769 | 231 | 1,000 | **no** — this *is* the API |
| **total per-backend** | **9,782** | **5,804** | **15,586** | |

Shared render code (`render:contract` + `render:passes` + `render:passes2d`): **7,588**.

- Commonised, whole render stack: **7,588 / 23,174 = 32.7%**
- Excluding `device`/`swapchain`/`commands`: **34.2%**
- Counting only packages with a real counterpart on both sides (`renderer/` + `pipeline/`):
  7,588 / 14,675 = **51.7%**

Movement since 2026-08-23: shared code is now 7,588 lines and the whole-stack figure is 32.7%.
The denominator also grew as generic packet execution, explicit binding metadata, and feature
seams became real code. Both backends still retain native allocation and command encoding; the
remaining commonisation work is the source-resource preparation seam and feature-family parity,
not moving driver API wrappers into `render:passes`. The per-package table is generated from the
current tree by `tools/loc_survey.py`; rerun it after a large refactor. The content-feature provider
list still adds a per-backend `*ContentFeature` and `SkyboxContentFeature`; those files remain
ledger entries for phase 3 of
[the content-split plan](../tasks/2026-08-23-backend-content-split-plan.md) deletes. A pass that
moves the number the wrong way is worth recording, not hiding.

## Read the percentage carefully

Deduplication shrinks the denominator as well as the numerator, so the ratio understates the
win. Commonising `renderer/` would delete roughly a thousand duplicated lines and move the whole
figure from ~21% to only ~29%.

The number that matters is **how many places must change to add one feature.** Transparency is
the worked example: adding it took one edit on Vulkan (which had a declarative request list) and
three copy-pasted call sites on WebGPU (which did not) — and WebGPU silently shipped without a
transparent pipeline at all for as long as Vulkan had one. That is the failure mode
commonisation prevents; the ratio is only a proxy for it.

## Ceiling

`device/`, `swapchain/`, `commands/`, and the raw struct-building inside
`pipeline/`/`texture/`/`mesh/` are the actual Vulkan and WebGPU APIs. Estimated 3,000–3,500
lines that cannot be shared under any design. A realistic ceiling is **55–65% commonised**.

## Ranked plan

1. **`renderer/` draw preparation** (4,245 lines) — the single largest win. Both sides walk draw
   calls, resolve a pipeline, pack uniforms, sort, and record. The port already exists
   (`CommandRecorder`) and the shared bodies are started (`SharedOpaqueRenderFeature`,
   `SharedTransparentRenderFeature`), so this is finishing a migration, not starting one. Its own
   multi-day pass.
2. **`debug/`** (862) — line, skybox and particle pipelines are the same shape
   `PipelineSpec`/`PipelineFactory` already handles. Cheap once that machinery exists.
3. **`mesh/` packing** (1,248) — instance/vertex buffer packing is arithmetic; only the
   allocation call differs.

## The pattern to use — and the one to avoid

**Use a port plus shared logic.** An interface the backend implements (`CommandRecorder`,
`PipelineFactory`), with the algorithm written once in `render:passes`/`render:contract` and
injected. Both backends supply only the primitives that genuinely differ.

**Do not reach for `expect`/`actual` to force symmetry.** It looks like the KMP-native answer and
is the wrong tool here:

- It enforces that both sides implement the same signatures. It shares no logic at all — you
  still write the body twice. It converts duplication from accidental to mandatory rather than
  removing it.
- It resolves per KMP *target*, not per *backend*. The two nearly coincide today (Vulkan on
  desktop/Android, WebGPU on wasmJs) but that is a coincidence of the current wiring, not a
  property worth encoding in the type system — a second desktop backend would have nowhere to go.
- The compile error it buys is already available, more cheaply, from an interface a backend must
  implement.

`expect`/`actual` stays right where this repo already uses it: platform primitives with one-line
bodies (`readResourceBytes` and friends).

## Re-measuring

```bash
python3 tools/loc_survey.py
```

Prints the per-package split, the shared total and the whole-stack percentage in one go.

This replaces three hand-run `find | xargs wc -l` commands that had gone stale: the WebGPU one
counted `src/wasmJsMain` only, and that backend now keeps most of its code in `src/commonMain`, so
it under-reported. The script walks the whole `src/` tree and excludes `build/` and tests by name,
which is the rule this doc states at the top.
