# Locomotion and warps

**Status:** Active.

`LocomotionAnimation` chooses which clip a skinned model plays — idle, walk, run, jump — from how
fast its world position moves across the ground. The speed is measured from the position itself, so
the component animates whatever moved the entity: a character controller, a script, or the network.

That makes it a consumer of a per-frame *position delta*, and position deltas are not always motion.
A networked entity **warps**: it respawns, it is leashed home when it strays too far, its position is
reset on aggro, and a client reconciles a controller back to where the server put it. Each of those
moves the entity in a single frame faster than it could ever walk or run — a discontinuity, not a
stride.

## The invariant

> Any system that derives motion from a per-frame world-position delta MUST reject a warp — a one-frame
> move too fast to be real motion — instead of reading it as speed. Treating a discontinuity as motion
> spikes whatever the system feeds from it.

`LocomotionAnimation` enforces this: a one-frame ground move faster than `max(runAbove × 4, 30)` units
per second is treated as a teleport and left out of the smoothed ground speed, so a jump in position
keeps whatever clip was playing instead of flipping to a run (and, under speed-matched playback,
racing the clip) for the fraction of a second the smoothing would take to bleed the spike off.

A discrete choice (which clip) driven by a continuous signal (ground speed) should also damp
single-frame transitions so no lone frame flips it; `LocomotionAnimation` smooths the ground speed
over about a tenth of a second for this reason.

## Where the warp is known

The *rule above* lives in the engine, with every delta-consumer. *When a warp happens* is the
consumer's own knowledge: a networked game's replication layer (for example a per-entity snapshot
interpolator) already decides that a jump between two snapshots is a teleport rather than interpolated
motion, and that same classification should drive what it feeds the animation runtime. Keeping the
rule in the engine and the warp's origin in the consumer is the [framework/game
boundary](framework-game-boundary.md): the engine supplies the warp-robust mechanism, the consumer
owns the policy of when its entities warp.
