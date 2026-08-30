# What nothing checks

Status: **in progress. Step 1 done, 2-4 open.** Written 2026-08-24.

One session surfaced four separate source sets that had silently stopped compiling, a
767-finding audit nobody reads, and a hot-path rule enforced only by memory. Different symptoms,
one shape: **a rule with no gate decays to a rule nobody follows, and looks exactly like a rule
being followed.**

This plan is about the gates, not about the findings. Fixing an instance is a commit; fixing the
class is this.

## The evidence

| What rotted | How long | Why nothing said so |
|---|---|---|
| `awake:ui:testing` commonTest | unknown | 75 tests stopped running; a suite that does not compile reports nothing, not zero |
| `:awake:engine:app` | since the engines moved to `ScenePipeline` | no module depended on it, so no task built it |
| `samples/ui-showcase` wasmJs | unknown | its own CI job existed — the job just never ran |
| `SceneAppFrame.kt` | caught same-day | deleted as "unused": its only caller is a test, and no loop compiles test sources |

The third one is the sharpest. A CI job *did* cover it. CI was pinned to `push: branches: [main]`
while every commit landed on `dev/**`, and `main` had not moved in two weeks — **927 commits**.
So the gate existed, was correct, and was never invoked.

## Steps

**1 — CI runs, and builds what nobody names. Done (`cf60f07cc`, `3e3810634`).** Trigger now
includes `dev/**`. Two steps were added that name no module, so a new one is covered the day it
exists:

```bash
./gradlew compileTestKotlinDesktop   # every desktop test source set
./gradlew compileKotlinWasmJs        # every wasmJs source set
```

Compile-only on purpose: running the suites pulls in `:awake:backend:vulkan:desktopTest`, which
needs the native library and lavapipe that only the `desktop-vulkan-smoke` job installs.

Both were verified green before enabling — a first run after two weeks dark that goes red for
pre-existing reasons gets ignored, which is the same failure in a new place. `samples:studio`'s
53 tests also joined CI; no step had named that module at all.

Step 1 paid for itself within the hour: `compileTestKotlinDesktop` caught `SceneAppFrame`'s
deletion before it left the branch.

**2 — the 767-finding audit. Open, and it needs triage before it needs a gate.**

`hooks/pre-commit-audit.sh` used to run it and was disabled 2026-08-22, with the reason recorded
in the file: it scans the whole repo (~2m05s against a 20s alarm) and always printed "found
issues or timed out", which is true either way and so carries no information. *"A warning that
always fires trains you to stop reading warnings."* It also pre-declined the CI variant: 661
findings then would land it permanently red.

It is **767 now**. The backlog grew while nobody read it, which is the same decay in a slower
form. Two things have to happen in order, and the first is not a gate:

- Triage by severity, not by count. Today's 2 HIGH are `Mat4` (792 lines, a math primitive whose
  function count is inherent) and an ECS *benchmark* class. If the top of the list is noise, the
  threshold is wrong, not the code.
- Only then wire it, and only at a severity the repo actually intends to hold.

Do not wire it first. That reinstates the always-fires failure the hook's own comment documents.

**3 — the per-frame allocation rule. Open, unenforced.**

`skills/awake-core-math` bans allocation on the render path. Nothing checks it: no detekt rule,
no build-logic task. It is a review rule, which means it holds exactly as long as reviewers
remember it.

One live instance, found while auditing `createBackendResources`:

```kotlin
final override fun update(delta: Float) {
    val (width, height) = viewportSize()   // Pair<Float, Float>, every frame
```

`GraphicsEngine.update` allocates a `Pair` per frame. The lambda itself is right — the value must
be read live because the swapchain resizes, and the shared layer cannot name `SwapchainManager`.
Only the `Pair` is wrong. Fix is `() -> Float` twice, or writing into a reusable holder; it
touches the base class and both engines.

Worth knowing before anyone reaches for a detekt rule: "allocates in a hot path" is not
statically decidable in general. What *is* checkable is narrower and probably enough — a rule
over `Pair`/`Triple` construction and boxed collections inside `update`/`render`/`recordCommands`
bodies. Scope it to that or it will produce another 767.

**4 — a module with no consumers. Open, no obvious gate.**

`:awake:engine:app` sat broken because nothing depended on it. Step 1's aggregate compiles cover
this for the two source-set kinds they name, but the general form — a module in
`settings.gradle.kts` that no task and no dependency reaches — has no check. The honest options
are a periodic `./gradlew build` that nobody watches, or accepting that an unreferenced module is
debt and deleting it on sight, which is what happened here.

## Not doing

- **A blocking pre-commit audit.** See step 2 — the previous attempt is documented in the hook
  itself, and the backlog it choked on has since grown.
- **`./gradlew check` at the root in CI.** Same reasoning: unmeasured, and likely red on arrival.
  Measure first; the two aggregate compiles were measured green before landing, which is why they
  could land.
- **Fixing the 767 findings.** This plan is about gates. Triage is step 2's first half and is a
  separate piece of work.

## Verification bar

```bash
./gradlew compileTestKotlinDesktop compileKotlinWasmJs detekt
```

Plus the rule this whole plan exists to state: **before adding a gate, run it and confirm it is
green.** A gate that fails on arrival for unrelated reasons is indistinguishable from no gate
within two weeks.
