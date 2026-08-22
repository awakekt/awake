# Backend commonisation: where the duplication actually is

Vulkan and WebGPU are hand-authored side by side. This is the measurement of how much, where,
and which parts can realistically be shared — so the next pass is chosen on evidence rather than
on which file happened to annoy someone.

Measured 2026-08-22. Kotlin lines in `commonMain`/`wasmJsMain`, tests and `build/` excluded.
Re-measure with the commands at the bottom; do not trust these numbers after a large refactor.

## Distribution

| package | Vulkan | WebGPU | combined | shareable? |
|---|---:|---:|---:|---|
| `renderer/` | 2,033 | 1,461 | **3,494** | mostly — same algorithm, two spellings |
| `pipeline/` | 1,229 | 662 | 1,891 | the *decisions* yes, the struct-building no |
| `mesh/` | 777 | 434 | 1,211 | packing yes, allocation no |
| `debug/` | 729 | 300 | 1,029 | yes — three more pipelines of the same shape |
| `ui/` | 719 | 310 | 1,029 | partly done already via `render:passes2d` |
| `texture/` + `material/` | 1,075 | 348 | 1,423 | mip/format logic yes, upload no |
| `application/` | 485 | 679 | 1,164 | partly — wiring, not device setup |
| `device/` + `swapchain/` + `commands/` | 574 | 158 | 732 | **no** — this *is* the API |
| **total per-backend** | **7,621** | **4,352** | **11,973** | |

Shared render code (`render:contract` + `render:passes` + `render:passes2d`): **3,115**.

- Commonised, whole render stack: **3,115 / 15,088 = 20.6%**
- Excluding `device`/`swapchain`/`commands`: **21.7%**
- Counting only packages with a real counterpart on both sides (`renderer/` + `pipeline/`):
  3,115 / 8,576 = **36.3%**

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

1. **`renderer/` draw preparation** (3,494 lines) — the single largest win. Both sides walk draw
   calls, resolve a pipeline, pack uniforms, sort, and record. The port already exists
   (`CommandRecorder`) and the shared bodies are started (`SharedOpaqueRenderFeature`,
   `SharedTransparentRenderFeature`), so this is finishing a migration, not starting one. Its own
   multi-day pass.
2. **`debug/`** (1,029) — line, skybox and particle pipelines are the same shape
   `PipelineSpec`/`PipelineFactory` already handles. Cheap once that machinery exists.
3. **`mesh/` packing** (1,211) — instance/vertex buffer packing is arithmetic; only the
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
find awake/engine/render/contract/src/commonMain awake/engine/render/passes/src/commonMain awake/engine/render/passes2d/src/commonMain -name "*.kt" | xargs wc -l | tail -1
```

```bash
find awake/backend/vulkan/src/commonMain -name "*.kt" | xargs wc -l | tail -1
```

```bash
find awake/backend/webgpu/src/wasmJsMain -name "*.kt" | xargs wc -l | tail -1
```
