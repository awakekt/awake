# Shape-cast finite-result investigation

Issue: [#989](https://github.com/awakekt/awake/issues/989). Baseline:
`07822e57ac090ed851be61ef10c277e579eb325e`, following Core v0.23.0.

## Reproduction and boundary

A stub world returned a hit with normal `(NaN, 1, 0)` and fraction `0.5` while a character
started at `(0, 0, 0)` and moved by `(0.1, -0.1, 0)`. The original controller produced position
`(NaN, -0.04375, 0)`. This reproduces propagation from a malformed result; it does not prove
that the reported consumer load run received that result from Jolt.

`ShapeCastHit` now rejects non-finite point/normal components, zero normals, and non-finite or
out-of-range fractions at construction. The error identifies the field and value. All four
Jolt bindings construct this same portable API type, so the invariant belongs there rather than
in a platform-specific or scene-owned controller algorithm. Valid results retain their values;
no fallback direction or clear-path result is invented. No public signature changes.

The controller's six query sites were audited: headroom in `standUp`, main movement/slide,
step landing, snap-down, step rise/forward in `sweepBy`, and ground-state probing. Stub regressions
exercise malformed contacts at those paths and verify that the position remains finite when the
exception is raised. A failed move can have applied earlier valid partial movement; this is not
transactional rollback or automatic recovery. Invalid caller motion/configuration and later
mutation of a result's mutable vectors are outside the construction invariant.

## Native evidence

The existing stationary-overlap regression passes. A new seeded synthetic-mesh/wall case makes
6,400 stationary/moving capsule queries (100 independently sampled query groups, 64 queries each,
seed 104). Every returned contact has finite point/normal/fraction, a fraction in `[0, 1]`, and a
unit normal. The case asserts that contacts actually occurred. It is a sequential native query
stress test, not a replay of the original 100-agent simulation.

On Windows desktop, 23 physics API tests, 76 scene physics tests and all 103 Jolt tests pass.
Formatting and detekt for the three modules, physics API compatibility, JVM/Wasm API compilation,
capability layering (88 modules), and headless-runtime checks pass. No Android device, browser
runtime or Apple native test was run locally; CI owns the available cross-platform checks.

## Remaining investigation

Keep #989 open for a trace/reproduction of the original load failure. The malformed-result path
is now diagnostic, but the native stress case does not establish or rule out its original cause.
If it recurs, record finite input position, motion/configuration, query endpoints and the rejected
contact before choosing a backend normalization or recovery change.
