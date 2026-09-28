# Framework and Game Boundary

Awake is a reusable engine/framework. A future MMORPG is a separate consumer repository.
This boundary keeps Awake generally useful while allowing the MMORPG to use it deeply.

## Decision Rule

Put a capability in Awake only when it is engine-generic and has either:

1. two credible independent consumers, or
2. a concrete framework-level limitation that a consumer cannot solve through public Awake APIs.

Otherwise keep it in the game repository. A future MMORPG is evidence of one consumer, not
automatic justification for an Awake module. For the second condition, record the missing public
API, why a consumer adapter is insufficient, and the smallest framework contract required.

## Ownership

| Belongs in Awake | Belongs in the MMORPG repository |
|---|---|
| ECS/world lifecycle, rendering, physics, input, assets, UI primitives | Gameplay, classes, combat, abilities, NPC behavior, quests, loot |
| Fixed-step and headless-runtime primitives when reusable | Authoritative simulation policy, tick rate, command semantics, prediction/reconciliation |
| Generic serialization hooks and stable identity/lifecycle contracts | Network protocol, transport implementation, accounts, sessions, persistence and migrations |
| Profiling, diagnostics, test primitives | Bots, load tests, dashboards, deployment, moderation, anti-abuse |
| Platform-neutral extension points | Zones, shards, guilds, chat, economy, live operations |

## Where a Capability Lives

| Capability | Home |
|---|---|
| Neutral engine seam: no world or product vocabulary, generic across games | Awake Core (Apache 2.0, Maven Central) |
| Authored world policy and game rules | The consuming game or content pack |
| Reusable commercial runtime kits a game ships with, and commercial authoring tools | Studio Pro (private, commercial; its own skills describe the layout) |
| A public starting point for a new game | `awake-template` |

A capability reaches Awake only through the Decision Rule and an Exception Record; it reaches
Studio Pro as a product decision. Awake and its editor contract never depend on Studio Pro. The
runtime of anything a Studio scene uses must exist where the game ships, either in Awake or in a
commercial runtime kit the game depends on. There is no separate starter-kits repository.

## Promotion Test

Before promoting game or sample code into Awake, answer all questions:

1. Is its vocabulary neutral? Game, player-economy, account, quest, guild, shard, and MMO
   protocol vocabulary stays outside Awake.
2. Can a consumer implement it with existing public Awake APIs? If yes, keep it there.
3. Are two independent consumers likely to need the same stable behavior? If no, defer it.
4. Can Awake expose a small contract without selecting product policy, storage, protocol, or
   service topology? If no, it is product code.
5. Does it keep network libraries, databases, renderers, and UI stacks out of core ECS/runtime
   dependencies? If no, redesign or reject it.

## Examples

| Proposal | Decision | Reason |
|---|---|---|
| Headless fixed-tick runtime | Candidate for Awake | Generic simulation capability. |
| `PlayerInventory`, currency, item-stack rules | MMORPG repository | Product economy and vocabulary. |
| WebSocket replication client | MMORPG repository initially | Protocol and transport policy are consumer-specific. |
| Narrow transport port | Possible future Awake contract | Extract only after demonstrated lifecycle reuse. |
| Stable entity serialization extension point | Candidate for Awake | General lifecycle seam if persistence format remains external. |
| Character database and anti-duplication locks | MMORPG repository | Persistence and operational policy. |
| Texture or UV animation on a material, set from a scene file | Awake | Neutral render capability a content-only pack cannot add itself. |
| A water look (ripples, shoreline, foam) | Consuming pack | Authored world policy; Awake supplies the surface-shader seam, scene depth and time. |
| River, lake and flow-painting tools | Studio Pro | Commercial authoring workflow over pack or Awake data. |

## Agent Routing

`awake-architecture-auditor` applies this guide before a new module, promotion from samples, or
server/network/persistence proposal. Domain engineers implement only after the boundary decision.
Create MMO-specific agents and skills in the MMORPG repository, where they can own product policy.

## Exception Record

Use a task note or decision that names the consumers, missing API, proposed Awake contract/module,
game policy intentionally excluded, dependency direction, and public-API validation plan.
