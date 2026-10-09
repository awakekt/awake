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
| Anything a shipped game needs at runtime, when it is neutral engine capability | Awake Core (Apache 2.0, Maven Central) |
| Authored world policy and game rules | The consuming game or content pack |
| Authoring productivity, team and cloud workflows | Studio Pro (commercial; its own skills score the placement) |
| A public starting point for a new game | `awake-template` |

Runtime is never commercial: whatever a Studio scene uses must run from Awake or the game's own
code, and a Studio Pro tool emits data that runtime reads. A capability reaches Awake only
through the Decision Rule and an Exception Record. Awake and its editor contract never depend on
Studio Pro. There is no separate starter-kits repository.

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

## Mechanism, Not Policy

Awake ships mechanisms; games and templates decide how they feel.

- Gameplay tuning (move and jump speed, gravity, camera follow distance and pitch, on-screen
  control layout) is never a constant in Awake code. It is a field on a scene component with a
  neutral default, and the template or game authors the value in scene data.
- Input is actions, not keys. A system reads named actions; which keys, buttons or touch controls
  trigger each, and whether it fires on press, while held or as a toggle, is one binding table in
  scene data. A new verb is a new action in that table, never a key constant or a per-verb key or
  mode field on a gameplay component. The player's fixed move and jump keys and
  `movement_control`'s `runKey` and `runMode` predate this and become actions in
  [#587](https://github.com/awakekt/awake/issues/587).
- Movement that needs ground, slopes or collisions goes through the physics character controller
  binding. A hand-rolled gravity or ground-snap system is game code.
- Template and sample art (an arena floor, demo materials, placeholder characters) is a project
  asset the template writes. Awake's built-ins are neutral primitives such as `cube`, `sphere` and
  `plane`.
- Promoting code from Studio, a sample or a template: split it into mechanism and values first,
  move only the mechanism, and give each value a scene-component field that the template fills.
  A system that works for one template's world only (a flat floor, a fixed arena) stays template
  code. A module that loads and runs whole projects is composition, not scene binding, so it lives
  outside `awake:scene:*`.

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
| Third-person camera distance and pitch as constants in a player module | Scene data | Feel is authored; Awake binds a camera-rig component. |
| Toggled running as `runKey` and `runMode` fields on the movement component | An input action bound in scene data | A field per verb grows Awake into one game's control scheme; a binding table lets each game add its own. |
| Jump by gravity and a fixed floor height | Game or template code | Real movement goes through the physics character controller. |
| A template's checkered arena mesh as an engine built-in | Template asset | Template art, not a neutral primitive. |

## Agent Routing

`awake-architecture-auditor` applies this guide before a new module, a promotion from Studio, a
sample or a template, or a server/network/persistence proposal. Domain engineers implement only after the boundary decision.
Create MMO-specific agents and skills in the MMORPG repository, where they can own product policy.

## Exception Record

Use a task note or decision that names the consumers, missing API, proposed Awake contract/module,
game policy intentionally excluded, dependency direction, and public-API validation plan.
