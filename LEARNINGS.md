# Learnings

What building this engine actually taught me, kept separate from the README because it is not
documentation of what Awake *is* — it is what I would tell myself before starting again.

Each of these cost a rewrite to learn. None of them is a preference.

---

## 1. Split game-authored content from the backend

A game describes *what* to draw. A backend decides *how*. The moment those two touch, the game
stops being portable and the backend stops being replaceable — and neither failure announces
itself, because everything still renders on the machine that wrote it.

The tell is a game or sample file that knows a backend noun. A `VkImage`, a `GPUBuffer`, a
descriptor set, a command encoder — any of them appearing above the render layer means the split
has already leaked, and every later platform pays for it.

This is why the repo has a `framework ↔ game` boundary at all
([`docs/reference/framework-game-boundary.md`](docs/reference/framework-game-boundary.md)): so
"does this belong in the engine or the game?" is a question with an answer instead of a habit.

**What I got wrong first:** treating the split as an organisational nicety — a tidier folder
layout — rather than the thing that decides whether a second backend is a project or a rewrite.
It is the latter. Adding WebGPU next to Vulkan is only tractable because the games above never
learned either one's vocabulary.

---

## 2. Use RHI layering — three layers, not two

"Split the backend out" is not enough on its own, because it leaves the shared rendering
*algorithms* with nowhere to live. They end up copied into each backend, and two copies of a
vertex writer drift the first time one is fixed.

Three layers, each with one job:

| Layer | Owns |
|---|---|
| `render:contract` | Backend-neutral contracts, GPU data shapes, vertex semantics and formats. Faced by `GpuDevice` — this is the RHI |
| `render:passes` | Shared algorithms: vertex writers, batch coalescing, layout registries, light/shadow view-projection, uniform packing |
| `backend:vulkan` / `backend:webgpu` | **Only** driver-specific bindings and GPU resource allocation |

The rule that makes it hold: **anything symmetric between two backends is a bug in the layering,
not a coincidence.** If Vulkan and WebGPU both need it, it belongs one level up. Writing it twice
is how a stride, an offset or a colour ends up hardcoded in one backend and derived in the other.

See [`docs/reference/render-hardware-interface.md`](docs/reference/render-hardware-interface.md)
for the boundary, and `docs/reference/backend-commonisation.md` for what is still duplicated —
that second file existing at all is the honest part.

**What I got wrong first:** collapsing this to two layers, contract and backend. The shared
algorithms then had to live *somewhere*, so they lived in whichever backend was written first,
and the second backend copied them. Every duplicated line was written deliberately, by someone
who could see the original, and it still drifted.

---

## 3. Learn immediate mode and retained mode — and notice Compose is both

This is the one I most wish I had understood up front, because it is not really about UI.

**The mistake is thinking it is one choice.** It is two, and they are independent:

- **How the UI is authored** — do you describe the whole UI every frame, or mutate a tree of
  objects that persists?
- **How the UI is executed** — is the layout tree rebuilt from nothing each frame, or retained
  and selectively updated?

I picked immediate-mode *authoring* (describe everything each frame — which I still think is
right for a game engine, where a 3D scene is redrawing at 60 fps regardless) and assumed that
forced immediate-mode *execution*. It does not.

The cost of assuming it did, measured on one screen:

| | Measured |
|---|---|
| Trial measure passes per frame, ui-showcase shell | **7,384** |
| Fixed overhead per trial pass | ~1,360 bytes |
| Allocation per frame, 60 plain surfaces | **1,151,016 bytes** (~68 MB/s at 60 fps) |

Those numbers come from a container having no way to learn its own size except by *re-executing
its own content lambda* and throwing the result away. Nesting multiplies it. That is not a
tuning problem; it is the execution model.

**Compose is the proof that the two choices separate.** You author it immediate-mode — a
function that describes the whole UI, called again when anything changes, with no widget handles
to mutate. It executes retained — a node tree persists across frames, recomposition touches only
what changed, and measure/layout/draw walk that tree instead of re-running your code to discover
it. Immediate to write, retained to run.

That is the hybrid, and it is the whole trick. Flutter makes the same split. Once you see it,
"immediate vs retained" stops being a side you pick and becomes two questions you answer
separately.

`awake:compose` ([`docs/reference/compose-engine/`](docs/reference/compose-engine/)) is this
learning applied: the same authoring feel, a retained tree underneath, every child measured
exactly once.

**The general lesson, past UI:** when a model imposes a cost, check whether the cost belongs to
the part you actually chose. I chose an authoring style and inherited an execution strategy I
never evaluated — and then spent a long time optimising constants inside it, which is the
expensive way to discover the constant was never the problem.

---

## The pattern under all three

Every one is the same shape: **two things that looked like one thing.**

- Game content and backend — one "rendering" concern, until it needs a second backend.
- Contract and shared algorithms — one "not-the-backend" pile, until it needs a second backend.
- Authoring model and execution model — one "immediate mode" decision, until you measure it.

The failure mode is always the same too: it works, so nothing objects. A leaked backend type
still renders. A duplicated vertex writer still draws. A trial-measure pass still produces the
right layout. Each one is only visibly wrong later, from a direction you were not looking —
a new platform, a fixed bug that only got fixed once, a frame budget that ran out.

So the habit worth keeping is not "layer everything". It is: **when something works, ask which
two decisions it just merged.**
