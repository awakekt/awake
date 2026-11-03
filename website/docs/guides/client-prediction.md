# Client prediction

<p class="awake-lede">Move the player's own entity the moment they press a key, then correct it from the server's snapshots without putting it back for the walk the server simply hasn't heard of yet.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:net:prediction</code></span>
<span class="awake-badge">Platform: all</span>
</div>

`awake:net:prediction` is the decision half of client-side prediction: a history of the poses the
client's inputs left its entity at, and a pure `reconcile` function that compares a server snapshot
with that history and says what to correct. It holds numbers only, so it works with any transport,
any protocol and any way of moving the entity, and depends on nothing but `awake:core:math`.

## The technique

1. **Predict.** Move the entity at once on each input. Send the input with a rising sequence number,
   and record the pose it left the entity at in a `PredictionHistory`.
2. **Acknowledge.** Each snapshot from the server carries the newest input sequence it took.
3. **Compare at the acknowledgement.** Compare the snapshot with the pose recorded for that sequence,
   not with the pose drawn now. A snapshot is a round trip old; the entity has moved on since on inputs
   the server hasn't seen. At a 6 m/s run, a 250 ms round trip is 1.5 m of walk that isn't an error.
4. **Correct only the divergence.** A small drift is owed and blended into the following movement. A
   real one shifts the entity by the drift alone and keeps its unacknowledged steps. A snap happens only
   for a drift too far to be movement (a respawn or teleport), a sequence the history no longer holds,
   or a rule of your game's.
5. **Cap the prediction.** Once the server has heard none of the client's inputs for about as long as
   it keeps moving the entity on a guess, the entity eases to a stop instead of running further and
   further ahead of a link that has stalled.

A snapshot corrects nothing unless its acknowledgement is newer than the snapshot before it, and the
server guessed at most a tick or two of that input: either one shows a server walking the entity on a
guess while the client's inputs are held up, which it may undo once they arrive.

## A client loop

```kotlin title="Kotlin"
--8<-- "awake/net/prediction/src/commonTest/kotlin/com/awakekt/awake/net/prediction/PredictionDocsSample.kt:client"
```

`reconcile` returns one of:

| `Correction` | What to do |
| --- | --- |
| `None` | Nothing; move at full speed. |
| `Blend(dx, dy, dz)` | Owe the drift and put it right a little each step as the entity moves (`PredictedEntity.blend`). |
| `Shift(dx, dy, dz)` | Move the entity and every recorded pose by the drift. Draw the jump as a short glide: `PredictedEntity` fades it with `core.math`'s `ErrorOffset`, a 0.05 s half-life, mostly gone within 0.15 s. |
| `Snap(x, y, z, reason)` | Put the entity on the server's pose now, and the history with it. |
| `Wait(speedScale)` | Scale the entity's speed, from 1 to 0 over the `capEaseSeconds` before the cap. It wins over a shift and a blend while it lasts. |

`PredictedEntity` applies a correction to a plain pose. For ECS entities whose movement owns a
root `Transform`, add `com.awakekt.awake.scene:net` and use its binding:

## Scene transforms

Register `NetSceneComponents` with the project's `SceneComponentRegistry` (`registerNet()`), decode
with that registry's `sceneJson`, and author `predicted_entity` alongside the root transform. The
schema exposes every `ReconcileConfig` option plus `historyCapacity`, `glideHalfLifeSeconds` and
`blendShare`. A missing `predictionCapSeconds` follows the authored guess window plus ease.

Install `PredictionSystem` before the game's fixed-step movement, and `TransformSystem` after it.
Movement reads `PredictedTransform.speedScale` and calls `recordInput` after moving, with the input
sequence and a timestamp on the system's monotonic clock. Keep sending and recording while stopped.
Snapshots are supplied on the world thread before the system runs. The system ignores older
acknowledgements and consumes each correction once.

```kotlin title="Kotlin"
--8<-- "awake/scene/net/src/commonTest/kotlin/com/awakekt/awake/scene/net/PredictionSceneDocsSample.kt:scene"
```

The transform holds simulation state. For a visual glide, copy `drawnX/Y/Z` onto a separate root
visual entity; never feed that pose into simulation or history. The offset decays at fixed-step
frequency. Parented simulation transforms are rejected. Physics characters and rigid bodies must
apply `net:prediction` through their own position owner instead of this direct-transform binding.

Convert world-absolute snapshot positions to scene coordinates at ingress. On an origin shift,
translate the simulation root and call `prediction.rebase(dx, dy, dz)` with the same translation,
before prediction or recording, so its snapshot and history shift together. Replace the component
when reconnecting or restarting input sequences; no previous session state is serialized.

## Your game's rules

A game snaps for reasons of its own. Name them with a `SnapReason` and return one from
`ReconcileRules`, which `reconcile` asks first:

```kotlin title="Kotlin"
--8<-- "awake/net/prediction/src/commonTest/kotlin/com/awakekt/awake/net/prediction/PredictionDocsSample.kt:rules"
```

## Heard, not acknowledged

A server that moves a late input's entity on a guess of the last one usually acknowledges the guessed
ticks too, so its acknowledgements keep arriving through the first part of a stall while the client
runs on. Pass how many of the acknowledged ticks were guessed as `guessedTicks`: a snapshot built on
more than `trustedGuessedTicks` of them corrects nothing, and the cap is timed from the newest input
the server actually heard, `ackSeq - guessedTicks`. Timed from the acknowledgement instead, a game
measured a 5 m shift after a 1.5 s stall; timed from the heard input with the default cap, no
correction at all and a drift of under a metre, blended away. A server that never guesses passes 0.

## Tuning

| `ReconcileConfig` | Default | What it is |
| --- | --- | --- |
| `correctMetres` | 1.5 | Past this, a drift is shifted at once; under it, blended. |
| `farMetres` | 10 | Past this, the entity snaps (`SnapReason.Far`). |
| `trustedGuessedTicks` | 2 | The most ticks the server may have guessed of an input for its snapshot to correct. |
| `serverGuessWindowSeconds` | 0.5 | How long your server moves a late input's entity on a guess before it stands it. |
| `capEaseSeconds` | 0.25 | How long the entity takes to slow to a stop, ending at the cap. |
| `predictionCapSeconds` | guess window + ease | How long the oldest unheard input may wait before the entity stands. Set it outright to override. Longer than the worst round trip you support. |
| `planar` | false | Ignore height: for an entity whose height a controller or the terrain sets on each side. |

The defaults suit a world in metres, entities at a person's running speed and round trips under a few
hundred milliseconds. Set your own for anything else.

## Allocation

Keep one `ReconcileInput` and fill it each frame. `PredictionHistory` is a fixed ring that allocates
nothing after construction. `reconcile` returns shared instances for `None` and a stopped `Wait`; it
allocates only a correction that does something, at most once per snapshot, and a `Wait` during its
ease.

## See also

- [Networking in the browser](web-networking.md)
- [Modules](../reference/modules.md)
