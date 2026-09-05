# Behavior Tree & State Machine Plan — one hybrid runtime, and where an LLM fits

Date: 2026-08-30
Status: Phase 0 done and its gate reached — three hand-written behaviours exist and hand-writing them
is still comfortable, so Phases 1–5 stay unstarted by design, not by neglect. Rung 2 is done too:
`patrol`, `chase` and `flee` are `scene.json` components and the inspector edits them live. What
remains is rung 3, gated on the trigger stated below — someone who cannot rebuild needing to change
*which* behaviour runs, not what its parameters are.

Answers a question asked while world streaming is mid-flight: what shape should NPC decision-making
take, and how does an LLM enter without landing in the frame loop. It deliberately does **not**
schedule work ahead of the terrain/streaming sequence [D28](../decisions/D28-open-world-framework-boundary.md)
fixed; Phase 0 below is the only part worth doing before that lands.

## The correction this research forces

**Do not build a behavior tree framework and a state machine framework.** That was the implied ask,
and it is the expensive version of the answer. Every serious 2024–2026 implementation converged on
one hybrid runtime, because BTs and FSMs each own one half of the same problem:

- BTs are good at **hierarchical selection** — descend, test conditions, pick a branch.
- FSMs are good at **explicit flow control** — "when X happens, go to Y."

Unreal's StateTree is the cleanest shipped statement of this: BT *selectors* plus FSM *states and
transitions*, with no decorators and no blackboard clutter, and with mutable per-instance data
separated from stateless execution logic — which is precisely what lets it run on Mass entities
rather than one actor at a time. That separation is the part that matters for us, because our ECS
has the same constraint.

One runtime. Two vocabularies fall out of it for free: a tree with only leaf states and completion
transitions *is* a behavior tree; a flat tree of siblings with tick transitions *is* a state
machine. Authors pick a dialect; the engine ticks one thing.

## Prior art, and what each one is actually for

| Approach | Good at | Fails at | Verdict here |
|---|---|---|---|
| **FSM / HFSM** | Small, explicit, debuggable behavior | Transition explosion; Halo 2 shipped hundreds of states and Isla's GDC 2005 talk is largely about that cost | Subsumed — it is the transition half of the hybrid |
| **Behavior tree** | Readable hierarchy, reactive, scales in authoring | Full-tree re-traversal, decorator/abort semantics, no model for *improving* a decision | Subsumed — it is the selection half |
| **StateTree-style hybrid** | Both of the above, parallel-safe instance data, best debugger story | Newer, less folklore | **Build this** |
| **Utility / scoring** | Extending behavior without breaking relationships — scorers stack, no transitions to rewire | Tuning is opaque; "why did it do that" is harder | Add later as a *selector variant*, not a second system |
| **GOAP / HTN** | Emergent, non-authored action sequences (F.E.A.R.: planner over a 3-state FSM) | Computationally expensive; needs a tuned world-state representation | **No.** Revisit only on the trigger below |

Trigger for revisiting planners: when a designer asks for a sequence *nobody authored* and the
answer cannot be expressed as an ordered set of enter conditions. Until that request exists,
planning is cost with no buyer.

## The four things that break behavior trees, and the counter-design

These are the recurring failure modes across Unreal's own forums, the *Game AI Pro* pitfalls
chapter, and Bungie's five-year retrospective. Each maps to a design rule, not a later fix.

1. **Re-traversing the whole tree every tick.** Deep trees make this quadratic in practice.
   → Memoize the active root-to-leaf path; re-select only on a transition trigger or state
   completion. Event-driven by construction, not as an optimization pass.
2. **Restart storms.** A decorator that blocks its child counts as a failure, the tree resets to
   the root, and immediately re-runs the same task. Unreal ships this footgun and the community fix
   is an event dispatcher.
   → No decorators. Enter conditions gate *selection*; transitions are the only re-entry mechanism,
   and they evaluate leaf→root so a specific state overrides a general one and shared escape
   hatches ("damaged → Flee") hoist to a parent instead of being duplicated per child.
3. **The tree becomes an undocumented blackboard.** Bungie's own conclusion — Halo's trees drifted
   into a blackboard system nobody had formalised, and the fix list was masks, stimuli, and a
   better GUI.
   → Typed blackboard from day one, engine-owned storage, consumer-owned key vocabulary. And a
   debugger before the third behavior, not after the thirtieth.
4. **Runtime tree mutation.** The flattened shared-shape layout that makes ticking cheap is exactly
   what makes structural edits at runtime hard (EntitiesBT documents this trade openly).
   → Accept it. Trees are immutable assets; variation comes from blackboard state and enter
   conditions, which is what Halo 3 converged on too (static trees + masks + stimuli).

## What already exists here

- Three behaviours ship in `awake/scene/scene-core/.../scene/ai/`: `ChaseAiSystem`, `PatrolAiSystem`
  and `FleeAiSystem`, each query-driven over an ECS family and sharing `RouteFollower`. Phase 0's
  ceiling — one system instance per NPC, constructed with `npcTransform` and `targetTransform` — is
  gone.
- Navigation is complete: `NavGrid` over one baked tile, `StreamedNavGrid` over a tile per world
  cell, and `PathRequestSystem` searching off the frame thread when given a scope. Behaviours ask
  through the `PathRequest` component rather than calling a navmesh, so a movement leaf state has a
  real path source and the long-running-task requirement in the seam section is already satisfied.
- `SceneSystemPhase.Fixed` already exists in `SceneSchedule`, draining through `FixedTimestepLoop`.
  AI registers there and gets determinism for free. **No new scheduling infrastructure is needed.**
- `World` is single-threaded by design and documents itself as not thread-safe.
- `AsyncWorldCellStreamListener` (landed 2026-08-29) is the template for anything asynchronous:
  suspend to produce off-thread with no `World` handed to the suspending half, apply on the frame
  thread. The LLM tier below reuses that shape rather than inventing a second async model.

## Boundary: what could ever belong in Awake

[framework-game-boundary](../reference/framework-game-boundary.md) puts "NPC behavior" in the
consumer repo, explicitly. The line that survives that rule:

- **The machine is engine-shaped**: tick semantics, the shape/instance split, blackboard storage,
  the debugger seam. Neutral vocabulary, same category as `System` itself.
- **The nodes are consumer content**: `AttackTarget`, `FleeToSafety`, `PatrolRoute`, and every
  authored tree. Product vocabulary, fails promotion test #1.

That does not license building the machine in Awake now. One consumer exists, and the limitation
test fails outright: a consumer *can* write this today over public `System`/`World` APIs — nothing
is missing. So the sequence is prototype consumer-side, promote the machine when a second consumer
needs the same stable behavior. `ChaseAiSystem` is the precedent for the promotion, not for
skipping it.

**Hard rule regardless of where it lands:** no HTTP, no LLM client, no network library reaches
`:awake:ecs` or `:awake:scene:scene-core`. Promotion test #5 is not negotiable, and the LLM design
below is built so it never has to be.

## Design sketch

**Shape (shared, immutable, one per asset).** States flattened depth-first into arrays — the layout
Knafla's data-oriented BT work and EntitiesBT both land on. Access order is depth-first, so the
array *is* the traversal order.

**Instance (per entity, one component).** Fixed-capacity active-state path plus task instance data.
No `List`, no per-entity traversal stack — the stack is preallocated on the system, so a tick over
N agents allocates zero. That is the same bar `awake-core-math` already sets for `System.update`,
and the UI side already has an allocation probe to copy for the test.

**Tick.**
1. Evaluate transitions leaf→root; first success wins.
2. On a change, exit old tasks, run selection (enter conditions descend; a leaf that passes is the
   new state), enter new tasks.
3. Tick tasks on every active state root→leaf — so a parent can hold "always running" movement
   while the leaf handles specifics.

**Budget.** Distance-bucketed behavior LOD and a per-tick agent cap. The field guidance is 20–40
simultaneous full-fidelity agents with distant ones on trivial behavior; with streaming landing,
"distant" is already a concept the world system computes.

**Debugger.** A state-path timeline in `:awake:editor`. Cited repeatedly as the single biggest
practical difference between StateTree and Unreal's older BT, and it is cheap while the runtime is
small.

## The navigation seam — separate plan, three couplings

Navigation has its **own plan** — [navgrid-navigation](2026-08-30-navgrid-navigation-plan.md) — not
a section here. They are different problems: this one is control flow, that one is geometry and
search, and bundling them makes a plan that cannot be finished. Nothing to migrate either: the
recast4j implementation is already gone, so "retire the library" was three dead lines in the version
catalog.

What must be recorded *here*, because it constrains the tree runtime's design:

1. **The budget is shared, and it is navigation's.** A tree tick is a walk over a flat array;
   pathfinding is the millisecond cost. `ChaseAiSystem` already knows this — it repaths every 0.5 s
   with a comment saying `findPath` is not cheap enough at 60 Hz for one NPC. So the agent cap and
   behavior LOD buckets above are really *path-request* budgets, and belong to whichever system
   issues them.
2. **Movement is a long-running task, and that is a hard requirement on the runtime.** If a path
   request goes off-thread, the `MoveTo` leaf must survive across ticks in a pending state and be
   cancellable when a transition fires mid-request. A runtime designed for instantaneous tasks
   cannot host it. **This is the one thing that would have to be rebuilt if the two plans were
   written independently**, which is why it is stated now rather than discovered later. The nav plan
   satisfies it with a `PathRequest` component rather than a mechanism invented for the tree.
3. **Nav data is per-cell, like everything else streamed.** A path crossing into an unloaded cell
   needs a coarse cell-level graph plus local refinement — hierarchical, keyed by the existing
   `WorldCellCoord`, not a second spatial scheme.

One contract note, deliberately not acted on: `findPath(start, end): List<Vec3f>` is synchronous and
allocates. Both are wrong for coupling 1 and 2. It should not change until a real implementation
exists to shape it — changing an interface with no implementor is guessing.

## Where an LLM fits, in three tiers

Per-frame LLM control is not a tuning problem, it is arithmetic: 200–800 ms cloud round-trip,
per-token cost across every NPC, and hallucinated output. The consistent industry answer is a
two-layer split — a slow strategic layer that emits *structured commands*, and a fast symbolic
layer that executes them deterministically and owns all game state.

**Tier 0 — authoring time, in the editor. Do this one first.** An LLM drafts a behavior asset from
a natural-language description; the engine validates it against the registered node catalogue and
rejects anything unknown. Zero runtime cost, zero runtime risk, and it attacks the real bottleneck,
which is authoring, not inference. There is a decade of prior work on LLM→behavior-tree generation
to lean on. **The validation step is the whole feature** — the known failure mode is that the model
has no idea which functions exist and will confidently emit an API that does not, so it may only
name IDs the engine enumerates, and an invalid tree is refused rather than partially loaded.

**Tier 1 — runtime director, off the frame thread.** Every few seconds, a snapshot goes out and
structured commands come back as *blackboard writes and goal selections only* — never actions,
never direct entity mutation. Same shape as async cell streaming: suspend to produce, apply on the
frame thread. Constrained decoding to a schema (llama.cpp-style grammars; note that Outlines'
schema compile times, measured in seconds, disqualify it for a real-time loop). Cache aggressively.
A local ~3B model runs an NPC-scale request in roughly 80–110 ms and does not scale in cost with
playtime; cloud is for marquee characters only.

**Tier 2 — dialogue and barks.** A separate concern from decision-making, listed only so it is not
conflated with it. Pre-generate and cache ambient lines. Treat the NPC's context as a permission
system: unrevealed quest state never enters a prompt.

**Never:** an LLM call in the per-frame path, free-form function calling against engine APIs, or an
inference dependency inside core ECS.

## Phases

**Phase 0 — make the existing behavior query-driven. Done.** `ChaseAiSystem`'s constructor
transforms are components and an ECS family.
*Gate:* then write two or three more behaviors by hand. **If hand-writing them is still
comfortable, stop here.** A framework built before three behaviors exist is a framework built for a
consumer that does not exist — the mistake the async-streaming plan already had to correct once.
**Gate reached and it says stop:** patrol and flee were both written by hand without strain, so
Phases 1–3 stay closed until the rung-3 trigger below fires.

**Phase 1 — hybrid runtime, consumer-side. Not started, gated.** Shape/instance split, enter conditions, leaf→root
transitions, typed blackboard. Built in a sample or the private pack, not in Awake. Tests that
matter: determinism (same inputs → same state path), zero allocation per tick, and correct
exit/enter ordering on transition.

**Phase 2 — debugger + behavior LOD. Not started, gated on Phase 1.** State-path timeline in the editor; distance buckets and a
per-tick agent cap. Do not defer the debugger past the third behavior.

**Phase 3 — promote the machine into Awake. Not started.** Only when a second consumer needs it. Machine only;
node catalogue stays out. Requires an exception record per the boundary doc.

**Phase 4 — Tier 0 LLM authoring. Not started; blocked on Phase 1's node catalogue.** Node catalogue enumeration, constrained generation, validator,
reject-on-unknown. Independent of Phases 1–3 in value but not in order: it needs a node catalogue
to validate against.

**Phase 5 — Tier 1 director. Not started.** Only after a game exists that has a strategic layer worth directing.

### Order against the navigation plan

Phase 0 here, then [navgrid](2026-08-30-navgrid-navigation-plan.md) Phases 1–2, then behaviors
accumulate, then Phase 1 here when the count justifies it. **That order ran as written** — Phase 0,
navigation bake and search, then chase, patrol and flee. What is left on the navigation side is its
Phase 3 (multi-tile, off-thread, cancellation), which is independent of anything here.

Navigation goes ahead of the runtime because it unblocks behavior *content*, and behavior count is
the gate on the runtime. Built in the other order, the runtime's leaf tasks would be validated
against `FakeNavMesh` only — which proves the tree walks, not that the node vocabulary is right, and
the vocabulary is the part that is expensive to get wrong. What flips this: a near-term need for
five or more distinct behaviors, in which case skip the gate. The repo currently has three.

### Authored or flexible — the question underneath Phase 1

Behaviour count is the gate the phases name, but it is not the deciding question. **Who authors a
behaviour is.**

- **Authored**: behaviours are Kotlin, written by whoever builds the game, shipped in a build. A
  new enemy type is a new `System` and a recompile.
- **Flexible**: behaviours are composed as data by someone who cannot or should not rebuild the
  engine — a designer, a modder, a live-ops tool.

Hand-written systems scale indefinitely under the first and stop being viable under the second, and
that holds regardless of whether there are three behaviours or thirty. A team of programmers with
forty authored behaviours may still never want this runtime; one designer who needs to retune a
patrol without a build already does.

**Where the repo actually sits today.** Behaviours are Kotlin. Scenes, however, are already data:
`SceneDocument` has a sealed `SceneComponent` with `@SerialName` variants, `SceneLoader` reads them,
and `SceneWorldExport` writes them back. So a data-driven content path exists and is in use — just
not for behaviour.

**That gap matters, because "flexible" has a much cheaper answer than this plan.** Most of what
designers ask for is *tuning* — a patrol route, a panic radius, a speed — not composition.
`PatrolBehavior` and `FleeBehavior` are already plain data: stops, radii, intervals, styles. Making
them authorable is adding `SceneComponent` variants and a mapping, using machinery that already
round-trips `spinControl` and `pbrMaterial`. It is a serialization feature, not a runtime.

So the ladder is three rungs, not two:

1. **Authored Kotlin.** Where we are. Correct while the author is the programmer.
2. **Authorable components. Done.** Behaviour parameters in `scene.json` — `ScenePatrol`,
   `SceneChase`, `SceneFlee` — plus inspector fields for the same three. Covers tuning, reused the
   existing serialization machinery, needed no runtime. This is the answer to "designers need
   flexibility" until proven otherwise. The one thing it could not express as plain data is an
   entity reference, which is a node name resolved after load.
3. **The hybrid runtime.** Only buys one thing the rungs below cannot: *composition between*
   behaviours — flee overriding patrol, returning to it when safe, sequencing without a recompile.

**Trigger for rung 3, stated so it is checkable:** someone who cannot rebuild needs to change *which
behaviour runs when*, not what its parameters are. Wanting a slower patrol is rung 2. Wanting "patrol
until you see the player, then chase, then flee below a third health, then return" without a build
is rung 3.

## Non-goals

- No GOAP, no HTN, no planner. Trigger for revisiting is stated above.
- No utility scoring in v1. It arrives as a selector variant, never as a parallel system.
- No runtime tree mutation. Blackboard state and enter conditions cover the variation.
- No multithreaded AI tick. `World` is single-threaded by design; the shape/instance split keeps
  the *option* open without spending anything on it now.
- No navigation implementation. Separate plan; this one only records the seam and the two
  requirements it places on the runtime.

## Risks

Phase 3's promotion is an API break for whichever sample built it first. Cheaper than promoting
prematurely and discovering the vocabulary was wrong, but it is a real cost and worth stating up
front rather than being surprised by it.

The fixed-capacity instance component needs a maximum tree depth. Guessing it now would be
guessing; pick it when a real tree exists, and make exceeding it a load-time rejection with a clear
message rather than a silent truncation.

Tier 1's snapshot is a prompt-injection surface the moment any of its inputs are player-authored
(names, chat, item labels). It is not a problem for a single-player prototype and it is a
first-class problem for the MMO. Design the snapshot as an allowlist of engine-computed fields, not
a serialization of whatever is nearby.

## Sources

- [Are Behavior Trees a Thing of the Past?](https://www.gamedeveloper.com/programming/are-behavior-trees-a-thing-of-the-past-)
- [Comparison between Behavior Trees and Finite State Machines (arXiv 2405.16137)](https://arxiv.org/pdf/2405.16137)
- [Overview of State Tree in Unreal Engine](https://dev.epicgames.com/documentation/unreal-engine/overview-of-state-tree-in-unreal-engine)
- [Behavior Tree in Unreal Engine — Overview (event-driven rationale)](https://dev.epicgames.com/documentation/en-us/unreal-engine/behavior-tree-in-unreal-engine---overview)
- [Common Behavior Tree problems](https://zomgmoz.tv/unreal/Behavior-Tree/Common-Behavior-Tree-problems)
- [Game AI Pro 3 — Overcoming Pitfalls in Behavior Tree Design](https://www.gameaipro.com/GameAIPro3/GameAIPro3_Chapter09_Overcoming_Pitfalls_in_Behavior_Tree_Design.pdf)
- [Game AI Pro — The Behavior Tree Starter Kit](https://www.gameaipro.com/GameAIPro/GameAIPro_Chapter06_The_Behavior_Tree_Starter_Kit.pdf)
- [GDC 2005 — Handling Complexity in the Halo 2 AI](https://www.gamedeveloper.com/programming/gdc-2005-proceeding-handling-complexity-in-the-i-halo-2-i-ai)
- [Evolving Halo's AI Behavior Trees](https://www.scribd.com/document/293958078/Halo-Behaviour-Tree-AI)
- [EntitiesBT — data-oriented BT for Unity DOTS](https://github.com/quabug/EntitiesBT)
- [Choosing between Behavior Tree and GOAP](https://www.davideaversa.it/blog/choosing-behavior-tree-goap-planning/)
- [A Study on Training and Developing LLMs for Behavior Tree Generation (arXiv 2401.08089)](https://arxiv.org/pdf/2401.08089)
- [EvolvingBehavior: Co-Creative Evolution of Behavior Trees for Game NPCs (arXiv 2209.01020)](https://arxiv.org/pdf/2209.01020)
- [Building a Game AI Commander — low-cost LLM/engine pipeline](https://huggingface.co/blog/AlexDuo/llm-unity-ai-commander)
- [Local AI NPCs for Game Dev (2026)](https://localaimaster.com/blog/local-ai-game-npcs)
