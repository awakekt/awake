# Shape-cast contact recovery investigation

Issue: [#989](https://github.com/awakekt/awake/issues/989). Baseline:
`07822e57ac090ed851be61ef10c277e579eb325e`, following Core v0.23.0.

## Evidence and behavior

A stub hit with normal `(NaN, 1, 0)` and fraction `0.5` turned position `(0, 0, 0)` and motion
`(0.1, -0.1, 0)` into position `(NaN, -0.04375, 0)` on the baseline. This proves propagation from
an invalid contact, not that Jolt produced it in the reported consumer load run.

All four Jolt bindings now pass raw penetration axes to the same decoder. It normalizes using
Double arithmetic, avoiding Float overflow. A zero, near-zero or non-finite axis with a usable
cast direction yields a conservative contact at fraction zero facing against the cast. This
blocks the sweep rather than turning an uncertain hit into a miss. The first such fallback is
logged once per world. A finite fraction within `1e-6` of `[0, 1]` is clamped to the endpoint;
larger errors are not silently repaired.

A stationary degenerate cast has no direction from which to infer a blocking plane. It raises
`InvalidShapeCastResultException`, as do invalid points or unrecoverable result values at the
`ShapeCastHit` constructor. The constructor remains the last line of defence.

The character capability catches only that typed contact fault, restores the starting position,
drops the attempted displacement and clears uncertain ground state. It logs one diagnostic per
consecutive fault episode with query endpoints and the cause. Another character can continue
stepping normally. Standing up returns false and keeps the crouched pose when its query faults.
Unrelated world/programming errors still propagate.

This restores character pose, not arbitrary side effects of earlier valid onContact callbacks.
Invalid caller positions/motion/configuration and later mutation of result vectors remain outside
the contact-result invariant. The reverse-cast fallback is a conservative blocking plane, not a
claim to have recovered the exact geometric surface normal.

## Capability boundary and compatibility

CharacterConfig and KinematicCharacterController now have a scene-independent implementation in
`com.awakekt.awake.physics.character` inside `:awake:physics:api`. Their unit tests run with no
scene dependency. The original scene package retains its configuration and constructor/method/
property signatures as an adapter. All nine existing configuration fields are forwarded; scene
schemas gain no new options. The legacy scene API dump is unchanged and a native adapter test
covers movement, body ownership, crouch/stand, teleport and contact callback access.

## Validation

- 56 physics API tests, 46 scene physics tests, 106 native Jolt tests and 14 scene character tests
  pass on Windows desktop: 222 tests total.
- Tests cover each of the six controller query sites, rollback after partial stepping/snap-down,
  continued updates of a healthy character, bounded diagnostics, and unrelated error propagation.
- Decoder tests cover zero/tiny/non-finite axes, a downward cast, very large finite axes, endpoint
  rounding, gross invalid fractions and stationary ambiguity.
- The existing overlap regression and 6,400-query synthetic native mesh/wall stress case pass.
  The latter uses 100 independent query groups, 64 queries each, seed 104; it is not a replay of
  the original multi-agent simulation.
- Formatting, detekt, physics/scene/backend API checks, JVM/Wasm compilation, capability layering
  (88 modules) and headless-runtime checks pass. No physical Android or Apple native execution
  was performed locally; platform CI remains required.

## Remaining investigation

Keep #989 open for a trace/replay of the original load failure. Record the finite input pose,
motion/configuration, query endpoints and rejected/fallback contact. These guards and native
stress evidence do not establish or rule out the original cause.
