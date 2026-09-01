# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-dev.8] - 2026-09-01

### Added

- **The editor has pixel baselines.** It had no visual coverage at all, which the icon-role change
  made concrete: moving the hierarchy's header actions from filled 20px glyphs to 24px outlines
  changed what is on screen and the whole suite stayed green. The console (at every severity) and
  the tool palette (the four transform glyphs) are pinned through the same `assertMatchesBaseline`
  the shadcn corpus uses -- same zero-tolerance compare, same recording workflow, no second tool.
  Repointing one icon role moves 98 pixels while the icon invariant tests stay green, which is the
  class of defect only the pixels can see.

- **The editor's glyphs are named by role and drawn from one tier.** The scene hierarchy drew its
  header actions as filled 20px mini glyphs directly above rows drawn as 24px outlines -- two
  weights adjacent in one panel, which nothing had decided: each icon was picked at its call site
  from whichever tier carried the glyph, and five roles used mini only because their outline
  variants had never been vendored. `EditorIcons` names 30 roles on `Outline24`, so switching the
  editor between outline and filled is editing one file. That is the only variant switching offered
  -- Heroicons' tiers do not carry the same glyphs (19 of 57 exist in both mini and outline), so a
  general `icon(name, variant)` lookup would miss for most of the set at runtime; a role wanting a
  tier the glyph lacks is a compile error instead.

- **Solid bodies can report their contacts.** Only sensors did, and a sensor is the thing you pass
  *through* -- so hit sounds and impact damage had nothing to hang on. `setContactReporting` opts a
  body in, per body: Jolt offers every touching pair in the scene on every step, so reporting them
  all would queue an event per pair per frame for a settled pile of crates that nothing reads.

- **Heroicons are generated from vendored SVGs at build time.** `HeroIcons.kt` was 3,082 lines
  pasted in by hand, with provenance as an assertion rather than a fact: each glyph named an
  upstream source nothing checked, no Heroicons version was recorded anywhere, and the file had been
  hand-edited despite saying not to. The 78 SVGs are now vendored at a pinned **v2.2.0** and the
  build produces the Kotlin. All 78 reproduce byte-identically from that release -- both the proof
  that the pipeline reproduces what shipped, and the answer to the version question that was the
  only unrecoverable part of this and got worse with time. Adding a glyph is copying its SVG in.
  Heroicons' MIT license is retained beside the SVGs.

- **`packedImageVector`, so an icon can be one string instead of forty method calls.** A generated
  icon set is mostly `lineTo`/`cubicTo` statements: 78 Heroicons are 3,082 lines of Kotlin. The same
  geometry as text is 47,331 characters against 111,937 of builder calls. It is deliberately not an
  SVG parser and cannot become one -- no XML, no shape elements, no transforms, no arcs, no relative
  commands -- because codegen already resolved those into cubics and hole nesting, which is what
  keeps decoding cheap enough to do at class init and keeps the icon reference a compile-time
  symbol. Every committed Heroicon is proven to survive the round trip. The generated `HeroIcons.kt`
  still emits builder calls: it lands in `build/`, where its size stops mattering and reproducing
  what shipped byte-for-byte is worth more. `--packed` is one flag away if that changes.

- **A skinned character goes limp: the "Skinned ragdoll" showcase.** CesiumMan collapses wearing his
  own mesh, then is dropped again once he settles. The bodies are shaped from his own bind pose by
  `ragdollFromSkeleton` over twelve of his nineteen joints, and `RagdollSkeleton` writes them back
  into the pose the joint palette is built from. Hands, feet and the lower neck are left undriven
  and follow their parents.

- **A ragdoll can be built from a character's own skeleton.** `ragdollFromSkeleton` shapes one
  capsule per named bone, spanning from that bone to the bone below it, with joints following the
  same hierarchy -- and records the fixed transform between each capsule and its bone.
  That last part is what makes a skinned ragdoll possible at all: a capsule lies *along* a limb and
  a rig's bones point down whatever local axis their exporter chose, so binding a generic ragdoll to
  somebody else's rig shears the mesh. `RagdollSkeleton` applies the offset on the way back out, and
  an unmoved ragdoll now reproduces its skeleton's bind pose exactly.

- **A ragdoll showcase, and the showcase's physics is interpolated.** The engine showcase gained a
  "Ragdoll" entry: an eleven-limb figure collapsing under gravity and dropped again once it settles,
  which is where the joint limits are visible -- a shoulder that folds flat against a back and a
  knee that bends the wrong way are what a ragdoll without them looks like. Separately,
  `ShowcasePhysics.system()` wraps `PhysicsSystem` in a lazy delegate, and `SceneSchedule` decides
  who to interpolate by testing the *registered* system: the wrapper declared plain `System`, so
  the interpolation added alongside it never ran anywhere. `Mat4.setTrs` came with the showcase --
  the in-place form, so a set of instanced transforms rebuilt each frame stops allocating a matrix
  per instance per frame.

- **Physics is drawn between fixed steps instead of snapping between them.** The simulation runs at
  a fixed 60 Hz and a display refreshes whenever it likes, so most frames redrew a pose that had not
  changed and every so often one jumped -- stutter from a perfectly smooth simulation. `alpha` had
  been computed since `FixedTimestepLoop` was written and thrown away by `SceneSchedule`, which took
  no parameter for it. `InterpolatedSystem` is the seam; `PhysicsSystem` implements it and blends
  each body's last two poses. Rotation is blended as a quaternion and only then converted to the
  Euler angles `Transform` stores -- doing it the other way round makes a body spinning past the
  wrap point snap a full turn backwards, once per revolution.

- **A ragdoll can drive a skinned character's bones.** Until now a ragdoll was eleven invisible
  capsules falling next to a character still playing its idle animation -- the simulation was right
  and nothing wore it. `RagdollSkeleton` maps limbs onto bones and converts each limb's world pose
  into the parent-relative form `AnimationPose` stores, so a corpse keeps its own mesh; bones no
  limb is bound to (fingers, face) keep whatever the animation left there. `AnimationPose` gained
  the read/write surface that needs, which is also the seam an IK solver or a procedural head-turn
  would use. `Ragdoll.forEachLimb` now reports every limb rather than only the awake ones.

- **Ball-and-socket joints with real angular limits, so a ragdoll folds like a body.** A ball joint
  used to be a distance constraint pinned nearly to zero length, which held limbs together and
  limited nothing -- a shoulder could fold flat against the back. `BallSocketConstraint` is the real
  thing on all four Jolt bindings: a swing cone and a twist range about an axis, built on six-DOF
  with translations locked, because Jolt's point, cone and swing-twist constraints are all absent
  from JoltC and six-DOF is the only one every binding has. `humanoidRagdoll` now carries anatomical
  limits per joint, and `PhysicsWorld.setAngularVelocity` came with it -- the rotational twin of
  `setLinearVelocity`, and the only way to induce twist and so to test a twist limit at all.

- **The inspector can add a component.** Until now it could show and edit what an entity already
  carried and nothing else, so building an entity meant editing the scene file. `SceneComponentFactory`
  is the seam -- the third over the same types, beside the inspector's form and the snapshotter's
  copy, and for the same reason: the editor cannot name `PhysicsBody` or a game's own component, so
  whoever owns the type supplies the constructor call. `Transform` and `SpinControl` come from the
  scene, `PhysicsBody` from its plugin, and the menu offers only what the entity does not already
  have -- `World.add` replaces, so offering a present type is offering to discard what the user
  configured on it. Undoable, like every other edit.

- **`BoxScope.align`, so a Box can anchor each child where it belongs.** A Box could only stack
  every child at one alignment, which is why the scene viewport spent a layout row on its display
  toggles -- and a row across the top of a 3D view is a band of the view that is gone, at the height
  where a horizon and an object's head sit. `BoxWithConstraintsScope` is a `BoxScope` now too, as
  upstream's is, so a responsive layout can anchor per child without nesting another Box.

- **The backend parity harness renders a lit, shadowed scene, not just UI.** `vulkanHeadlessScene`
  and `webGpuHeadlessScene` stand up `lit_shadow` with a real cascaded depth pre-pass, and the new
  scenario draws a ground plane with a quad hovering over it and compares where the shadow lands on
  each backend. This is the case the UI scenarios could not reach: WebGPU sampled the shadow map
  along the wrong axis for as long as the shader existed, Vulkan's rendering of this same scene has
  had coverage the whole time, and nothing compared them. Reintroducing that bug now fails here.

- **One headless harness both backends answer to.** `:awake:engine:render:parity` opens Vulkan and
  WebGPU in a single run, draws the same scenarios through each, writes every capture to
  `build/reports/render-parity/`, and compares the two. Each backend exposes its windowless fixture
  as ordinary API (`vulkanHeadlessUi`, `webGpuHeadlessUi`, both returning `HeadlessRenderSession`)
  instead of hiding it in its own test source set, which is what made "do these draw the same
  thing?" a question with nowhere to live. It found a real divergence on its first run -- see the
  two glyph fixes below. Vulkan is opened first, deliberately: `libwgpu_native` brings its own
  Vulkan loader into the process and an instance created after it loads fails at `vkCreateInstance`.
  Every scenario is enforced. Coverage is compared everywhere and colour only where alpha is full:
  the two targets store colour differently -- Vulkan's offscreen target is UNORM and premultiplied,
  WebGPU's takes its format from the surface, which is sRGB on macOS -- so partial-coverage colour
  is not comparable across them, while the shape is.

- **A file panel with a pluggable viewer, and Studio's Files tab on top of it.**
  `EditorFilePanel` lists the files a host gives it and previews the selected one with whichever
  registered `EditorFileViewer` claims the extension. The editor never reads a file -- the host
  passes bytes, because `readResourceBytes` suspends and on the web it is a `fetch`, so a panel that
  owned loading could not run in a browser. The built-in `PlainTextFileViewer` deliberately does not
  claim `.md`: a built-in that took every text-shaped file would take the extension an add-on exists
  to handle, and the add-on would only win if the host happened to install it first. Studio's Files
  tab ships a Markdown document nothing can draw, so the seam is visible in the running app --
  install a viewer for `md` and the same document renders.

- **Ragdolls.** `Ragdoll` builds jointed limbs above `PhysicsWorld`, and `humanoidRagdoll` gives an
  eleven-limb figure that collapses under gravity and stays in one piece. Not a binding to Jolt's
  own `Ragdoll` — **JoltC exposes none**, so that would work on three targets and throw on the
  fourth; a ragdoll is bodies and constraints, and Jolt's class is convenience over that.
  **A zero-length distance constraint is not a ball joint**, which is what this cost to learn: Jolt
  solves one along the axis between its two points, and when they coincide that axis is undefined,
  so it never converges — eleven limbs still twitching after fifteen seconds. Two centimetres of
  slack makes it solvable and it settles in two. Self-collision was the first suspect and was
  innocent: a non-self-colliding layer changed nothing.
  Ball joints still have no angular limits, so a shoulder folds in ways a body cannot. That wants a
  six-DOF-backed joint, the only type all four bindings share.
- **Physics replays exactly: the same scene, stepped the same way, produces the same numbers.**
  Contact events are normalised low-id-first and sorted before delivery, and every map whose
  iteration feeds teardown order is insertion-ordered — teardown decides which body ids Jolt hands
  out next, which feeds its island ordering and so the simulation itself.
  This is same-build, same-machine reproducibility. Cross-platform is not available: the four
  backends are three different Jolt builds on three architectures.
  What it buys during development: physics tests that assert exact values instead of tolerances,
  bug reports that replay instead of "sometimes", and contact-ordering flakes that cannot happen.
  **The test only has teeth at scale** — with six falling bodies, removing the sort still passed;
  with forty it fails every run. Jolt guarantees deterministic simulation, not the order it invokes
  a contact listener across worker threads.
- **Fixed: buoyancy crashed the process on Android.** Applying it to a body that had settled and
  fallen asleep aborted, because buoyancy adds velocity and a sleeping body has no motion state to
  receive it. Only Android found it: it ships jolt-jni's *debug* artifact, so it asserts where
  desktop's release build runs on quietly — the same way the iOS simulator does, and worth
  remembering as where misuse surfaces first.
  The first fix asked `BodyInterface.isActive` while already holding a write lock on that body,
  which is a recursive lock and crashed a different test instead. It asks the locked body now. That
  is the second time nested body locks have bitten, after iOS's constraint creation.
- **The player can swim.** Walking into the showcase's pool now dampens gravity, slows horizontal
  movement, and turns Space and Ctrl into rise and dive. It needed **no character-controller
  change**: vertical velocity is already the caller's to integrate — the controller's guarantee is
  that it only ever removes motion from what it was handed — so swimming is the caller integrating
  differently while the pool reports the player inside it. `applyBuoyancy` could not do this job,
  because the character's body is kinematic and buoyancy only moves dynamic ones.
  Three ways to get it wrong, each a test: no drift is a swimmer hanging motionless, full gravity is
  a swimmer falling, and a drift stronger than the swim speed is water that cannot be climbed out of.
- **Water you can drop something into.** The terrain showcase has a pool: a sensor volume is the
  water, contact events track what is inside it, and the fixed step applies buoyancy to each
  occupant so a box dropped in floats instead of reaching the bottom. That composition is the point
  — physics has no idea where water is, so the sensor answers "who is in it" and the loop answers
  "how often".
  Two things worth knowing. **There can be only one `drainContacts` caller**: it hands over the
  events since the last call and forgets them, so the goal zone and the pool share one loop that
  routes by which sensor was touched. And **a pool whose surface is level with its own bed proves
  nothing** — floating and resting end at the same height — so a placement test asserts the surface
  clears the ground beneath it.
- **A trigger can finally detect the player.** `CharacterConfig(innerBody = true)` gives the
  character controller a kinematic body of its own. Without one it is invisible to physics — it
  moves by sweeping shapes, so Jolt has nothing to report contacts about, and every sensor built for
  pickups and checkpoints was deaf to the one thing it exists for. The showcase's goal zone now
  reacts to the player, not only to crates pushed into it.
  The body is excluded from the controller's own sweeps, which is what `shapeCast`'s `ignore` was
  needed for: a body at the character's own position is nearer than anything else in every sweep it
  makes, so without the exclusion the character stops dead on its first frame. Removing it fails
  three tests.
  **It does not give pushing for free** — the obvious guess, and wrong. The controller refuses to
  move into a crate, so the kinematic body never drives through one and there is no contact to
  solve; shoving stays the caller's job. A test asserts that after claiming the opposite and being
  corrected by the simulation.
- **Control over which bodies are actually being simulated.** `setActive` and `isActive` on all four
  backends. Jolt already sleeps a settled body, so this is control over *when* — the open-world case
  where a world holding thousands of crates should not be simulating thousands of crates.
  **Deactivating is not disabling:** a sleeping body still collides and is woken by what hits it, so
  an open world that sleeps distant scenery does not become scenery the player falls through. Two
  consequences bite from elsewhere in the contract — a sleeping body is not visited by
  `forEachBodyTransform`, and a sensor only detects *active* bodies, so deactivating something
  inside a trigger makes the trigger forget it.
  The distance-based *policy* is deliberately not here: nothing consumes it, and what counts as
  "far" is a game's decision, not the engine's.
- **Hinge and distance constraints, so a world can have doors and ropes.** `HingeConstraint` is
  every door, lid, lever and wheel; `DistanceConstraint` is every rope, chain and grapple. Two of
  Jolt's ten, because two cover the verbs that asked for them.
  **A constraint dies with either body it joins.** Jolt does not detach one when a body is
  destroyed — it crashes on the next step — so `destroyBody` removes them first and `destroy()`
  clears them before freeing bodies.
  Three traps, each caught by a test rather than reasoned about: **two write locks at once trips
  Jolt's lock-ordering assert** (iOS died in `BodyLockInterfaceLocking::LockWrite`; it now takes one
  at a time); **a constraint is ref-counted, so freeing it by hand double-frees** (on wasmJs that
  corrupted the shared heap and showed up as unrelated tests failing later); and **Jolt wants a
  hinge's normal axis and will not derive one**.
- **Buoyancy, so things float.** `PhysicsWorld.applyBuoyancy(handle, surfaceY, Buoyancy, deltaTime)`
  on all four backends. Called every step for every body in the fluid — it is one step's worth of
  push, not a state a body is put into — because physics has no idea where the water is and a
  sensor covering the volume does. That is the composition the contact-event work was for.
  A horizontal surface at a height rather than an arbitrary plane: every water body in a game is
  level. **Dynamic bodies only, and enforced**, because Jolt's own call reaches for motion
  properties a static body lacks and aborts the process rather than returning — the KDoc claimed
  that guard before the code had it, and the test that passes a static body found it as a SIGABRT.
- **Streamed terrain colliders, and rebuilding them when the ground is deformed.**
  `heightFieldCellStreamer` gives every streamed cell a tile of a larger heightfield, and **derives
  the cell size** instead of taking one — the streamer's cell centres and a tile's own extent agree
  only at one value, and a hand-passed mismatch offsets every collider from its terrain uniformly,
  which reads as the whole world being subtly wrong rather than as a mistake.
  `PhysicsCellStreamer.reloadCells` rebuilds a deformed cell, off the frame thread like a load and
  only for cells actually streamed in. `tilesTouchedByEdit` says which cells an edit invalidated —
  **an edit on a tile boundary invalidates both sides**, since neighbours share that sample, and
  rebuilding one leaves a wall exactly where the player just dug.
- **Terrain can be collided with as a grid of tiles, not one body for the whole map.**
  `heightFieldTile` cuts one tile out of a larger sample grid; `heightFieldTileCenter` places its
  body, for a centred authored heightmap or a corner-anchored streamed one.
  **Neighbouring tiles share their edge samples** — tile `k` starts at `k * (samples - 1)` — and
  that off-by-one is the entire difficulty: cut disjointly, the two sides of a seam end at
  different heights, so a character catches on a one-sample wall or drops through a gap, and
  nothing about it looks like a slicing bug. Verified against a real Jolt world on all four
  backends; cutting disjointly fails three of the five tests.
- **iOS can collide with terrain, and no vendored library was forked to do it.** JoltC wraps no
  heightfield at all, so that backend threw for one — the last per-target capability gap, and a
  blocker for anything terrain-shaped. It now builds the field as a triangle mesh: a heightfield
  *is* a grid of triangles, so the binding was missing an optimisation rather than a capability.
  The cost is one float per sample becoming three per vertex plus six indices per cell, which is
  nothing at 9x9 and half a million triangles at 512x512.
- **Vertex buffers are built through `VertexFormat` now, not by counting floats.**
  `InterleavedVertices` writes attributes by name — `put(vertex, VertexSemantic.Normal, x, y, z)` —
  and takes the offset from the format. The format had always known every attribute's offset and
  the stride; nothing used it to *write*, so every producer hand-packed `vertices[cursor++] = ...`
  and had to remember its write order matched the format it declared. When those disagree the mesh
  still loads and still draws, rendering UVs as colours, with nothing pointing at the packing loop.
  `GltfMesh`'s five near-identical interleave functions collapse to two shared helpers — 165 lines
  gone — and the heightmap mesh builder uses it too. **Four of those five had no test at all** while
  the showcase's skinned and glTF-viewer drivers depended on them, so they were pinned by
  characterisation tests first; those caught two things I had wrong, including that joint indices
  are stored as `Float.fromBits` patterns because the GPU reads that slot as `uint4`.
  The clipmap builders follow: their two byte-identical vertex loops become one, and their
  hand-maintained `COLOR_OFFSET = 6` and `STRIDE = 11` are now asked of the format through the new
  `VertexFormat.floatOffsetOf`. Pinned first by a test asserting a clipmap vertex slot by slot,
  which nothing did — the existing ones count vertices and read the colour channel, so a packing
  change kept every count intact and rendered UVs as colours.
- **One definition of grid winding, replacing four copies.** `gridTriangleIndices` in
  `:awake:core:geometry`, used by the heightmap mesh builder, both clipmap ring builders and the
  new physics conversion. Winding is the part worth centralising: wound backwards a surface still
  draws, and rays still hit it from both sides, and only a body falling through it shows the
  difference — so the shared version has the test that asserts the face normal, which none of the
  four copies had.
- **Continuous collision detection, so a bullet cannot cross a wall.** A step moves a body by
  velocity times delta and then looks for contacts where it landed, so at 400 units/s a projectile
  is six metres past a wall before anything is asked about it.
  `PhysicsWorld.setContinuousCollision(handle, enabled)` makes Jolt sweep the body's shape along
  that displacement instead. A setter rather than a `createBody` argument: whether a body needs it
  depends on how fast it is *now*, unlike `sensor`, which is what a body is.
  Off by default — it is a shape cast per step per body. **It protects the body it is set on, not
  the ones it hits:** whichever thing moves fast is the one that needs it.
  The test asserts both directions, because "it stopped" proves nothing if the bullet was never
  fast enough to tunnel; the no-CCD run must tunnel, and does, identically on all four backends.
- **Jolt's asserts are on for the iOS simulator build, off for the device.** With them off, Jolt
  does not check its callers at all — a wrong body type or an unlocked body read is silent
  corruption rather than a stop — and the simulator is where the tests that would catch that run.
  The device build stays off: an assert there stops a player's game over something the simulator
  should have caught.
  Worth knowing before relying on it: **this does not get you the message.** Jolt's default
  `AssertFailed` breakpoints and prints nothing, and its default `Trace` is a stub, so a violation
  is `signal 5: Trace/BPT trap` plus a stack trace rather than text. The frame under
  `JPH::DummyTrace` in the crash report is the site — and `DummyTrace` appearing at all is the tell
  that Jolt formatted a diagnostic and had nowhere to put it. Installing a real handler means
  setting C++ globals that cinterop cannot reach and JoltC does not expose.
- **Android instrumented tests, so all four physics backends are now actually run.** Android was
  the last blind spot and could not be covered by host tests at all: jolt-jni's Android artifact
  ships device ABIs and nothing for a JVM host, so `System.loadLibrary("joltjni")` has nothing to
  load. `connectedAndroidDeviceTest` runs the same `commonTest` sources on a device — 44 tests,
  including the heightfield ones, which had never executed on that binding. `testAndroidHostTest`
  is disabled in this module for the reason above; it would fail every test with
  `UnsatisfiedLinkError`. The four jolt-jni capability tests moved to a `joltJniTest` directory
  shared by desktop and Android, since those two are the same binding.
- **The Jolt backend's tests moved to `commonTest`, and found three more bugs on the way.** 40 of
  them now run on desktop, the iOS simulator and a headless browser instead of desktop alone. What
  the first run turned up, all of it invisible to a compiler:
  - **iOS `raycast` accepted `onlyLayer` and ignored it**, so a camera meant to see only the level
    was stopped by every crate — and it reported sensors as solid.
  - **iOS swept meshes silently**, returning a hit instead of the `PhysicsCapabilityException`
    every other backend throws for a surface with no inside.
  - **wasmJs's body filter compared a pointer to an id.** JoltPhysics.js hands its filter callbacks
    Emscripten *pointers*, not values, so `ignore` matched nothing and silently did nothing.
  Four tests stayed in `desktopTest` because they assert jolt-jni *capabilities* the contract makes
  optional — heightfields (unimplemented in JoltC) and active-body readback (`forEachBodyTransform`
  explicitly permits visiting everything).
- **Fixed: the iOS physics backend aborted on its very first step, and always had.**
  `TempAllocatorImpl` is a linear allocator over a fixed block — overrunning it calls `abort()`
  rather than falling back to malloc — and Jolt sizes its per-step arrays from the *configured*
  maxima, not from the bodies present. So 10 MB was under what a world with one box in it asks for,
  and every `step` killed the process. Raised to 32 MiB.
  It survived four features because nothing ever *ran* that backend: it compiled, and compiling
  proves nothing about a temp allocator. Found by moving one test into `commonTest`, where it runs
  on desktop, iOS and wasmJs instead of desktop alone. `JoltBackendSmokeTest` now guards all three.
- **Casts no longer report sensors, so a trigger volume is not an invisible wall.** Found by
  spiking the assumption rather than trusting it: a sweep at a sensor hit it at fraction 0.17, which
  meant the goal-zone trigger added a commit earlier was a solid obstacle the character bumped into.
  A sensor is not solid, so it is not an answer to "what would block me" -- `shapeCast` and
  `raycast` now skip them, and `overlapShape` is the query that does see them. That is the division:
  casts ask what stops you, overlaps ask what you are inside.
- **Overlap queries, and a sweep that can ignore one body.** `PhysicsWorld.overlapShape` visits
  every body inside a convex volume -- spawn validity, an explosion's victims, a melee arc's
  targets -- which no cast can answer, because a sweep stops at the first thing that would block
  it. A visitor rather than a list, so an explosion does not allocate at the rate the game is
  played. `shapeCast` gains `ignore`, which skips one body *inside* the query: filtering the result
  afterwards cannot substitute, since a closest-hit cast only ever reports the ignored body and
  everything behind it stays invisible.
  The backends split on how, and the reason is worth knowing: **jolt-jni's `BodyFilter` cannot be
  overridden** -- it has a public `shouldCollide` and no callback subclass, and an override is never
  called. Measured, not assumed: a filter rejecting everything left the ray hitting the body anyway.
  So the JVM backends collect every hit and take the nearest other one; iOS builds a real
  `JPC_BodyFilter` and wasmJs uses `IgnoreSingleBodyFilter`.
- **A pickup you can push a box into, in the terrain showcase.** A `goal-zone` post marks a trigger
  volume; boxes shoved into it are collected and removed, body first. The first thing in the
  showcase that reacts to physics having *happened* rather than to physics having moved something --
  nothing here measures a distance to the zone, it waits for Jolt to say a box entered. `PhysicsBody`
  gained a `sensor` flag so a scene can author a trigger at all.
  **The player is not what trips it, and cannot be.** `KinematicCharacterController` owns no body --
  it sweeps shapes -- so Jolt has nothing to report contacts for, and giving it an inner body is
  blocked until a sweep can exclude one: `onlyLayer` restricts a query to a single layer and a
  character needs two, and skipping a specific body needs multi-hit queries. Until then a trigger
  fires on the crates, not on the character.
- **Sensors and contact events, so physics can be a gameplay verb.**
  `createBody(..., sensor = true)` builds a body that detects what passes through it instead of
  blocking it, and `PhysicsWorld.drainContacts` hands over the `BEGAN`/`ENDED` pairs once per step.
  Pickups, checkpoints and damage volumes are expressible now; before this, physics could push
  things around but could not tell anyone that something had happened.
  The queue exists because **Jolt calls its contact listener from worker threads, in the middle of
  a step** — acting there would mean mutating a running simulation from a thread that does not own
  it, so the callback only records and the drain replays on the frame thread. Guarded by
  `synchronized` on the JVM backends, a pthread mutex on iOS, and nothing on wasmJs, which has no
  threads. Only sensors report, because Jolt reports every touching pair in the scene on every step
  and forwarding all of it would allocate an event per contact per frame for events nothing reads.
  No contact point, normal or impulse: `ENDED` arrives from a callback that carries none of them.
  `MeshShape` and `HeightFieldShape` are rejected as sensors — both are surfaces with no inside.
- **Snap and transform-space toggles in the scene editor's viewport.** `GizmoSnap` and `GizmoSpace`
  were complete and tested but nothing read them: the editor's only `GizmoFrame` left both at their
  defaults, so it snapped never and dragged in world space always. `EditorState` now carries
  `snapEnabled` and `transformSpace`, and `EditorGizmoOptions` toggles them beside the tool palette.
  Snapping is one flag rather than a step per tool, at one world unit, fifteen degrees and tenths
  of scale.
- **`EditorPlugin` has its first implementation, so the plugin registry is live.** A feature that
  simulates, draws and is editable has to reach `SceneSystemsDsl`, the shader pack's
  `ContentFeatureSource` and `EditorProviders` separately, and a host that forgets one gets a
  feature that runs but cannot be edited. `EditorPluginRegistry.install(plugin)` bundles the editor
  half and rejects a duplicate id or an incompatible API version on the way in. Plugins are linked
  at build time, not loaded at runtime -- Kotlin/Native and wasmJs cannot load code, so a
  marketplace would be a desktop host above this contract. Systems are not on the plugin:
  `SceneSystemsDsl` sits in a module `awake:editor:scene` depends on, so a plugin cannot reach the
  DSL without inverting that edge, and a host installs both for now.
- **`awake:editor:ai` and `awake:editor:render`.** The AI behaviour and rendering inspector fields
  were `when` branches inside `awake:editor:scene` while physics editing was already a plugin. Each
  feature's editor now ships with the feature, and what stays built into the scene editor is
  `Transform` and `SpinControl` -- what every scene has. A new engine feature adds a plugin rather
  than a branch. Both dropped Compose in the move: `SceneFieldScope` describes a form, so an
  inspector no longer knows how a field is drawn.
- **`awake:editor:physics`**, joining `awake:editor:scene` and `awake:scene:physics`. Neither may
  depend on the other -- the editor must not pull in the physics backend, and physics must not
  depend on an editor -- so the plugin that makes `PhysicsBody` inspectable needs a module of its
  own rather than a home inside whichever sample happened to see both.
- **Inspecting something no longer requires this ECS.** `EditorFieldScope` and
  `EditorInspector<T>` moved to `awake:editor`, which does not depend on `awake:ecs`; the ECS
  binding stays in `awake:editor:scene` as `SceneInspectorTarget(World, Entity)` and a
  `SceneComponentInspector` over it. The field vocabulary never named an entity, world or
  component -- it described a form -- but it lived beside the ECS-typed inspector, so an engine
  with a different object model could not reach it. `SceneFieldScope` is now a typealias, so
  existing inspectors compile untouched.
- **`PhysicsBody` is inspectable.** `PhysicsScenePlugin` contributes a motion-type field through
  the inspector seam, which until now had no user outside its own test -- `awake:editor:scene`
  cannot name the type without depending on the physics backend, which is what the seam exists to
  avoid. `shape` stays read-only: editing one means choosing a sealed variant and then its
  parameters, and there is no field for that.
- **Physics runs in a scene for the first time.** `createJoltPhysicsWorld` gives common code a way
  to obtain a world at all -- `JoltPhysicsWorld` is a separate class per target rather than one
  `expect` class, so until now nothing outside the backend could construct one. The
  heightfield-terrain showcase drops four boxes onto the same samples the terrain mesh is built
  from, stepped on a fixed timestep.
- **A capsule, and a shape cast to move it with.** `CapsuleShape` and
  `PhysicsWorld.shapeCast(shape, from, to)` on all four backends. `ShapeCastHit` carries a
  contact normal, which is what collide-and-slide projects along; without it a hit cannot be slid
  against. No rotation parameter, deliberately.
- **A character that walks on the world instead of through it.**
  `KinematicCharacterController` sweeps, slides along whatever stops it, steps up kerbs and
  follows the ground down the far side, and reports what it is standing on. It is Awake's own
  rather than a binding to Jolt's `CharacterVirtual`: collide-and-slide needs exactly one thing
  from a backend, so written once here it behaves identically on all four. Gravity stays the
  caller's -- the controller integrates nothing, which is what makes it predictable. The engine
  showcase drives one with WASD, Space to jump, a camera that follows it and stops at walls, and
  crates it can shove around.
- **Velocity and impulse: `setLinearVelocity`, `getLinearVelocity`, `addImpulse`,
  `moveKinematic`.** Four methods rather than one because each does what the others get wrong --
  an impulse is mass-aware where a velocity write is not, and a kinematic body moved toward a pose
  genuinely has a velocity rather than teleporting, so what stands on a platform rides it.
- **Level geometry can collide.** `MeshShape` gives static geometry a triangle-mesh collider built
  from the same vertex and index arrays the renderer draws, and `ConvexHullShape` gives a moving
  prop a shape that is not a box. Both on all four backends. A mesh is a surface -- no inside, no
  mass -- so it is static only, and its **winding decides which side is solid**: wound the wrong
  way, bodies fall through it while rays still hit it from both sides.
- **Crouching, and refusing to stand where there is no room.** `CharacterConfig.crouchHalfHeight`
  gives a character a shorter capsule; `crouch()` swaps to it with the feet planted, and
  `standUp()` sweeps upward first and returns whether it managed. A caller that ignores the answer
  puts the capsule inside the ceiling, which is the one state a swept character cannot get out of.
  Held on Ctrl in the engine showcase, tried every frame, so the character stands the moment it
  walks back into the open.
- **Moving platforms carry what stands on them.** `KinematicCharacterController` reports
  `groundVelocity` -- how fast the ground underfoot is moving -- which a caller adds to the
  displacement it asks for. Reported rather than applied, for the same reason gravity is: a move
  only ever does what it was asked, so the caller decides whether walking against a lift should
  win. The terrain showcase has a lift beside the mound, driven with `moveKinematic` so Jolt
  derives the velocity to arrive rather than teleporting it.
- **Colliders stream with their cells.** `PhysicsCellStreamer` gives every streamed world cell a
  collider and takes it back when the cell leaves, so an open world's body count follows the view
  distance rather than how far the player has walked. The shape is built off the frame thread and
  the body created on it, mirroring `MeshCellStreamer` -- including its cell-centre convention, so
  a streamed collider lands where the streamed mesh does. `PhysicsBody` gained a `layer`.
- **Colliders can be seen.** `physicsDebugLines(world)` builds world-space wireframes of every
  collider at the pose the simulation has it in, coloured by motion type, and the engine showcase
  has a "Colliders" toggle for them. The collider and the mesh drawn for it are two different
  things, and the bugs that matter live in the gap -- a capsule sunk into terrain, a heightfield
  offset by half a tile, a body whose collider never moved with its transform. All of them read as
  rendering faults until this is on. Heightfields draw as a footprint rather than a sample grid.
- **Collision layers.** `CollisionLayers` gives a world its own matrix -- how many layers, which
  collide, which move -- instead of the two every backend hardcoded, and bodies carry a
  `CollisionLayer`. Queries can be restricted to one layer, which is what stops the showcase
  camera being shoved about by the crates around the player's feet. Broadphase is two trees now,
  static and moving, rather than one. The limit worth knowing: a query takes ONE layer, not a
  mask, because that is all jolt-jni and JoltPhysics.js expose in public API.

### Fixed

- **Bone hierarchies were composed with quaternions multiplied the wrong way round.** `Quat.times`
  is the Hamilton product with its operands swapped, so composing a child under its parent is
  `local * parent` -- not the `parent * local` that reads correctly and that the matrix APIs are
  spelled as. Six places in the ragdoll code had it backwards, which was invisible because their
  tests recomposed with the same swap; it only surfaced against a walker mirroring real playback.
  `QuatCompositionTest` now pins the order against the matrix path a joint palette actually uses.
- **A bone authored as a baked matrix was read as though it had none.** `RagdollSkeleton` took an
  undriven bone's transform from the pose, which holds nothing for such a bone, and
  `ragdollFromSkeleton` took the matrix's rotation but not its translation. A glTF Z-up root is
  exactly this kind of bone, so every bone under one was posed a quarter-turn out.

- **Physics readback values were kept by reference where they had to be copied.**
  `PhysicsWorld.forEachBodyTransform` hands out a position and a rotation that the next body
  overwrites. Positions were copied everywhere; rotations were not, in three places -- so every body
  ended up sharing whichever rotation happened to be visited last. The pose interpolation added
  alongside it was affected: rolling previous from current made both halves the same object, leaving
  nothing to interpolate between. The shared test fake now hands out scratch the way the real
  backends do, which is what would have caught it.

### Changed

- **A stroked path is tessellated once, not once per frame.** Stroking is the most expensive
  tessellation the 2D coalescer runs -- flatten every curve, offset both sides of every contour with
  per-join arc trig, scanline-fill the ring -- and a UI redraws the same icons every frame in the
  same place. The frame ratchet's own scene went 41,016 to 56,184 vertices when the icon set became
  real outlines, and every one of those was being recomputed. `StrokedPathMeshCache` keys on the
  placed path, so a stroked path that moves still misses; normalising to the origin costs a full
  walk of the commands and is not worth it until something that moves strokes every frame.

- **The scene hierarchy's header actions are one button group.** Duplicate, delete and add all
  perform an action, which is shadcn's own rule for choosing `ButtonGroup` over `ToggleGroup`, and
  the reference cites it as well. Joined and at `size-6` they fit a 160dp sidebar, which three
  separate `size-9` buttons did not -- the Row overlaps rather than shrinks, so the title and the
  entity count were being painted over. The count is gone: the tree below shows what matched.

- **The hierarchy's duplicate and delete actions are always in the header, disabled with nothing
  selected.** They were composed only while a selection existed, which reflowed the header under the
  pointer as the selection changed -- and, worse, made a subtree come and go in an engine whose
  `remember` is positional, the exact shape that surfaced once as a `ClassCastException` in an
  unrelated panel. The React reference shows both permanently for the same reason.

- **The scene viewport's controls float over the scene, and move when it is too narrow to hold
  them.** Tools in one corner, display and debug toggles centred on the top edge, gizmo in the
  other -- the anchors the React reference (`tools/shadcn/reference-app`, case `studio-shell`) uses,
  and the reason it gives: a single strip across the top costs the scene its most useful band. Below
  760dp the three top anchors collide, so the toggles stack into the top corner and the tools move
  to the bottom one. Both were measured from the rendered shell rather than chosen.

- **The viewport's display and debug toggles read as two groups, and the inspector's empty state
  fits its column.** The two toggle groups sat eight pixels apart in identical styling, so ten
  unlabelled glyphs read as one strip a user navigates by counting positions; each group now sits on
  its own surface. The inspector showed `shadcnEmpty` -- shadcn's page-scale empty state, `p-12` and
  a `text-lg` title -- centred in a 300dp column, which is two lines of near-heading text floating
  in the middle of the panel; it is one muted line at the top now. The shared component is unchanged:
  the parity baselines hold it to the web's dimensions, and the call site is what was wrong.

- **A shader's clip space is handed to it, not remembered by whoever wrote it.**
  `aslShaderSet { clipSpace -> ... }` builds a definition once per backend with that backend's
  `ClipSpace` in scope, and `ndcToUv` in the shader DSL is the one place an NDC coordinate becomes a
  texture coordinate. `ClipSpace` used to govern only matrix construction, so the inverse -- reading
  an image back by screen or light-space position -- was arithmetic each shader author typed from
  memory. Two got it wrong: `depth_fog` was patched with a hand-passed `flipDepthV` boolean, and
  `lit_shadow` then sampled every shadow map mirrored on WebGPU. `litShadowShader` and
  `depthFogShader` both take a `ClipSpace` now and neither does the arithmetic itself.

- **Studio's bottom dock has Console, Assets and Timeline tabs**, composed through the stateless
  `EditorDock` with the selection in Studio's own store. `AwakePluggableWorkbench` is deleted: it
  derived its tabs from data presence, so an empty asset list meant no Assets tab and a user who
  deleted their last asset watched one disappear. The capability panels it wrapped are unchanged
  and now reachable directly. Assets lists the fixture's registered meshes; selecting one does
  nothing until drag-to-viewport exists, and the timeline is empty because the fixture has no
  clips -- both shown rather than hidden, which is the point.
- **Studio moved from `samples/studio` to `apps/studio`.** Its Gradle path is now `:apps:studio`,
  so the quickstart is `./gradlew :apps:studio:run`. It was never a sample: it is the product the
  README points a new user at, and the only host every editor seam is proven through. `samples/`
  now holds only illustrative code. No Kotlin changed -- the package was already
  `io.github.awakelab.awake.studio`.
- **Body poses are read back by visitor, and only while they are awake.**
  `PhysicsWorld.syncTransforms()` became `forEachBodyTransform`, which hands out scratch values
  instead of allocating a list plus a vector and a quaternion per body per frame, and on jolt-jni
  and JoltPhysics.js enumerates only the bodies Jolt considers awake -- so a settled scene costs
  almost nothing to read. `syncTransforms()` remains as an extension for tests and tools.

  This changes what a caller may assume: **a body may never be visited at all.** Static bodies are
  never awake and settled ones stop being awake, so neither is reported. Hold the last pose you
  were given -- which a scene's `Transform` already does -- or ask the world where a body is.
- **A body's rotation is a quaternion.** `BodyTransform.rotation` and `createBody` take
  `core.math.Quat` rather than Euler angles, so the solver's own rotation passes straight through
  instead of being converted in four backends. `Transform` still stores Euler, so the conversion
  moved to one place at the ECS boundary rather than disappearing -- this does not fix gimbal
  lock, which lives in `Transform`.
- **`PhysicsBody.motionType` is settable, and changing it rebuilds the body.** It was a `val`, and
  `PhysicsSystem` created a body once and never rebuilt one, so a hypothetical edit would have left
  the component saying `STATIC` while the simulation kept a dynamic body falling. The system now
  tracks what each live body was built with and destroys the old one on a mismatch. A rebuild
  rather than a mutation, because the backend port has no `setMotionType`; the body's velocity does
  not survive it.

### Fixed

- **Shadows compare in hardware, and the last of the acne is gone.** `lit_shadow` now samples its
  cascades through a linear-filtering comparison sampler (`textureSampleCompareLevel`, new to the
  shader DSL alongside `sampler_comparison`; `compareEnable`/`compareOp` new to the Vulkan sampler
  binding), so each PCF tap blends four comparison *results* instead of flipping on one near-tie
  depth -- the shadow edge is a smooth penumbra instead of a three-texel dither. The residual
  waffle on tilted faces turned out to be the map polygon's own slope, which no receiver-side
  `nDotL` estimate can see, so the cascade depth pass now applies the rasterizer's per-polygon
  depth bias at the source. Together they take the studio cube's worst yaw from 24 (Vulkan) and
  32 (WebGPU) self-shadowed pixels to 1 and 0, while the receiver bias constants *shrink*
  (2.5/3 -> 1.5/2 texels, normal offset 1.5 -> 1), so resting casters hold their shadows tighter,
  not looser. What the frame looks like is now pinned by `SceneShadowBaselineTest`: per-backend
  pixel goldens over one symmetry period of yaws, zero tolerance, on both backends.

- **Heroicons' outline tier is stroked path data again.** All 32 glyphs were generated before the
  script emitted `path(stroke = ...)`, so each was a *filled stroke centreline* -- the silent-garbage
  case the generator's own comment names. Regenerated from the official SVGs. Two of them,
  `userCircle` and `lightBulb`, still rendered as solid shapes afterwards -- a stroke-tessellation
  bug that predated the data, fixed below. `OutlineIconStrokeTest` measures the hole rather than
  leaving it to be noticed on screen.

- **A stroke outline is no longer mistaken for a convex shape.** `isConvex` only compared turn
  signs, and a stroked arc's ring turns one way the whole way round while wrapping about twice: out
  along one side, round the cap, back along the other, cap again. It read as convex, `tessellateFill`
  centroid-fanned it, and the fan covered the hole the outline was supposed to leave -- `userCircle`
  and `lightBulb` rendered as solid discs. It now also requires the total turning to be one
  revolution, summed over edge directions with zero-length edges dropped: pairing raw vertex triples
  across such an edge discards the turn at that vertex, enough to push a genuinely convex rounded
  rectangle out of tolerance. Those icons now go through the scanline triangulator, which is what an
  outline costs -- the frame ratchet moves from 41,016 to 56,184 vertices, and the cheaper number was
  measuring blobs.

- **A stroked contour that ends where it began is stroked as a loop.** The open-contour path walks
  the outer and inner boundaries the same way round, so under `NonZero` the windings add and the
  hole fills. Found while chasing the icons above; not what was wrong with those two, but wrong.

- **The icon generator emits types that exist.** It still wrote `UiImageVector`/`uiImageVector` and
  `UiStroke`, which went with `ui-core`, so its output no longer compiled against the file it feeds
  -- adding an icon meant hand-editing generated code, which is the one thing the icon rule forbids.

- **The hierarchy's header actions are all one Heroicons tier.** Duplicate came from `Solid24` while
  delete and add came from `Solid20Mini`; the two tiers are drawn for different pixel sizes, so one
  button read heavier than its neighbours. `square2Stack` is generated at 20 now.

- **The console marks warnings and errors with a glyph.** A letter column was standing in for the
  reference's `exclamation-triangle`/`x-circle`, which the icon set did not have. It does now. The
  column is reserved even on lines with no glyph, so a warning appearing above does not shift every
  message's left edge.

- **An inspector section's state belongs to the section, not to its position.** `remember` stores
  its slots on the nearest enclosing node and indexes them by call order, so every component
  section's expanded flag lived in the panel's own slot list -- adding or removing a component
  shifted them all by one, silently swapping which section was open. Each section is now a keyed
  node, which is what makes its state its own. The add-component menu made the same latent bug
  fatal rather than silent: its remembered state landed on a section and was read back as one.
- **A lit face no longer speckles itself as it turns.** The shadow slope bias approximated
  `tan(acos(n))` as `(1 - n)/n`, described as the same shape without a square root. The two differ
  by `sqrt((1 + n)/(1 - n))` -- 2.4x at `n = 0.7`, 4.4x at 0.9 -- and agree only at grazing angles,
  so the cheap form was weakest exactly where a surface faces the light. A rotating cube sweeps its
  lit faces through that band, which is why the studio's cube stippled at some angles and not
  others, and why it looked like a WebGPU fault when both backends measure it identically. The two
  texel counts had been fitted against the wrong slope and were re-swept with it. The parity harness
  gains the scene that can show this at all: its existing one uses a single-sided plane under one
  fixed box, which cannot self-shadow however wrong the bias is.

- **A horizontal `ShadcnButtonGroup` with a separator no longer takes its parent's whole height.**
  The separator is a hairline that fills the cross axis, and the group's `Row` set no height, so it
  read whatever the parent offered: dropped into the scene hierarchy's header it became as tall as
  the panel and pushed every entity row off the bottom of the screen. The vertical orientation
  already guarded this with `IntrinsicSize.Min`; the horizontal one does now too. A
  height-constrained bar -- the only place a separated group was used before -- hides it completely.

- **WebGPU sampled the shadow map upside down, so shadows landed away from what cast them.**
  `lit_shadow` turned a fragment's light-space NDC into a shadow-map UV with one formula for both
  backends. That formula is only right for a Y-down NDC: Vulkan's projection flips Y
  (`ClipSpace.Vulkan.flipY`), WebGPU's does not, so on the web the lookup was mirrored about the
  map's centre. The shader takes the axis as a parameter now -- the same shape `depthFogShader`
  already used for the identical divergence -- and `PackShaderSets.LitShadow` emits one source per
  backend. A test holds the two sources to differing on that line and no other.

- **WebGPU's shadow binding test reads the shader it is meant to check.** It loaded
  `assets/shader/webgpu/lit_shadow.wgsl` from the classpath, and that file stopped existing when the
  packed shaders moved their WGSL inline -- so the test failed on its first line and the binding it
  exists to verify has been unchecked since. It resolves both shaders from `PackShaderSets` now,
  and passes.

- **WebGPU's glyph shader was told what kind of atlas it had, so web text is sharp.** The font-info
  uniform was written as a partial `writeBuffer` at its own offset -- which reached the buffer and
  never the shader, leaving `fontInfo` at zero. The glyph shader read that as "coverage-alpha
  atlas" and sampled an MTSDF atlas's alpha channel as if it were ink, turning every edge into a
  wide linear ramp: the same letter came out about two pixels wider on every side than Vulkan's,
  with no solid interior at all. The pipeline now holds both halves of the uniform block and writes
  it whole from offset zero, the way Vulkan always has.

- **WebGPU scaled UI colour by coverage, so web text stopped rendering as blobs.** The UI blend
  state hardcoded `srcFactor = One` for every pipeline kind, but only the texture pipelines emit
  premultiplied colour -- quads, rounded quads and glyphs emit straight alpha, so a half-covered
  pixel painted at full strength and every antialiased glyph edge came out at full weight. Vulkan
  has always chosen the factor per kind from the same `isPremultiplied` flag this backend already
  carried and never read. Found by the new cross-backend parity harness, not by looking at Studio.

- **The WebGPU backend reports its display scale, so the web UI is no longer half size.**
  `BackendResources.density` was left at its `1f` default, while the canvas is sized in physical
  pixels -- so on any 2x display the whole UI laid out as though one dp were one physical pixel and
  came out at half the size it does on desktop, with distance-field text sampled far below the size
  it was tuned for. Vulkan has always derived the ratio from its window's logical extent; WebGPU now
  asks the platform, which on wasmJs is `devicePixelRatio`. Native desktop WebGPU still reports 1x:
  it never sees the GLFW window the ratio would come from.

- **Delete, undo and duplicate no longer drop components in silence.** `SceneEntitySnapshot` copied
  seven named component types; everything else an entity carried was not captured, so it did not
  come back and nothing reported the loss -- delete an NPC and undo, and it returned without its
  behaviour. `SceneComponentSnapshotter` is the seam a module registers a copy through, capture is
  driven by `World.componentTypes`, and a type with no snapshotter is warned about rather than
  skipped quietly. `PhysicsBody` and the AI behaviours are covered; `PhysicsBody.handle` is
  deliberately not, so a restored entity gets a fresh body instead of a pointer to a destroyed one.
- **The slider's bar tracked its value again**, along with the progress bar's. Both drew through
  `drawWithCache`, which rebuilds on size, density and layout direction only, so a drag changed the
  value while the paint kept the fraction from the frame that built the cache.
- **Terrain collision works on wasmJs.** It was refused outright, so the terrain showcase would
  have thrown in a browser. JoltPhysics.js ships heightfields in full and the case was simply
  unimplemented. iOS genuinely has none -- JoltC exposes no heightfield settings -- and still
  refuses.
- **Android and iOS compile again.** Both were left unbuildable by the floating-origin work:
  Android never got `shiftOrigin` at all, and iOS called a JoltC function it had not imported and
  passed pointers where that binding takes values.
- **The heightfield showcase renders again.** Its follow camera flew out of the scene, taking the
  view with it. `EngineShowcaseLoader` built every rig with `offsetPosition = camera.lens.center`
  -- the same `Vec3f` instance, not a copy -- and `CameraSystem` writes the pivot it computes back
  into `lens.center`. With no target the pivot is the offset and nothing changes, which is why it
  sat there harmlessly; with a target the pivot is `target.position + offsetPosition`, so the
  character's position was added again every frame. Nothing threw and nothing logged, which is
  what made it look like a rendering fault.
- **The third-person camera keeps its distance.** Its collision sweep starts on the character, and
  a sweep that starts in contact reports a fraction of zero -- so anything touching the player
  collapsed the eye onto them and the near plane clipped the world away. There is now a floor on
  how close it may be pulled, and it sweeps static geometry only, so crates rolling past no longer
  shove the view.
- **The character no longer hovers, or stalls against a step it can climb.** Ground was reported
  the moment the probe saw a surface, and since being grounded is what stops a caller accumulating
  gravity, nothing pulled the character the rest of the way down; it stood in the air. And the
  step probe advanced by whatever motion was left, which at walking pace leaves the capsule on its
  own rounded shoulder at the edge, where the sweep reports the edge rather than the top face and
  the step is refused.

- **A lit surface no longer shadows itself where the light grazes it.** A face nearly parallel to
  the light has almost no area in the shadow map, so its texels hold whatever stands above it and
  the face reads as shadowed by itself; no bias fixes that, because the stored depth is a
  different surface. The lookup now steps `texel / nDotL` along the normal, and -- the part that
  actually mattered -- the cascade is selected from the fragment's own position rather than from
  the offset one, so the offset can no longer change which cascade a fragment reads.
- **Turning shadows off no longer darkens the scene.** A primary draw with no cascades was handed
  `mvp + light.directional` -- a block sized for the UNSHADOWED lit shader -- while the pipeline
  was still the shadowed one, so material, camera position and fog read whatever the buffer held.
  A renderer that owns a depth target now always writes the full block, with a cascade set that
  shadows nothing. Measured: the frame's peak brightness went from 104 with shadows off to 144,
  matching shadows on.
- **A headless frame can go through the on-screen path.** `HeadlessSurface` now allocates
  stand-in swapchain images, so `Renderer.draw` -- acquire, record, submit -- runs without a
  display, and `readPresentedPixels` copies the frame it produced back. `renderToTexture` is a
  different recording, and the two have diverged before; this is how they get compared.
- **An app's render plan can be rendered without a window.** `HeadlessSurface` makes
  `VulkanEngine` create its device and swapchain headlessly, so a test can boot an app's OWN plan
  -- its content features, depth passes and pipelines -- instead of a hand-built fixture. Every
  shadow probe until now built its own single-pipeline renderer, which cannot reproduce a scene
  that looks wrong on screen while the probes pass.
- **Cascades can be turned off, not just outlined.** The showcase's "Shadow cascades" toggle drew
  cascade wireframes; there was no way to see the scene WITHOUT cascades, which is the only way to
  see what they buy. `WorldDebugSettings.cascadedShadows` switches the fit back to the single
  fixed box, and the showcase exposes it as its own checkbox.
- **An authored sun points where the scene says it does.** `SceneLight` had no `direction` field,
  and `SceneJson` ignores unknown keys, so every scene that authored one -- the cascaded-shadows
  showcase among them -- was lit from the engine's default angle instead. Nothing failed: the
  light existed and the shadows fell, just not where the file said. Found by making a test load
  the real scene rather than a copy of its numbers.
- **Shadows are fitted to a shadow DISTANCE, not to the camera's far plane.** A 1000m view gave a
  5m-wide scene cascades 329m, 676m and 2338m across -- texels of 16cm to 114cm, on which a 2m box
  is six texels wide and no amount of bias helps. `DEFAULT_SHADOW_DISTANCE` is 100m; a camera that
  sees less than that is unaffected.
- **A cascade slice keeps its camera's projection.** The per-slice `Lens` copied eye, centre and
  field of view but not `projection`/`orthoHalfHeight`, so every slice of an orthographic camera
  was fitted as a perspective cone -- the other half of the frustum fix below.
- **An orthographic camera is culled and shadowed as the box it is.** `Frustum.corners` derived a
  cone from the field of view whatever the lens said, so culling, the spatial index's frustum
  query, the debug overlay and the shadow cascade fit were all wrong together for such a camera:
  a 5m-wide top-down view was fitted with a 480m cascade.
- **A single-box light had no shadow bias at all.** Bias and normal offset are now sized in
  cascade texels, and a cascade set built from matrices alone -- which is what a non-cascaded
  light produces -- reported a texel size of zero, so both came out zero and every lit surface
  self-shadowed. The scale is now read off the matrix instead of defaulted.
- **Shadow bias is measured in texels, not metres.** A texel is the unit the error is actually in:
  the map stores one depth per texel, so a surface is misrepresented by its own slope across it.
  A fixed distance is too much in a near cascade and too little in a far one.
- **Shadows no longer scale over their own lit surfaces.** A metres-valued bias is the right unit
  but the wrong tool for a far cascade, whose texel covers tens of centimetres of slope -- more
  error than any bias small enough to keep the shadow attached. The lookup now steps along the
  surface normal by two texels of whichever cascade it lands in, so the fragment reads its
  neighbour's depth instead of its own. Acne and peter-panning are the two ends of one knob, and
  both are now asserted on the same scene.
- **A heightmap is centred on its own origin.** `generate { cube() }` and `plane()` already were;
  a heightmap put its corner at the origin, so a tile's `Transform.position` sat most of a tile
  from anything drawn -- which LOD distance, culling, origin rebasing and collider placement all
  read. `heightAtWorld`, the mesh builder, the nav bake, Jolt's heightfield offset and
  `MeshCellStreamer`'s cell placement all move together.
- **Shadow bias is a distance in metres, not a number in NDC.** The bias was a constant in
  shadow-map depth, and each cascade maps a different world depth range into 0..1 -- so the same
  constant was a centimetre in the near cascade and half a metre in the far one, which is what
  peter-panned the far one. Cascades now upload their NDC-depth-per-world-unit and the shader
  scales a metres-valued bias by it.

## [0.1.0-dev.7] - 2026-08-30

### Changed

- **The terrain feature is one package.** `TerrainComponent`, `TerrainClipmapSystem` and
  `terrainContentFeature` moved from `scene.rendering.components`, `.systems` and `.content` into
  `scene.rendering.terrain`. They are one feature that was filed under three layer-named folders,
  which is the worked example `module-architecture.md` uses for the rule. First slice of that
  conversion; the rest happens as those files are touched for other reasons.
- **The particle feature is one package.** `ParticleEmitter`, `ParticleSystem`, `BurstEmitterPool`
  and their extensions moved from `scene.rendering.components` and `.systems` into
  `scene.rendering.particles`. Second slice of the same conversion.
- **The mesh feature is one package.** `MeshRenderer`, `InstancedMeshRenderer`,
  `InstancedSkinnedMeshRenderer`, `MeshBounds` and `LodGroup` moved from
  `scene.rendering.components` into `scene.rendering.mesh`. `RenderSystem` did not follow them —
  it draws every feature, not meshes, so filing it under `mesh` would misname it.
- **The animation feature is one package.** `Animator`, `SkinnedPose`,
  `ModularCharacterComponent`, `AnimationSystem` and `ModularSkeletalSystem` moved into
  `scene.rendering.animation`.
- **The debug-visualisation feature is one package.** `WorldDebugSettings` and
  `DebugVisualizationSystem` moved into `scene.rendering.debug`.
- **`scene.rendering.components` and `.systems` are gone.** `PbrMaterial` joined `mesh`, `Occluder`
  and `SpatialIndexSystem` joined `spatial`, and `Camera`, `Light`, `RenderSystem` and
  `RenderSystemSupport` sit at the module root. The module now has no layer-named package.
- **KDoc links across the moved packages resolve again.** Ten files referenced types that earlier
  slices had moved out of their package. Dokka's `failOnWarning` is off, so nothing failed — the
  links simply stopped resolving.

### Fixed

- **Shadow cascades are fitted to the real viewport, not the culling aspect.** `RenderSystem`
  fitted them with `CONSERVATIVE_ASPECT` -- a frustum three times wider than any real viewport,
  which exists so the CPU-side cull test never drops something visible. Harmless there, expensive
  here: a cascade's texel size is its box size over the map's fixed 2048, so the near cascade
  resolved 1.91cm per texel instead of 1.34, and a shadow edge is a staircase of exactly those.
  A test pins the number so it cannot drift back.

- **A camera rig with no target entity orbits a point instead of doing nothing.** Every mode
  except free-fly was skipped outright when `targetEntity` was null -- no pose written, no
  complaint. From behind the mouse that is not "no target", it is a camera that ignores input,
  and the two are indistinguishable. Orbit and top-down now use `offsetPosition` as their pivot;
  the modes that genuinely follow something say so through `CameraMode.needsTarget`.
- **A loaded scene's meshes carry their own bounds.** Nothing attached `MeshBounds` -- no scene
  document authored one and the loader did not -- so frustum culling had nothing to test, the
  spatial index nothing to index and the bounds overlay nothing to draw. All three looked
  implemented and were inert on every authored scene. `Mesh.localBounds` records what
  `createMesh` already computed, and `attachRenderableComponents` turns it into a component.

- **The shadow debug overlay draws the boxes the renderer actually uses.** It still drew
  `directionalShadowBox`'s fixed origin volume after cascades landed, so the overlay claimed a box
  the shadows were not coming from -- evidence pointing the wrong way, which is worse than no
  overlay. It now draws one box per cascade, a colour each, fitted to the same camera and aspect
  `RenderSystem` fits them to.

- **Frustum culling stopped rebuilding six planes per entity.** `RenderSystem` called
  `Frustum.intersects(lens, aspect, box)` once per entity, and that call derives the frustum's
  planes from the camera every time. Hoisted to once per frame, the same 10k-entity test drops
  from 1446us to 34us. Culling also now runs before an entity's uniforms are gathered, so a
  culled PBR entity no longer allocates the float array it was about to discard.

- **The published desktop jar shipped a native library for one platform only.** A host compiles just
  its own, and the publish job ran on a single macOS runner, so every release would have carried
  `macos-arm64` and nothing else — a Linux consumer got "not found for platform" at run time while
  the README promised macOS, Linux and Windows. Natives are now built on one runner per platform and
  merged before packaging, and `verifyDesktopNatives` fails the publish if any supported platform is
  missing. Windows is stated as unshipped rather than promised: nothing has ever compiled the
  desktop C++ on it.


- **An upgraded `vulkan-kmp` no longer loads the previous version's native library.** The extracted
  library was cached at `~/.awake/natives/<platform>/<libname>` and re-extracted only when that file
  was missing or empty, so whichever version a consumer ran first was reused forever. `System.load`
  accepted it, so nothing failed at load time — the mismatch surfaced as `UnsatisfiedLinkError` on
  the first native call, and before the package rename it would have silently run stale native code
  against new Kotlin. The cache path now contains a hash of the library's own bytes, so two builds
  cannot collide.

### Changed

- **BREAKING: every package moves from `io.github.ronjunevaldoz.*` to `io.github.awakelab.*`.**
  Consumers of the published `vulkan-kmp` bindings must update their imports. Nothing has been
  released under the current artifact coordinates yet, so no published version is affected.

  The Maven group has been `io.github.awake-lab` for a while and the packages were the last thing
  still saying something else. A package cannot contain a hyphen, so the conventional rendering
  drops it. Entries below this one keep the old package names on purpose — they record what was
  true when they were written.

### Fixed

- **A sample gets the naga library it needs to build a pipeline.** Shipped shaders are WGSL, so
  `VulkanShaderResolver` compiles one per pipeline through `NagaShaderCompiler` -- but only
  `:awake:backend:vulkan`'s own tests passed the library down. `:samples:studio`,
  `:samples:engine-showcase` and `:samples:ui-showcase` died at
  `UnsatisfiedLinkError: no awake_naga in java.library.path` while building their first pipeline.
  `useNagaShaderCompiler` now supplies it (and builds it first) for every desktop `run` and test
  task, so the fourth consumer cannot forget it.
- **A failed Vulkan setup frees the depth targets it had already created.** Both are built well
  before the features that own them, so a failure in between -- a shader that will not compile,
  a pipeline the device rejects -- left them with no owner and no one to destroy them:
  `vkDestroyDevice` then reported exactly one `DepthTarget`'s nine objects as leaked, on top of
  whatever really failed.
- **The inspector drew every component section on top of the last one.** `shadcnSidebar`'s
  scrollable slot was a `Box`, which stacks its children at one origin; upstream's `SidebarContent`
  is `flex flex-col gap-2`. Every caller had a single child when the scroll was added, so the two
  were indistinguishable until the inspector began emitting a section per component.

- **Headless Vulkan test fixtures destroy the device they cache.** Six suites cached a `Renderer`
  for the whole class and never tore it down: `Renderer.destroy` frees what the renderer built,
  not the render pass, pipelines, descriptor set layout, transfer context or device a fixture
  hands it, so each class leaked a live headless device for the rest of the JVM run. The symptom
  was elsewhere — a later suite failing at `vkCreateInstance` under the accumulated pressure.
  Each now releases everything in an `@AfterClass`, which also means the validation layer's
  leak report at `vkDestroyDevice` now fails the class that leaks rather than nothing at all.

- **Growing a Vulkan UI mesh no longer frees buffers the GPU is still reading.** Reallocation
  replaced every frame slot's buffer but waited on only the current frame's fence, so a slot an
  in-flight frame still referenced was freed underneath it — a use-after-free whose symptom is
  corruption or a lost device a frame or two later, not at the growth itself. It now waits on the
  device before touching the slots.
- **Textured draw runs drop a redundant clip like every other primitive.** `buildTextureRun` was
  the one primitive not consulting `canSkipExactClip`, so a textured run kept an exact clip its
  neighbours had already dropped, and broke the batch.
- **Debug line buffers grow instead of throwing.** `Renderer.drawDebugLines` enforced a fixed
  64-line capacity and threw above it, which killed the app on the first frame rather than dropping
  a few lines — a navigation grid drawing two lines per blocked sample hit 74 and the window stayed
  blank. Both backends' `LineMesh` now reallocate the affected frame slot, by one shared rule in
  `DebugLineLayout.grownVertexCapacity` rather than a copy per backend. A ceiling still throws, so a
  runaway producer fails loudly instead of allocating until it runs out of memory.
- **The scene inspector shows an entity that has no `Name`.** It found its entity by scanning every
  `Name` in the world and comparing ids, so a nameless entity selected from the viewport rendered an
  empty panel. `Name` is now a field above the sections rather than the label of the only one, which
  also stops the entity's name reading as though it were a component.

### Added

- **Floating origin moves the physics simulation too.** `PhysicsWorld.shiftOrigin(offset)` is
  implemented on all three Jolt backends, and `PhysicsOriginShiftListener` connects it to
  `FloatingOriginSystem`. Bodies are activated as they move: a sleeping body keeps its old
  position until something wakes it, so shifting the world under one without activating leaves it
  behind at the previous origin while everything else moved. This closes the gap the floating
  origin shipped with -- a scene mixing physics with a shifting origin is now supported rather
  than documented as broken.

- **Orbit and zoom in `engine-showcase`.** The camera stack existed and nothing installed it, so
  every demonstration was a fixed viewpoint. `cameraInputSystem()`/`cameraSystem()` are now wired
  and each showcase's primary camera gets a rig pivoted on whatever it was already looking at --
  turning it on does not move the shot, only makes it movable.

- **`World.debugSettings()`**, and the showcase toggles that needed it. The debug overlay reads a
  `WorldDebugSettings` component and returns immediately when none exists -- and nothing ever
  created one, so bounds, occluders, the light gizmo and the shadow overlay were unreachable
  rather than unimplemented, whatever a caller set. It is now get-or-create, and
  `engine-showcase`'s debug card carries bounds, shadow cascades, occluders, the light gizmo and
  wireframe alongside the navigation toggles.
- **A cascaded-shadows showcase.** Casters at 6, -6, -26 and -60 along the view -- one per
  cascade, so the near shadow is sharp and the far one still exists. The single fixed shadow box
  it replaced covers only the first of them.

- **Cascaded shadow maps.** A directional light now renders `DEFAULT_SHADOW_CASCADES` depth
  layers, each fitted to its own slice of the camera's frustum, and `lit_shadow` picks the
  cascade that contains the fragment. The single `directionalShadowBox` it replaces covers a
  fixed 12-unit volume at the world origin: correct for a demo sitting there, and silently
  shadowless for anything that walks away. Stored as a depth texture ARRAY rather than atlas
  tiles -- arrays cannot bleed between neighbours under filtering, and the atlas convention in
  the literature dates to D3D10, which could not do PCF on an array at all.
  `cascadeSplitDistances` blends uniform and logarithmic splits; each box is fitted to its
  slice's bounding sphere and texel-snapped, so a cascade keeps its size and alignment while the
  camera turns instead of making every shadow edge crawl.
- **`texture_depth_2d_array` in the shader DSL** -- `textureDepth2dArray` plus
  `textureSampleArrayLevelDepth`, the depth twin of the colour-array pair.

- **A spatial index, and the measurement that says when to install it.** `SpatialGrid` holds
  entity ids in uniform (x, z) columns and answers AABB, range and frustum queries;
  `SpatialIndexSystem` keeps one current from `Transform`/`MeshBounds`, and `RenderSystem` culls
  through it when a scene installs one. Read the numbers before you do: at 10k props over 2km
  (`SpatialCullingBenchmarks`) a frustum query costs 6.5us against 34us for testing every entity,
  but keeping the index current costs 67us -- so for culling *alone* the index loses, and it pays
  only when several queries share the one maintenance pass. Nothing installs it by default.
- **`List<Plane>.intersects(box)`** tests a box against planes the caller already built, and
  `Aabb.intersects`/`Aabb.squaredDistanceTo` join the geometry they belong beside.

- **LOD hysteresis.** `LodGroup` remembers the level it last drew and keeps drawing it until the
  distance leaves that level's threshold by `hysteresis` (10% of the threshold by default). The
  selection it replaces was a bare comparison, so an entity parked near a threshold swapped mesh
  on every frame the camera breathed -- visible as flickering geometry rather than as LOD.
  Outside the band the plain threshold still decides, so a teleport lands on the right level in
  one frame instead of stepping through the ones between, and `hysteresis = 0` reproduces the old
  behaviour exactly.

- **Floating origin.** `FloatingOriginSystem` keeps the `StreamObserver` near the scene's local
  origin by moving the world under it: past a threshold every root `Transform` is translated back
  in whole quantum steps, and `WorldOrigin` records where the local frame now sits, so absolute
  coordinates are unchanged. A `float` resolves about a centimetre at 100km, and the symptom of
  running out of it is a jittering frame in which every inspector value still looks correct.
  `WorldPartitionSystem` converts through the origin and streams in absolute space, so a shift
  cannot change which cells are loaded. `OriginShiftListener` is how anything holding its own
  world coordinates follows along -- physics does not yet, since Jolt is authoritative for a
  `PhysicsBody` and `PhysicsWorld` has no shift call, so that combination is unsupported for now.

- **Fixed: a showcase's own entities outlived it, and a long walk stopped after one leg.**
  Streamed terrain is spawned by the sample rather than by its scene document, so closing the scene
  left it drawing under whatever ran next; `EngineShowcase.onDeactivated` gives a showcase the
  chance to take back what it put in the world, and the loader calls it *before* the close, while
  there is still the right `World` to remove things from. And `HierarchicalNavGrid` now falls back
  along the corridor rather than giving up: the coarse graph can be right that a route exists while
  the fine grid cannot confirm the *furthest* loaded cell, because the way through needs a detour
  into a cell that has not streamed in yet. Aiming at nearer corridor cells until one is confirmed
  is what keeps an agent walking instead of stopping dead a few metres into a journey.
- **An agent can be sent somewhere that is not loaded yet.** Every path leaving the streaming
  radius used to come back empty, so an NPC asked to walk over the horizon stood still.
  `NavCellSummary` reduces a baked cell to its walkable regions and the border samples each one
  touches -- a few hundred bytes, kept after the cell unloads -- `CoarseNavGraph` searches those
  for a corridor of cells, and `HierarchicalNavGrid` returns a fine path as far along it as the
  world is actually loaded. Nodes are cell *regions* rather than cells, so a cell a ridge splits
  cannot claim a crossing that does not exist; the result is a leg rather than a whole journey,
  because waypoints through terrain nobody has verified are the confident-path-through-a-wall bug
  the design exists to avoid. `NavGridCellStreamer` fills the graph from the bake it was already
  doing, and `streamed-nav` paths through the hierarchy.
- **Streamed cells can carry a mesh, and a world can stream more than one thing.**
  `MeshCellStreamer` builds a cell's geometry off the frame thread and uploads plus spawns it on
  the frame thread, in the cell's own local space so a distant world does not lose float precision.
  Its mesh is destroyed a few frames after its cell unloads rather than with it -- freeing it
  immediately is a use-after-free the Vulkan validation layer caught on the first real run, since
  the GPU is still reading the command buffer that refers to it -- and `dispose` is the stalling
  teardown path. `CompositeCellStreamListener` runs several listeners from `WorldPartitionSystem`'s
  single async slot, loading concurrently and applying in order, because a second registration used
  to silently replace the first. `streamed-nav` now streams terrain alongside navigation, so the
  ground itself appears and disappears at the streaming radius.
- **The navigation overlay is opt-in, tells cells apart, and stops following you between demos.**
  It is off until the showcase panel's "Nav grid" checkbox turns it on: it is a diagnostic, and
  always-on it reads as part of the demonstration. The streamed-grid overlay now outlines every
  resident cell, shades blocked markers on a per-cell checkerboard -- a tile baked at the wrong
  offset breaks the pattern instead of hiding in it -- and gives each chaser its own route colour
  keyed by entity id, ending in a cross on its goal. Switching showcases clears the previous one's
  debug lines, which `Renderer.drawDebugLines` would otherwise keep showing over the new scene
  because it holds the last list it was handed rather than clearing per frame.
- **The engine showcase streams a world, and you can switch demos without restarting it.** A new
  `streamed-nav` showcase has no authored terrain at all: cells stream around the moving target,
  each one's walkability is baked off the frame thread as it arrives, and the chaser's path
  searches run off the frame thread too -- the first thing in the repo that exercises
  `StreamedNavGrid`, `NavGridCellStreamer` and an async `PathRequestSystem` together. The sample
  also gains its one and only piece of UI: a list of showcases down the left edge that switches
  scene on click, so `-Pawake.showcase=<id>` is no longer the only way to see a different demo.
  `navGridDebugLines` gained a streamed-grid overload that draws every resident cell's blocked
  samples at that cell's own offset.
- **The streamed nav grid fills itself from world partition streaming.** `NavGridCellStreamer` is
  an `AsyncWorldCellStreamListener` that bakes a cell's nav tile off the frame thread and publishes
  it into a `StreamedNavGrid` on apply, dropping it again on unload -- so navigation follows the
  observer with no per-cell wiring in the consumer. It rejects a grid whose cell size is not the
  world's at construction, which is the mismatch that otherwise shows up as paths mysteriously
  stopping at a boundary. A cell with no authored terrain loads no tile rather than pretending the
  ground is walkable.
- **Behaviour parameters are authored data, and tunable in the inspector.** `scene.json` gains
  `patrol`, `chase` and `flee` components carrying the values a designer actually changes -- stops,
  radii, dwell, speed -- with `chase.target` and `flee.threat` naming a node rather than pointing at
  an entity, resolved once every node exists. A name that matches nothing fails the load instead of
  producing an NPC that quietly stands still, and exporting a reference to an unnamed entity fails
  rather than dropping it. `SceneValidator` rejects a stopped behaviour and flee radii that cannot
  hysterese. The scene inspector edits the same three components live. Composing behaviours --
  which one runs when -- is still not authorable and deliberately so; see
  docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md.
- **Navigation spans the streamed world, and searching it can leave the frame thread.**
  `StreamedNavGrid` holds one baked tile per `WorldCellCoord` and searches across the resident
  set, so a route crosses cell boundaries without knowing they exist and stops at the edge of what
  is loaded rather than walking optimistically into unbaked terrain. `Heightmap.bakeNavGridCell`
  bakes the exactly-one-cell tile it needs — the existing `bakeNavGrid` samples both edges, which
  would put every boundary sample in two tiles at once. `PathRequestSystem` now takes an optional
  `searchScope`: given one it runs searches off the frame thread and applies answers on the next
  update, and `PathRequest.queryGeneration` is what stops the answer to a withdrawn question from
  landing on the question that replaced it. Tiles are immutable and the resident map is replaced
  rather than mutated, so streaming a cell out from under a running search is safe by
  construction. A* is now one implementation over a `NavField`, sparse rather than dense per
  sample, which is what lets the same search serve one tile and a streamed set.
- **Depth fog, the first consumer of the scene-depth pass.** `depthFogContentFeature(color)`
  draws a full-screen exponential fog after the scene's geometry, mixed by the world distance it
  unprojects from the camera-space depth target — so it fogs every pipeline in the frame,
  including content whose own shader knows nothing about fog. Colour alpha is density per world
  unit, packed the way `lit_shadow`'s per-fragment fog already packs it. Requires
  `RenderPlan.sceneDepthShaderSet`; `engine-showcase` now opts into both.
- **A `ContentFeature` can draw after the scene's geometry and can sample the scene depth.**
  `ContentPaint.AfterGeometry` puts a feature between the opaque draws and the UI pass on both
  backends (the sky's `BeforeGeometry` is still the default), and `samplesSceneDepth` declares the
  depth group so Vulkan compiles the pipeline with a layout at that set rather than leaving it to
  land at the wrong one. `RenderFrameContext.sceneDepthBinding(pipeline)` hands a feature the
  group to bind — non-null on WebGPU, whose bind groups are per pipeline, and null on Vulkan,
  which binds its own set with the pipeline, exactly as `PreparedDraw.sceneDepthBinding` already
  did for mesh draws. `PipelineVariant.Overlay` is `Background` with blending, for the
  full-screen effect that covers geometry rather than backing it.

- **`World.componentTypes(entity)`** returns the component types attached to an entity, in
  registration order, empty for a dead one. The only way to enumerate an entity's components was
  `inspectStorage`, named and documented as storage diagnostics and returning each type's storage
  kind and count — a question about performance rather than about the entity. Both KDocs now point
  at each other, and the ECS README documents the new call.
- **The scene inspector sections every component on the selected entity**, rather than Name and
  Transform alone. A component with no editor yet says so instead of being omitted, and the list is
  read from the world, so a component no editor module knows about still gets a section.
- **Editable fields for the engine's own components** in that inspector: `PbrMaterial`'s metallic
  and roughness, `Light`'s colour and intensity (plus range, for a point light only — a directional
  light has no falloff), `Camera`'s field of view, near and far, and `SpinControl`'s speed. Field of
  view reads in degrees; the lens keeps radians. Each is one undo step, and one `SceneScalarCommand`
  covers all of them: its write lambda assigns for a `var` field and puts a `copy` back for a `val`
  one, so a component that cannot be mutated in place needs no command of its own.

- **Enum and boolean fields in the inspector.** `Light.type` and `MeshRenderer.cullMode` are toggle
  groups; `MeshRenderer.visible` and `transparent` are switches. Switching a light to `Point` is
  what reveals its range. One `SceneValueCommand<T>` now covers numbers, enums and booleans —
  numbers merge consecutive keystrokes into one undo step, toggles do not, because every click is a
  state the user chose to stop at.

  No `Camera.isPrimary` toggle: `SceneEditorCameraSystem.syncPrimaryCamera` assigns that field on
  every camera every frame from whether the scene view is active, so a checkbox would be reverted
  before the user released the mouse.
- **`FleeBehavior`/`FleeAiSystem` and `PatrolBehavior`/`PatrolAiSystem`** in
  `io.github.ronjunevaldoz.awake.scene.ai`. Fleeing uses two radii so an entity hovering at the
  threshold does not start and stop every frame, and fans its escape direction when the grid answers
  `Unreachable` rather than standing still against a wall. Patrolling walks authored stops with
  `Loop`/`PingPong`/`Once` styles, a dwell at each stop, and skips an unreachable stop instead of
  deadlocking the beat.
- **`RouteFollower`**, the state every navmesh-walking behaviour shares. Chase, flee and patrol
  differ only in how they pick a goal; asking on an interval, adopting the answer and stepping along
  it is now written once. `ChaseBehavior` implements it, so `ChaseAiSystem` is 26 lines instead of
  108. This is the `MoveTo` leaf
  [the decision-runtime plan](docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md) predicted,
  reached from three real behaviours rather than designed up front.

- **`EditorCommand`/`EditorHistory` — the editor's undo stack.** `apply()`/`revert()` take no
  arguments and a command closes over what it edits, so `:awake:editor` stays free of a world,
  document, or renderer and scene commands can live in `:awake:editor:scene`. `revert()` restores
  the state `apply()` replaced rather than recomputing an inverse, because a gizmo drag undone by
  subtracting its delta drifts against a system editing the same value. Consecutive commands
  sharing a `mergeKey` collapse into one entry — a drag emits one per frame and a typed field one
  per keystroke — with `absorb()` keeping the earlier command's starting state. Dirty tracking
  compares identity against the command on top at save time, so "edited twice then undone twice"
  is correctly the saved state. Nothing is wired to it yet.
- **A console dock, and a dock slot in the editor shell.** `EditorConsolePanel` reads a
  `LogRingBuffer` in place — no per-frame snapshot — with error and warning badges, a Clear button,
  and the buffer's `droppedCount` on screen so the panel cannot claim a completeness it lacks. It
  follows the tail only when the reader is already at it. `AwakeEditorShell` grows an optional
  `dock` slot, the fourth region the archived layout design and the shadcn reference both specify;
  null by default, and null costs no layout.

- **`awake:core:logging` — the engine can say things now.** Nothing in `awake/core` or
  `awake/engine` had a logging facility at all, which is what blocks the editor's console dock and
  why a renderer in a bad state has nowhere to report it. `Logger` takes every message as a lambda
  behind an `isEnabled` check, so a disabled call builds no string and a log line is affordable
  inside the frame loop. Records carry a frame number, since an engine log is read against the frame
  it happened in. `LogRingBuffer` is bounded and reports `droppedCount`, because a ring that
  discards silently makes its reader claim a completeness it does not have. `Log` is process-wide —
  a named exception to the host-owns-it rule, because logging has to work inside a buffer allocation
  and an ECS query, neither of which is handed anything.

- **Pointer input carries button identity.** `PointerButton` (Primary/Secondary/Middle/Back/
  Forward) plus `InputSnapshot.buttonsDown`/`buttonsPressed`/`buttonsReleased`, modelled as sets the
  way keys already are — `17-modifier-parity.md` marked `onClick`/`onDrag` N/A precisely because
  "input here does not carry button identity". `GameplayInput.isDown(button)` applies the same
  UI-capture rule as `pointerDown`, so a middle-drag that began over a panel belongs to the panel.
  `Press`/`SecondaryPress` are untouched, so no existing handler changes behaviour. Fixes a real web
  bug on the way: the wasmJs bridge treated every non-secondary button as primary, so a middle-click
  actuated whatever was under the cursor.

- **An editor entity id now carries the entity's generation.** `EditorEntityId(entity.id)` threw
  away the generation `Entity` packs beside the id, so two different entities — one destroyed, one
  created into its recycled id — collapsed onto the same editor id, and a selection or an undo would
  follow the replacement. The scene adapter encodes the packed handle instead; the generic editor
  still treats the id as opaque. `World.entityById(id)` is the only safe route back from a bare
  `Int` (what a viewport pick returns) and refuses a dead id rather than returning whatever now owns
  it. This unblocks undoable delete and duplicate.

- **Multi-entity selection, and modifier keys on the pointer.** `EditorSelection` holds a set plus
  a primary (always a member), replacing the single `selectedEntityId`. Populating it needed
  `PointerEvent.modifiers` and `LocalPointerModifiers`, since the pointer path carried no modifier
  state at all and shift-click could not be expressed — the audit was wrong to say the singular type
  was the only blocker. `pointerModifiers()` returns a shared instance when nothing is held, so it
  costs no per-frame allocation.
- **Add and rename entities, both undoable.** Rename is the inspector's Name field and merges by
  entity, so typing a name is one undo step. Add sits in the outliner header and selects what it
  creates. Delete and duplicate are absent on purpose: `World.destroy` recycles the entity id and an
  `EditorEntityId` is that number, so undoing a delete can hand the id to a different entity.
- **Play no longer discards your work, and simulation cannot damage the scene.** `startPlay()` was
  `Unit` and `stopPlay()` reloaded the file, throwing away the session's edits. Start now captures
  the edited world as a document and Stop rebuilds from it. Not an isolated second world — Studio's
  systems and renderer are bound to one world — but the guarantee is the same. The undo history is
  cleared at Stop, because rebuilding replaces every component instance the stack refers to.

- **Studio can save.** `SceneLoader.fromWorld` and `writeSceneDocument` had existed and been
  correct with only a test calling them, so every editor edit evaporated on reload. `StudioFixture`
  now keeps the authored mesh/material each renderable was instantiated from -- the resolver
  `fromWorld` demands, since a live `MeshRenderer` holds GPU handles and cannot be serialized back
  without it. `EditorSaveButton` reads `EditorHistory.isDirty`, so the unsaved marker cannot drift
  from the edits it describes, and `markSaved()` runs after the write so a failed export stays
  dirty. Play is still a stub and there is no Open yet.

- **Undo and redo, wired through every scene mutation.** The gizmo drag, the inspector's axis
  fields and the outliner's visibility toggle now record what they replaced, via
  `SceneVectorCommand`/`SceneVisibilityCommand`. A whole drag is one entry -- the gizmo captures on
  grab and records on release, so it costs one command rather than one per frame inside
  `System.update`. Typed axis edits merge by vector instance, so stepping back through "1", "12",
  "125" is one step. `EditorHistoryButtons` surfaces both in the toolbar, disabled when their stack
  is empty and labelled with the step ("Undo Move"). `ProvideAwakeEditor` takes the history
  explicitly, alongside the session, providers and store.

- **Scene outliner filter, type glyphs and count.** `SceneHierarchyPanel` gained a search field
  that keeps a parent whose child matches (filtering row by row hides the only path to the match),
  per-type camera/light/mesh glyphs, and a header count of the rows currently shown.
- **Three compose modules have detekt baselines, and three real findings are gone instead.**
  `compose:ui`, `compose:ui-testing` and `compose:foundation` had no `detekt-baseline.xml` at all, so
  the repo-wide gate the pre-push hook runs failed on findings nobody had introduced — a gate that is
  red by default teaches people to bypass it. 50 structural findings are baselined; the three that
  were dead code were deleted rather than suppressed.

  One of those was worth the detour. `DrawWithCacheNode` carried an unread `lastCallback` field —
  the half-written version of "invalidate when the lambda changes". Wiring it would have destroyed
  the cache: with no compiler plugin a capturing lambda is a fresh instance every pass, so identity
  would miss every frame, in the engine where tessellating a shape per frame cost twenty
  milliseconds. The field is gone and the real consequence is written down instead — a
  `drawWithCache` whose captures change without a size change keeps drawing the old result, and a
  caller needing otherwise must key its own cache as `BorderNode` does.

  Controlled: a newly introduced finding still fails the build, so these are ratchets rather than
  mute buttons. The other 21 modules that fail `detekt` are untouched and still fail.

- **Open-World Subsystems & Runtime Shader Architecture:**
  - **Subsystem 1 (GPU Multi-Texture Terrain Splatting & Clipmap Morphing):** Added `TerrainComponent`, `TerrainClipmapSystem`, `TerrainSplatShaderSource` (WGSL runtime continuous alpha-morphing to prevent LOD pop, 4-channel RGBA texture array splatting), and `TerrainClipmapConfig`.
  - **Subsystem 2 (Spatial World Partitioning & Streaming):** Added `WorldCellCoord`, `WorldPartitionConfig`, and `WorldPartitionSystem` in `:awake:scene:scene-core` for distance-ring based spatial cell streaming with hysteresis band to prevent loading thrash.
  - **Subsystem 3 (Modular Skeletal Character System):** Added `ModularCharacterComponent` and `ModularSkeletalSystem` in `:awake:scene:rendering` for runtime slot attach/detach with shared joint palette evaluation across modular skinned slots without CPU vertex re-concatenation.
- **Declared material bindings.** `GroupBindings`/`ResourceBinding`/`ResourceKind`/`ShaderStage` in `:awake:engine:render:contract`, plus `PipelineSpec.materialBindings`, let a pipeline state what occupies its material group instead of inheriting one hardcoded glTF metallic-roughness shape. Vulkan derives its descriptor set layout and pool from the declaration; WebGPU derives its bind-group entries. Omitting it keeps the previous layout exactly. `BindingSemantic` became a sealed interface with a `Custom(name)` case so a consumer can name a group the engine does not ([D28](docs/decisions/D28-open-world-framework-boundary.md)).

- **Texture arrays.** `TextureAsset.layerCount` lets one sampled binding hold several
  same-sized layers -- a splat terrain's diffuse layers without spending a binding each. Vulkan
  uploads them as a `VK_IMAGE_VIEW_TYPE_2D_ARRAY`, WebGPU as a `"2d-array"` view; neither
  generates mips for an array. ASL declares them with `texture2dArray` and reads them with
  `textureSampleArrayLevel`, so the derived `GroupBindings` still comes from the shader itself,
  and `ResourceBinding.arrayed` lets `ContentFeature` reject a layer count that disagrees with
  the declaration. A single-layer `TextureAsset` behaves exactly as before
  ([D28](docs/decisions/D28-open-world-framework-boundary.md)).
- **Vulkan Pipeline Shader Entry Point Alignment:** `LineRenderPipeline`, `UiRenderPipeline`, and `DepthOnlyPipeline` now configure entry points defaulting to `vertexMain` and `fragmentMain` to match generated WGSL/ASL SPIR-V shader outputs.
- **`vulkan-kmp` Maven Central release pipeline & embedded native loader.** Standalone
  Vulkan API bindings published under `io.github.awake-lab:vulkan-kmp` and companion AAR
  `io.github.awake-lab:vulkan-kmp-android-native`.
  - Ships `VulkanNativeLoader` on Desktop JVM (macOS ARM64/x86_64, Linux x86_64, Windows x64)
    which auto-extracts precompiled native libraries from the JAR to a local cache directory
    with zero configuration and no manual `-Djava.library.path` required.
  - Applied `binary-compatibility-validator` tracking public API baseline in `bindings.api`.
  - Added `NOTICE.md` documenting bundled third-party licenses (MoltenVK, Khronos Vulkan
    headers/validation layers, GLFW, Jolt Physics).
  - Modernized GitHub Actions release workflow (`.github/workflows/build-and-publish.yml`)
    with tag-triggered publishing, native matrix builds, and secure Gradle credential env vars.
- **`:awake:asset:shader-dsl` — ASL, procedural shader authoring.** Shaders defined in Kotlin
  emit WGSL text for the existing naga pipeline (`validateAwakeShaders`/`syncAwakeShaders`
  unchanged). `by`-delegated builders make the Kotlin property name the WGSL identifier;
  structural mistakes (duplicate bindings/locations, unwritten varyings, constructor arity,
  shape-mismatched math) throw at definition time, while real type checking stays with naga.
  Ships `AslEvaluator`, a CPU fragment interpreter used as both test oracle and headless
  terminal preview (`:awake:asset:shader-dsl:previewShader` — ANSI half-blocks, no file, no
  GPU/UI dependency), plus two proof definitions: `TriangleShader` (regenerates
  `triangle.wgsl` code-identically minus comments) and `CheckerShader` (new content). Plan:
  `docs/tasks/2026-08-23-asl-procedural-shader-plan.md`.
- **`samples/studio`'s `triangle.wgsl` is generated from ASL.** `AslShaderDriftTest` keeps the
  committed file equal to what `TriangleShader` emits (re-record: `AWAKE_RECORD_SHADERS=1`);
  the synced Vulkan `.spv` stayed byte-identical, so the swap changed no rendered pixel.
- **`:awake:asset:shader-compiler` — runtime WGSL→SPIR-V on device (naga as a library).**
  A ~150-line Rust shim over the `naga` crate, bridged full-KMP: JNI from Rust on
  desktop/Android, cinterop static lib on iOS, throwing wasmJs actual (browsers take WGSL
  directly). On-demand cargo build tasks; desktop verified with real JNI round-trip tests.
- **`generateAslShaders` + the last literal buffer sizes gone.** One command re-emits all 9
  generated `.wgsl`, naga-validates, and re-syncs `.spv` (byte-stable against the committed
  tree; `--continuous` makes it the dev loop). Studio's skinned/instanced/particle drivers
  now size materials from the layouts (`createMaterial(SkinnedUniformLayout)` etc.) instead
  of three hand literals — one of which was another `MAX_JOINTS` twin.
- **All 9 shaders are generated; every uniform struct derives from a renderer layout.**
  `skybox`/`textured`/`particle` ported to ASL (the DSL gained `textureSample`, derivative
  builtins, `vertex_index`, local const arrays, and matrix-column reads), and the mesh trio's
  structs now derive from `InstancedUniformLayout`/`SkinnedUniformLayout` instead of a hand
  list. `textured.wgsl`'s `material` field is renamed `pbrFactors` — the committed shader had
  drifted from its own layout's name. Verified end to end once `scene:runtime` compiled again:
  the full Vulkan, WebGPU, and studio desktop suites pass on the regenerated shaders.
- **The mesh-variant trio is generated from one parameterized ASL definition.**
  `instanced`/`skinned`/`skinned_instanced` come from `meshVariant(instanced, skinned)` in
  `awake:asset:shader-pack` — the hand-maintained cross-product is gone and a new axis is a
  flag, not a fourth file. `MAX_JOINTS` moved to `render:contract` (backends alias it), the
  synced Vulkan `.spv` stayed byte-identical, and the full Vulkan/WebGPU/studio desktop
  suites pass.
- **The shadow shader pair is generated from ASL, from one shared struct.** The DSL grew
  arrays, runtime control flow, module functions, and texture bindings;
  `shadow_depth.wgsl`/`lit_shadow.wgsl` in `awake:asset:shader-pack` now come from
  `AslShadowShaders.kt`, whose single field list makes the lightMvp-offset drift class
  (the empty-shadow-map incident) unrepresentable. Guarded by `AslShadowDriftTest`; Vulkan
  `.spv` stayed byte-identical and the headless shadow-map GPU test passes.

- **`samples:ui-showcase` moves onto the compose engine.** The last production `overlay { }`
  consumer -- it ran on `AppUiRuntime`/`appUi { }`, a separate ui-core-only 2D runtime with no
  `content { }` arm, so it mounts on `SceneAppLifecycleRuntime` (Studio's own runtime) with no
  entities and no systems instead of a second runtime built to duplicate one frame loop. All 51
  catalog pages convert in the same step, since every page shares one typealias. 41 port for real;
  10 become `showcasePlaceholder(...)` for a recipe that does not exist yet (Combobox, InputGroup,
  InputOTP, RangeSlider, Select, ScrollArea, AlertDialog, ContextMenu, Sheet, Drawer). Verified the
  shell's own historic sidebar defect (a weighted body collapsing to 0px with the team switcher in
  the `header` slot) does not carry over before shipping the shape that would have reproduced it.
  `shadcnRadioGroup` and `shadcnToggle` are still the polled shape `shadcnSlider`/`shadcnSwitch`
  were until this pass -- flagged as the next thing to convert, not fixed here.

- **shadcn's `Table` family on the compose engine** — `shadcnTable`, `shadcnTableHeader`,
  `shadcnTableBody`, `shadcnTableFooter`, `shadcnTableRow`, `shadcnTableHead`, `shadcnTableCell`,
  `shadcnTableCaption`. Eight parts matching upstream's eight exports, replacing the `ui-core`
  recipe's own three-part shape (`shadcnTable`/`row`/`cell`).

  Measured against a new `table-demo` reference case, which caught a real defect: a body row and
  a header row are the same 40px in a first-pass reading of the Tailwind classes, but the browser
  measures 40 vs 37. `TableHead` carries `h-10`; `TableCell` does not; a row has no height of its
  own at all and is only ever as tall as the cells inside it — giving the row itself a fixed
  height made every body row 3px too tall. `TableFooter`'s `font-medium` also turned out to reach
  every cell through CSS inheritance rather than a per-cell class, which the port reproduces with
  an ambient default (`LocalTableCellWeight`) a cell can still override.

  "Which row is last" — upstream's `[&_tr:last-child]:border-0` — is answered by declaring rather
  than by a descendant selector asking the tree afterward: `shadcnTableBody`/`shadcnTableFooter`
  take a builder scope that collects every row before placing any of them, the shape
  `shadcnResizablePanelGroup` and `shadcnToggleGroup` already use for the same reason.

- **shadcn's `Field` family on the compose engine** — `shadcnFieldSet`, `shadcnFieldLegend`,
  `shadcnFieldGroup`, `shadcnField`, `shadcnFieldContent`, `shadcnFieldLabel`, `shadcnFieldTitle`,
  `shadcnFieldDescription`, `shadcnFieldError`, `shadcnFieldSeparator`. Ten parts, matching
  upstream's ten exports — *not* the seven `shadcnFieldTextField`-style composites the `ui-core`
  recipes carry, which have no counterpart upstream and existed only because a polled `Boolean`
  made `field { label; control; error }` awkward to write by hand.

  Measured against the browser rather than read off Tailwind's scale, which caught three defects
  on the first pass: a legend missing its `mb-3` (36px to the group upstream, 24 in the port), and
  a description and an error message at weight 500 where the browser says 400. Three upstream
  behaviours are named as absent rather than approximated: `orientation="responsive"` (a container
  query — a measure-dependent style the engine has no mechanism for), descendant-driven gaps
  (`has-[>[data-slot=checkbox-group]]:gap-3`), and the label-as-card pattern.

- **`shadcnCheckbox`, `shadcnCollapsible` and `shadcnSelect` take callbacks.** They returned a
  `Boolean` that the next build read, so the handler ran a build later than the press and against
  whatever state that build happened to see. The last three recipes on the compose engine still
  shaped that way, after `shadcnSidebarMenuItem`; `shadcnSelect`'s is `onClick`, since it reports a
  trigger press rather than a value the recipe knows.

- **`shadcnTooltipped` — a tooltip that anchors itself.** The engine's first `Layer` consumer, and
  the anchoring `LayoutTree`'s own comment deferred: the bubble sits above its trigger, flips below
  when there is no room, and is pulled back inside the viewport. The layer reports itself as
  zero-sized so it cannot take the hover keeping it open — hit-testing walks layers before content
  and stops at the first one containing the point, so a tooltip with real bounds over its own
  trigger would blink at frame rate. No open delay: that needs a clock the recipe does not have,
  and is better absent than approximated.
  An icon-only `shadcnToggleGroup` item gets one automatically, since a glyph with no drawn label
  is otherwise discoverable only to whoever wrote it.

- **`shadcnToggleGroup` on the compose engine.** shadcn's own rule is that a button group groups
  buttons that *perform an action* and a toggle group groups buttons that *toggle a state*;
  Studio's viewport pills were button groups faking pressed with a variant, so a screen reader
  heard thirteen unrelated buttons where the screen shows one modal choice and two sets of
  switches. Single-select reports a radio group of radios. Richer than the `ui-core` recipe it
  replaces, which took `List<String>` and indices: items carry their own label, glyph and test
  tag. Joined ends still draw every corner rounded — `Style` carries one `cornerRadius`, not
  four, the same limitation `shadcnButtonGroup` states.

  This also retires a known issue listed below: *"a wrap-height `shadcnToggleGroup` creates a
  100,000px hit rectangle"*, whose cause was an internal `fillMaxHeight()` resolving against the
  trial-measurement sentinel. There are no trial passes on this engine and the recipe has no
  `fillMaxHeight()`, so the failure is structurally absent rather than fixed — and the viewport
  header it was blocking is what now uses it.

- **Every rotate ring turns about the world axis it is drawn on.** The drag used to be added into
  one component of `Transform.rotation`, and since `fromEuler`'s `Qx*Qy*Qz` is the matrix
  `Rz*Ry*Rx`, that meant three different things: the Z ring turned about world Z, the Y ring only
  while `rotation.z` was zero, and the X ring about the object's own local X. Three identical rings
  labelled by world axis, obeying three different rules, and wrong on any already-rotated object —
  not only near a pole. The drag is now composed as a quaternion. Storage stays Euler, so the pole
  itself is unchanged.

- **Grid, angle and scale snapping for the gizmo**, via `GizmoSnap` on `GizmoFrame`. A step of zero
  means that tool does not snap, so there is no separate enabled flag to fall out of step with it.
  Snapping quantises the drag's running total rather than each frame's delta — a delta below half a
  step rounds to nothing, so snapping deltas would leave a slowly dragged object frozen however far
  the pointer travelled. Positions and scales snap to absolute multiples; a turn snaps in
  increments from wherever it was grabbed, because an orientation has no grid to land on.
- **A drag no longer accumulates float drift.** Every frame now recomputes the transform from the
  value captured at the grab, instead of compounding one more addition — or, for rotate, one more
  Euler round trip — onto the last frame's result.

- **Local and world space for the gizmo**, via `GizmoSpace` on `GizmoFrame`. `GizmoBasis` says
  where each handle points — the identity for world space, the object's own rotation for local —
  and handle geometry, hit-testing and the drag all read the same one. The direction is frozen at
  the grab, because in local space the basis comes from a rotation a rotate drag is in the middle
  of changing, and re-reading it each frame would let the axis chase itself.
- **Scale handles now point along the axes they actually resize.** `Transform.scale` is three
  factors along the object's own axes, but the handles were drawn along world axes, so on a rotated
  object the handle pointed one way and the size changed along another. Scale is local whatever
  space the caller asks for.

### Changed

- **Scene depth: a shader can read the depth of the scene in front of it.** `RenderPlan
  .sceneDepthShaderSet` opts into a camera-space depth pre-pass, bound at
  `BindingSemantic.SceneDepth` for water, soft particles and depth fog -- none of which the
  engine could express, because the one depth pass that existed renders from the light for
  shadows. Live on both backends and proven on pixels on each. A full extra geometry pass per
  frame, hence opt-in; the scene pass cannot supply it, since content features draw inside it
  and neither backend lets a shader sample the depth attachment it is writing. Engine-owned
  descriptor sets now bind by semantic rather than through one hardcoded shadow slot, so shadow
  depth and scene depth can co-occur.
- **`PipelineSpec` carries `ShaderSource`, and every shipped shader carries its own WGSL.** The
  spec held two resource paths as `String`, which made an inline shader inexpressible; it holds
  two `ShaderSource` now (moved down into `:awake:engine:render:contract`, acyclic), and
  `vertexEntryPoint`/`fragmentEntryPoint` became accessors. `PackShaderSets` emits the ten
  content shaders from their ASL definitions the way `EngineShaderSets` already did for the
  engine's six, so a `RenderPlan` names a set instead of `shaderSet("lit_shadow")`. Removes the
  ten canonical `.wgsl`, `AslPackDriftTest`, `GenerateShaders`, and the shader-pipeline plugin
  from `shader-pack`, `studio` and `engine-showcase`. Tracked shader files: 99 → 5.
- **The engine's own shaders ship as ASL rather than resources.** `EngineShaderSets` carries
  WGSL inline (`ShaderSource.InlineText`, which gained the `entryPoint` its siblings already
  had), emitted from an ASL definition at construction, so the four UI shaders, the target
  compositor and debug lines need no file on any platform -- including iOS and wasm, whose
  loaders cannot see a library's own resources. `debug_line` was the last hand-written WGSL of
  the six and is ASL now too. Content shaders still resolve by path: `PipelineSpec` carries
  shader paths as `String`, so inline source cannot reach a scene pipeline yet.
- **Both backends load WGSL; generated shader artifacts are no longer committed.** Vulkan reads
  `assets/shader/vulkan/$name.wgsl` and compiles it through the in-process naga binding
  (`VulkanShaderResolver`, which caches per path) instead of a shipped `.vert.spv`/`.frag.spv`
  pair. `syncAwakeShaders` compiles nothing now -- it places the canonical WGSL where each
  loader looks, and `validateAwakeShaders` is where naga still runs. Tracked shader files drop
  from 99 to 21, and the samples' copies are gitignored build output. Removes the version-skew
  hazard between the build-time naga CLI and the runtime `awake-naga-bridge` crate, which had
  already drifted on Y-axis handling. `perBackendShaderSet` and `VulkanEngine`'s
  `runtimeShaders` flag are gone with the SPIR-V they existed for; both had no users.

### Removed

- **`recomposeScope`, `mutableStateOf` and the tracked `InteractionSource` accessors.** The compose
  engine composes the whole tree every frame, so a skip boundary bought nothing it did not also
  cost. Placed by hand rather than generated by a compiler plugin, every value a scope read had to
  be observable or the scope silently never re-ran — hover, focus and typing each shipped that bug.
  It could not deliver the win either: invalidation had to dirty the whole ancestor chain to reach
  a nested scope, because there is no slot table to seek into. Measured over Studio's shell it
  executed 0 scopes and skipped 1 per frame. Retained state is now a plain class held by
  `remember`, which `ScrollState`, `TextFieldState` and `InteractionSource` already were.
  `TextFieldState.collectAsState` and its revision counter go with them.

### Added

- **`navGridDebugLines(world, tile, surfaceAt)`** in `:awake:scene:navigation`. World-space
  wireframes for every sample the bake rejected and the route each chaser is walking. Returns lines
  rather than drawing them, matching `debugVisualizationLines` — `Renderer.drawDebugLines` replaces
  the frame's line buffer instead of appending, so a caller with several sources has to merge them
  into one call. The markers are the grid's opinion rather than the mesh's, so a bad bake shows up
  instead of hiding behind terrain that looks fine.
- **"Navigation chase" showcase** in `samples:engine-showcase`. A cube chases another across
  terrain, around a ridge it cannot climb. The ridge is terrain rather than a collider: nothing
  tells the chaser a wall exists, and it detours only because `bakeNavGrid` read the slope. The
  target slides between the two sides so the route flips end to end and the chaser re-plans.
- **`:awake:scene:navigation` module.** `NavGridTile`, the slope bake, A*, smoothing and `NavGrid`
  move here from `:awake:asset:terrain`, in package `…scene.navigation.grid`. The dependency runs
  `navigation → asset:terrain` (a grid is baked from a `Heightmap`) and `navigation → scene-core`
  (the `NavMesh` seam it implements), so terrain stays unaware of navigation and `scene-core` stays
  free of a terrain dependency. Breaking for anyone importing the `NavGrid*` types from
  `io.github.ronjunevaldoz.awake.asset.terrain`.
- **`PathRequest`, `PathStatus` and `PathRequestSystem`** in `:awake:scene:scene-core`. A
  request-and-poll navigation query as an ECS component: a path is not something an entity can wait
  for inside one frame. This is the seam the decision-runtime plan needs — a `MoveTo` behaviour
  writes a request, stays running while the status is `Pending`, and cancels if interrupted. The
  search currently runs inline on the frame thread; the contract is what lets that move off-thread
  later without a consumer noticing.
- **`NavGrid`**, a `NavMesh` over one baked tile. Multi-tile search across a streamed set is
  deferred — nothing bakes or streams per-cell navigation tiles yet.
- **`NavGridTile.smoothPath(path)`.** Drops waypoints the ones around them can already see past,
  turning A*'s staircase into straight runs between the corners that matter. A supercover
  traversal, not a Bresenham line, so it cannot straighten through a diagonal gap too thin to walk;
  and it applies the same both-orthogonals-open corner rule as `findPath`, so smoothing cannot undo
  a corner the search refused to cut. Waypoint Y is carried through untouched.
- **`NavGridTile.findPath(...)`.** 8-connected A* with an octile heuristic over a baked navigation
  grid, in `commonMain`. Deliberately not Jump Point Search: JPS pruning is only sound on a
  uniform-cost grid, and returns plausible-looking non-optimal paths once cost varies with slope.
  Diagonals require both shared orthogonal neighbours to be open, so an agent cannot shave a wall
  corner. Ties break on node index rather than heap order, so a repeated query reproduces its path.
  Waypoints carry world X and Z; Y is the caller's to resolve. Phase 2 of
  [the navgrid plan](docs/tasks/2026-08-30-navgrid-navigation-plan.md); paths are not smoothed yet.
- **`NavGridTile` and `Heightmap.bakeNavGrid(cellSize, maxSlopeDegrees)`.** Derives walkability from
  terrain slope into a `LongArray` bitset — 32KB for a 512m cell at 1m navigation resolution, versus
  256KB as bytes. Navigation resolution is independent of the heightmap's own sample spacing.
  Walkability uses forward and backward differences rather than the central difference
  `toPositionNormalColorMesh` uses for normals: a central difference halves the apparent rise of a
  one-cell step and would bake the lip of a drop as walkable. Phase 1 of
  [the navgrid plan](docs/tasks/2026-08-30-navgrid-navigation-plan.md).
- **`Heightmap.heightAtWorld(worldX, worldZ)`.** Bilinearly samples terrain height at a world
  position, applying `scale.y` so the result is a world Y rather than the raw sample `heightAt`
  returns. `Float.NaN` outside the map — not a nullable `Float`, which would box on every call, and
  a navigation bake calls this hundreds of thousands of times per world cell. First step of
  [the navgrid plan](docs/tasks/2026-08-30-navgrid-navigation-plan.md)'s Phase 1.

- **`SceneComponentInspector`**, so a module the editor cannot depend on can still contribute
  inspector fields. `awake:editor:scene` matches its own components with a `when`, but cannot reach
  `PhysicsBody` without pulling the physics backend into the editor, nor every future gameplay
  component without depending on everything. A host that sees both registers an inspector through
  the existing `EditorProviders`. Contributed fields go through a `SceneFieldScope` rather than a
  `Composer`, so a provider gets the editor's own controls and its undo behaviour without depending
  on Compose or being able to record a command wrongly.

- **Gizmo handles are a constant screen size.** The handle was a fixed world length of 1.5, so it
  shrank with distance like the object it was attached to — measured against the editor's own
  projection, the same handle covered 274 px on a near object and 15 px on one 53 units further
  back, and a large object swallowed its handles entirely. `handleLengthAt` derives the world
  length from the distance to the eye under perspective, or from `orthoHalfHeight` under
  orthographic, so a handle always covers the same slice of the viewport. Drawing, hit-testing and
  dragging all take the same length, so what is drawn stays what is grabbable.

- **Each gizmo tool draws its own handle.** Rotate and scale drew the same three straight axis
  lines as move, so the gizmo looked identical in all three modes and the only way to tell which
  was active was the toolbar. Move is an arrow, rotate a ring in the plane that axis turns things
  through, scale a shaft with a box on the end. Drawing and hit-testing read one shared geometry,
  so a ring is grabbable all the way round rather than along a diameter it no longer has.
- **Rotate reads its drag around the ring**, at the point grabbed, instead of along the axis'
  screen direction. Grabbing the top of a ring and grabbing its side are different drags, which a
  single axis direction cannot express — one of them was always at right angles to the way the
  handle visibly went.

### Changed

- **`ChaseAiSystem` no longer pathfinds, and takes no constructor arguments.** It decides *when* to
  ask — every `ChaseBehavior.repathInterval` seconds — and writes a `PathRequest`;
  `PathRequestSystem` decides how to answer. A chaser now needs `Transform`, `ChaseBehavior` and
  `PathRequest`, and one missing its request is skipped rather than steering blind. Schedule order
  between the two systems is not load-bearing: a chaser keeps walking its previous route while a
  replacement is outstanding, so a repath is never a visible stutter. Breaking — `ChaseAiSystem(navMesh)`
  becomes `ChaseAiSystem()`, with the `NavMesh` passed to `PathRequestSystem` instead.
- **`ChaseAiSystem` and `ChaseBehavior` moved to `io.github.ronjunevaldoz.awake.scene.ai`.** They
  were one feature split across `scene.core.components` and `scene.core.systems`. Package layout now
  follows the subsystem rule that already governs module names — see
  [module-architecture](docs/reference/module-architecture.md)'s "Packages inside a module".
- **`ChaseAiSystem` is query-driven; one system now drives every chaser.** It took a pair of
  `Transform`s and a `NavMesh` in its constructor, so a scene with twenty NPCs needed twenty of
  these in its schedule. It now takes only the `NavMesh` and iterates
  `family<Transform, ChaseBehavior>()`, with per-NPC target, tuning and path state on the new
  `ChaseBehavior` component in `:awake:scene:scene-core`. Breaking for anyone constructing it
  directly: add a `ChaseBehavior` to the NPC entity instead of passing transforms. Two behaviour
  fixes come with it — the target's position is read through the world each tick rather than held
  as a reference, so a destroyed target is skipped instead of steered toward, and the repath timer
  survives a pool `reset()`. See
  [the behavior tree & state machine plan](docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md)'s
  Phase 0.
- **Overlays fade instead of vanishing between two frames.** Every overlay here unmounted the
  instant its flag flipped, so there was nothing left to animate — the caller's `if (!visible) return`
  had already removed the subtree. `rememberOverlayAlpha` keeps it alive while the value falls and
  `isPresent` says when it has actually gone; the alert dialog, sheet, drawer, popover and context
  menu all render at that alpha now.

  It fades both ways for an overlay that toggles: `animateFloat` returns its target on the *first*
  pass only, so a component composed already-visible is opaque immediately while one that opens has
  a zero to rise from. Worth knowing when reading a capture — and the context-menu baseline fixture
  now advances frames until the fade settles, because a capture taken mid-fade is a timing
  measurement wearing an appearance test's name.

  `dismissOnScrimClick` became the presence or absence of a scrim click handler. A null one is the
  alert dialog and a real one is a dialog or sheet, which is the single behavioural difference
  between them and reads better as a handler than as `= false`.

- **`shadcnContextMenu`, on a new `PointerEventType.SecondaryPress`.** The engine had no notion of a
  right-click at all: `PointerEventType` had no secondary press and `FrameInput` no secondary field,
  though `core.input` had been carrying `secondaryPointerDown` the whole time without a way through.

  It is its own event type rather than a flag on `Press`. Every existing handler matches on `Press`,
  so a flag would have quietly made every button, slider and menu item right-clickable too — pinned
  by a test that right-clicks a `clickable` and asserts it does not fire.

  The menu anchors to the *point* rather than to a trigger's box, which the shared provider already
  handles: a zero-sized anchor at the pointer, since "below" a point is at the point. Measured
  opening at exactly the pressed coordinate.

- **The resizable divider draws its grip.** Upstream's `withHandle` is `h-4 w-3 rounded-xs border`
  around a `GripVertical` icon, inside a `w-px` handle — the overflow *is* the affordance, since a
  divider that grew to hold it would take twelve pixels from the panels whenever it was shown. That
  needs `requiredSize` rather than `size`: the divider's own constraints squeeze a child to one
  pixel, which is exactly what the first attempt rendered.

  The dots are drawn rather than taken from an icon set. lucide's `GripVertical` is six circles on a
  2x3 grid and at `size-2.5` each is under a pixel across, so a vector round-trip would cost fidelity
  and buy nothing. The grip's fill is `muted` rather than `border`, because `bg-border` on this theme
  is white at 10% — a 24/255 lift over a near-black panel, which rendered the grip present and
  invisible.

- **`shadcnPopover` is an overlay, and the anchoring behind it is shared.** The popover was content
  only — a styled surface whose own doc said anchoring and lifecycle belonged to the overlay layer.
  It now has the controlled, trigger-anchored overload the dropdown already had: a `Popup` layer, so
  it neither affects the trigger's layout nor is clipped by an ancestor, closing on an outside press,
  on Escape, or on the trigger again.

  The dropdown, select and combobox each carried their own anchor class, and two of them their own
  copy of the same flip-above-and-clamp provider; a popover would have been the fourth. `PopupAnchor`,
  `Modifier.popupAnchor` and `AnchoredBelowPositionProvider` are now shared, and all four use them.
  Select keeps its own provider because its rule is genuinely different — it item-aligns the selected
  row on the trigger rather than sitting below it — which is exactly the sort of thing that goes
  wrong when a rule is deduplicated on the strength of looking similar.

  No baseline moved but the new one, so the three migrations changed no pixels.

- **The OTP's focused slot follows its own shape.** Two deviations made the active slot read as a
  detached box floating above the row rather than a highlighted member of it. The focus ring took a
  single radius, so a first or last slot — rounded on its outer side, square where it meets its
  neighbour — got a fully rounded ring; `focusRing` now takes the field's `Shape` and grows every
  corner by half the stroke, which is what a negative `radiusInset` asks `createOutline` for. And the
  active slot switched to `BorderSides.All`, drawing edges its neighbours already own and breaking
  the seam the group is made of; upstream's `data-[active=true]` rules change the border's colour and
  add the ring, and leave the sides alone.

  The ring itself was never wrong. It measured one-sided until the fixture stopped clipping it at
  y=0 — 3px at the same value above and below once there was room to draw it.

- **`ShadcnButtonGroup` no longer collects its members inside `remember`.** The key was the content
  lambda, whose identity differs every pass, so the memoization never hit. It also removed a trap:
  the scope is declarative and its members are composed later, but nothing stops a caller composing
  *in* that lambda, and doing so while the collector ran as a `remember` calculation surfaced as an
  `IndexOutOfBoundsException` from the slot table rather than as anything diagnosable. Correct usage
  was never affected.

- **`shadcnToaster`, with a queue that ages on the frame clock.** `LayerKind.Toast` had been in the
  enum with no user; this is it. A toast outlives whatever raised it, so `ShadcnToastState` is held
  by the app rather than by a call site, and the toaster ages it by `LocalFrameClock.deltaSeconds`
  during its own build — which makes the dwell exact in tests instead of a sleep. Capped at three,
  oldest dropped, so a per-frame loop cannot grow a column taller than the window. Nothing is
  emitted while the queue is empty.

  It is the first overlay with no scrim, and that exposed something the others were hiding: a layer
  sizes to its content and, with no `positionProvider`, is placed at the **origin**. The alert
  dialog, sheet and drawer only appear where they should because their scrim makes the layer
  viewport-sized and the alignment then acts inside it. A bare toaster landed top-left and swallowed
  clicks meant for the page. It now says where it goes.

- **`shadcnSheet` and `shadcnDrawer`, over a shared modal layer.** With three consumers the scrim,
  the layer and the dismissal choice moved into an internal `shadcnModalLayer`; the alert dialog now
  goes through it and its tests and baseline were unchanged by the move, which is what made the
  extraction safe to do.

  `dismissOnScrimClick` is the working form of the intent `Layer`'s `dismissOnOutsideClick` cannot
  express once a scrim is present, and it is exactly the dialog-versus-alert-dialog distinction: a
  sheet and a drawer close on their backdrop, an alert dialog does not.

  A sheet fills the edge it is pinned to and sizes itself on the other axis. `w-3/4 sm:max-w-sm` is
  a *minimum of two rules* and no single modifier chain says it — `fillMaxWidth(0.75f)` fixes the
  width before `widthIn` can cap it, and hoisting the cap makes the fraction a fraction of the cap.
  It takes two boxes, and within the panel the cap has to sit outside the fill for the same reason.
  Both orderings were found by a test asserting the width at two viewport sizes rather than one.

  The drawer follows a downward drag and dismisses past 96dp. It does not spring back: `draggable`
  reports deltas with no drag-end callback and there is no exit animation, so a partial drag stays
  where it was released. Documented rather than faked.

  `ShadcnBaselineCoverageTest` now counts only *public* composables. Internal plumbing like the
  modal layer has no appearance of its own to baseline — it surfaces in the baselines of the three
  components that render through it — and demanding one would have taught people to pad the debt
  list. Verified the rule still fails for a genuinely uncovered public component.

- **`shadcnAlertDialog`, and Escape split from outside-press dismissal.** The first real consumer of
  `Layer(modal = true)` — the machinery shipped with the layer system and no component had ever
  passed it, so it had only run in its own unit tests. `ModalLayerTest` pins it now: a click cannot
  reach the page behind a modal, Tab cannot leave it, Escape reaches it.

  Building the first consumer immediately found an API gap. `activeDismissableLayer` required
  `dismissOnOutsideClick`, so one flag drove both Escape and backdrop presses — and upstream's alert
  dialog is precisely the component that wants Escape without the backdrop, because a destructive
  question must not be answerable by clicking away from it. `Layer` now takes `dismissOnEscape`
  separately, defaulting to `dismissOnOutsideClick` so every existing overlay is unchanged.

  A second finding, recorded rather than fixed: a **scrim makes `dismissOnOutsideClick` inert**. The
  scrim is a full-viewport child of the layer, so the layer's bounds are the viewport and the
  dispatcher's "outside the layer" test can never be true. A scrimmed overlay that *should* close on
  its backdrop — a plain dialog, a sheet — will have to make the scrim itself clickable. Found by a
  control that refused to fail when it should have.

- **Text reports its own intrinsic width, and the dropdown menu is as wide as its widest item.**
  A `MeasurePolicy` with no children inherits `max over children`, which for a leaf is zero — so
  every label claimed it wanted no width at all, and `width(IntrinsicSize.Max)` above one resolved
  to nothing. `TextMeasurePolicy` now answers with the unwrapped line for the maximum and the
  longest word for the minimum. The minimum is measured directly rather than by wrapping at zero: a
  greedy wrapper keeps at least one word per line and returns the text unbroken when nothing fits,
  so asking it for a zero-width layout answers with the maximum wearing the minimum's name.

  `widthIn`/`heightIn` had the same shape of bug one layer up — `SizeInNode` never overrode the
  intrinsic queries, so a query walked straight past it and reported the content's own size as if
  the bounds were not there. Exactly what `LayoutModifierNode`'s doc warns about. A dropdown asking
  for its widest item got the item and never the `min-w-[8rem]` floor sitting next to it.

  With both fixed the menu takes `width(IntrinsicSize.Max)` outside its `widthIn`, and the rows'
  `fillMaxWidth` finally means what it reads like: fill *this menu*, whose width the content
  decided. Three short labels came out as wide as the window before; they are 128dp now.

- **`BoxScope.matchParentSize()`.** A child sized *by* the box rather than one the box sizes itself
  to. `fillMaxSize` takes the incoming maximum, so a shrink-wrapping Box grows to it — which is why
  an enabled `ShadcnInputOtp` measured 300x60 while a disabled one measured 216x36: the focus
  overlay is only composed when enabled, so the control's own size, and its click target, depended
  on whether it was editable. CSS says `position: absolute; inset: 0`; Compose says
  `matchParentSize`; this had neither.

  `BoxMeasurePolicy` now measures in two passes — sizing children first, then the matched ones
  against exactly what those produced — with every child still measured once. Scoped to `BoxScope`
  for the reason `weight` is scoped to `RowScope`: outside a Box it would silently do nothing.
  Adding the receiver is source-compatible, so no call site changed.

  An earlier attempt sized the overlay from the measured slot row instead. Bounds came out right
  and all typing broke: input is hit-tested against the previous frame's placed tree, so an overlay
  gated on a measured size does not exist when the first click lands. That version was measured and
  discarded, not shipped.

- **`ShadcnInputOtp` matches upstream's slot styling.** Its geometry already did — measured 216x36
  for six slots, 1px borders at every 36px, `first:rounded-l-md last:rounded-r-md` — but every
  state on top of it had drifted. The active slot thickened its border to 2px instead of keeping
  1px and changing colour, and had none of `ring-[3px] ring-ring/50`; the caret was 1.5dp and
  static rather than `h-4 w-px` on `animate-caret-blink`'s 70% duty cycle; disabled repainted the
  slots `muted`, turning the control into a grey slab, where `has-disabled:opacity-50` dims it and
  leaves it otherwise identical; the group separator was a `-` glyph sitting on the text baseline
  rather than lucide's centred `MinusIcon` line.

  The slot fill exposed a Tailwind translation trap worth naming: `/N` is
  `color-mix(in oklab, <colour> N%, transparent)`, which *scales* the colour's existing alpha
  rather than replacing it. `--input` is already white at 15%, so `dark:bg-input/30` is white at
  4.5%. Reading it as "set alpha to 0.3" painted the slots at 83/255 over a 10/255 page — a grey
  box where upstream has a hint. `scaleAlpha` is the operation the modifier actually names.

  `shadow-xs` is still missing and deliberately so: there is no shadow modifier in the engine, and
  it resolves to black at 5% — invisible on this theme.

- **A text field no longer throws when text is deleted after `setText`.** `displayedText` spliced
  the IME composition range into the text unconditionally, but that range is only meaningful while
  a composition is live: `setText` parks it at the new cursor and a later delete shortens the text
  without moving it, so it points past the end. `setText("123456")` then one backspace threw
  `StringIndexOutOfBoundsException` out of the field's own draw, from any caller — the OTP length
  cap merely made it reachable, since that cap calls `setText`. Now gated on `hasComposition`;
  an active composition splices exactly as before.

- **`ShadcnInputOtp` enforces its length, and takes upstream's `pattern`.** `length` was applied
  only where the slots are drawn — `text.take(length)` — while the field kept everything typed. A
  seventh digit was still in the value, invisible, so the next two backspaces appeared to do
  nothing because they were deleting characters that had never been drawn. That is what "not
  working" was: typing itself was fine. The limit now applies to the value, in one place, and
  `pattern` filters what may be accepted at all. Like upstream's prop of the same name it defaults
  to accepting anything, since a one-time code is not always numeric; `ShadcnInputOtpPatterns`
  carries the alphabets shadcn's own examples pass.

- **The resizable divider answers the pointer from inside its grab margin.** `Modifier.hoverable`
  takes the same `hitMarginPx` as `draggable`, and the handle turns `ring` while the pointer is
  within 5dp of it. shadcn's own handle carries no hover style — its affordances are the resize
  cursor and the optional grip — but a one-pixel line needs one: the cursor is the only signal that
  the pointer is close enough to grab, and it does not say *which* divider answered. Without the
  margin on the hover link the highlight would only fire on the pixel the grab margin exists
  because nobody can hit.

- **A pointer link can accept hits beyond its own box, and the resizable divider uses it.** The
  divider is one physical pixel wide, which is what it should look like and is not something a
  pointer can land on — the reported "hover threshold is wrong". `PointerInputNode.hitMarginPx`
  grows what counts as hitting a link without growing the link: Compose's answer is to expand the
  layout box and centre the visual, but shadcn's handle keeps its `w-px` and overlays a wider strip,
  so the divider costs the panels one pixel instead of eleven. The handle takes 5dp, matching
  react-resizable-panels' `hitAreaMargins.fine`. Default is 0, so nothing else changes.

  Dragging itself turned out not to be broken. Slider, range slider and the resizable split all
  drag correctly through the real frame loop at 1x and 2x density — the new `ShadcnDragTest` pins
  all three, and the test session grew a `density` parameter so the 2x case is actually exercised
  rather than assumed. `ComposeTestSession` had `click` and `hover` and no drag at all, so no test
  had ever run a press-move-release across frames, which is the exact gap `Modifier.draggable`'s
  own comment warns about.

- **Components have pixel baselines, and the gate knows what it is not covering.** The repo had
  two committed pixel baselines, both backend smoke tests, against 42 shadcn components — so
  nothing failed when a component changed appearance, and answering "does this tessellation change
  look worse" meant writing a throwaway probe. `assertMatchesBaseline` rasterizes a
  `ComposeComponentFrame` and compares it to a committed PNG at **zero** tolerance: the software
  rasterizer is deterministic, so unlike a GPU capture there is no driver drift to absorb and no
  threshold to argue about. On failure it writes expected/actual/diff PNGs. Coverage is a ratchet
  rather than a registry — every component either has a baseline or is named in
  `uncovered-components.txt`, landing a baseline requires deleting its line, and a baseline whose
  name matches no component fails as an orphan. Five components on the open defect list are pinned
  at their current, in two cases wrong, appearance, so the fix for each produces a reviewable diff.
  Not a new command: it is a test the existing suite runs and the pre-push hook already covers.

- **UI mesh buffers grow to fit instead of capping.** `MAX_UI_QUADS` was 256, so a run carried
  1,024 vertices — while a 16px outline icon tessellates to roughly 1,500 and a rounded border to
  2,400. Every one of them overflowed and was split and re-indexed, every frame: 3.2 ms of a
  Studio frame against 0.2 ms once they fit, for the same vertex volume. A single shape is no
  longer split — it becomes its own run, and `DynamicMesh` reallocates that run's buffers to fit
  and keeps the larger size, on both backends. `MAX_UI_QUADS` is now how many *small* primitives
  to batch and the size buffers start at, not a ceiling, so it stays at 256 and memory tracks the
  content actually drawn rather than a guessed worst case — which matters most on the web target,
  where a flat ceiling lands in a browser tab. Capacity converges: `UiUploadCostBenchmark` asserts
  that redrawing an unchanged frame reallocates nothing.
- **UI buffer uploads no longer round-trip through a heap allocation.**
  `writeBufferMemoryFloats`/`Bytes` extracted each JVM array into a `std::vector` and then copied
  that into mapped memory — an allocation and a second full copy on every call, and Studio makes
  144 of them a frame carrying about 2 MB. They now write the array straight into the mapped
  pointer with `GetFloatArrayRegion`/`GetByteArrayRegion`. A Studio-shaped frame's `drawUi` falls
  from 8.5 ms to 1.2 ms on a real device, measured by the new `UiUploadCostBenchmark`. Every
  buffer write in the backend goes through these two functions, so the whole Vulkan pixel-baseline
  suite is the correctness gate.
- **The shadow depth pre-pass no longer drains the GPU mid-frame.** It submitted its own one-time
  command buffer and blocked on a fence until the GPU finished, so the CPU could not record the
  scene pass while the GPU drew the shadow map — a full CPU↔GPU round-trip every frame to express
  an ordering the GPU can honour by itself. The dependency is a depth write followed by a
  fragment-shader sample on one queue, so `DepthTarget`'s render pass now declares it as an
  outgoing subpass dependency and the pre-pass records into the same command buffer as the pass
  that samples it. Studio drops from 3 synchronous round-trips per frame to 1 (5 to 2 with a
  camera entity selected, since `renderToTexture` ran its own pre-pass too). Verified against the
  real-device shadow-map pixel test.
- **The frame overlay reports the GPU wait as its own phase.** `uiStageMs` wrapped
  `Renderer.drawUi`, whose first act is `vkWaitForFences` on this frame's in-flight slot — so with
  two frames in flight it also measured how far behind the GPU was, and a GPU-bound frame read as
  an expensive UI: 40.7 ms of a 43.9 ms Studio frame, against a UI costing about one. `Renderer`
  gains `awaitFrameResources()` (a no-op by default, the fence wait on Vulkan) and the scene
  runtime times it as `ScenePhaseStats.uiWaitMs`, before staging. Studio's status bar now reads
  `ui … wait … stage … sim+render …`. No behaviour change: the wait already happened, in the same
  place, on the same fence.

- **Present mode is configurable, and the mode actually selected is reported.** `WindowConfig`
  gains `presentMode` (`Auto`, `Vsync`, `LowLatency`, `NoVsync`) with a matching `presentMode` in
  the window DSL; the Vulkan swapchain resolves it against what the surface offers, falls back to
  FIFO — the only mode every implementation must support — and exposes `selectedPresentMode`,
  which the engine prints at swapchain creation. It was previously hard-coded to prefer mailbox
  and silently accept FIFO, so an app could be capped at the refresh rate with no way to ask
  otherwise and no way to tell. That matters for profiling: a 17.4 ms frame on a 60 Hz panel can
  be 10 ms of work and 7 ms of waiting, and only `NoVsync` makes a frame-time number mean how long
  the frame took.

### Fixed

- **A `shadcnSidebar` panel taller than its frame lost everything past the bottom edge.** The
  content slot clipped and stopped there; upstream's `SidebarContent` is `overflow-auto`, and only
  half of that had been ported. A scene outliner and a component inspector are the two panels in an
  editor guaranteed to outgrow their height, and the four-entity fixture the editor develops
  against is exactly the scene that never shows it.
- **Two viewport controls shared a glyph, in a strip of ten unlabelled ones.** Sky and the
  orientation gizmo were both `globeAlt`; Wireframe and orthographic projection were both
  `squares2x2`. Resolving it inside HeroIcons' 33-glyph outline set moved three: Wireframe to
  `square3Stack3d`, shadow frustum to `moon`, the gizmo to `arrowsPointingOut`.
- **The Studio status bar printed four phase figures that were never measured.**
  `SceneAppLifecycleRuntime.phaseStats()` reports all-zero rather than null while F2 collection is
  off, so "not measured" and "measured, and free" were the same value; every run showed
  `ui 0.0ms wait 0.0ms stage 0.0ms sim+render 0.0ms`. `ScenePhaseStats.isMeasured` makes the
  distinction available. The bar is also segmented into fixed-width figures now — it was one
  dash-joined string of live counters in a proportional font, so a digit changing width moved every
  figure to its right.
- **The viewport tool palette moved when the console dock resized.** It was centred on the
  viewport's left edge, and the viewport is a panel a dock resizes, so dragging the dock taller
  slid Select/Move/Rotate/Scale up the screen while the user was aiming at them. It is anchored to
  the top-left corner now; the camera pair stays centred because both corners on its side belong to
  the orientation gizmo and the camera preview.
- **Arcs were flattened to a fixed minimum instead of what their sagitta needed.**
  `adaptiveArcSteps` computed a step count from the flatness tolerance and then clamped it up to
  `MIN_ADAPTIVE_STEPS`, so any sweep needing fewer than four segments got four. A stroke's round
  join runs it once per centreline vertex, and between two segments of an already-flattened curve
  the sweep is a fraction of a degree — one chord is exact to 0.001px there. Only the lower clamp
  moved; a 30° arc still takes 5 segments and a 90° corner still takes 15. A Studio frame drops
  from 109,946 mesh vertices to 70,248 and its coalescer CPU from 7.6 to 1.3 ms.
- **Anti-aliasing was silently disabled on every curve.** `offsetPolygon` clamped each vertex's
  offset to half its shorter adjacent edge — a self-intersection guard, but one that made the
  fringe width a function of how finely a contour happened to be flattened rather than the width
  asked for. A rounded outline flattens into sub-pixel arc segments, so a requested 0.5px fringe
  came out at 0.003px and every curved fill and stroke shipped effectively unantialiased. Growing
  a convex corner outward cannot fold a ring back on itself however short its edges are, so the
  clamp now applies only where direction and convexity disagree. Borders and rounded shapes gain
  the soft edge they were always meant to have; their anti-aliased rim extends up to one pixel
  past the node, as an anti-aliased edge does.
- **Borders no longer re-tessellate every frame.** `BorderNode` builds its ring once and keeps it
  on the node, rebuilding only when size, colour, shape, sides or density change — the same reason
  Compose's `BorderModifierNode` caches through `CacheDrawModifierNode`. With the icon fix, Studio's
  coalescer CPU falls 20.2 → 7.3 ms per frame. `Modifier.border` now emits `UiDrawPrimitive.Mesh`
  rather than `StrokedPath`.
- **Icons no longer re-tessellate every frame.** Drawing an `ImageVector` through `drawPath` handed
  the coalescer a shape to tessellate 60 times a second, and a 16px outline icon flattens to ~2,400
  vertices: Studio spent 20.2 ms of a 43.9 ms frame on 38 of them, against 3.6 microseconds for the
  other 194 primitives in the same frame, all 160 glyphs included. `rememberVectorPainter`
  tessellates once and redraws the triangles through the new `UiDrawPrimitive.Mesh` and
  `DrawScope.drawMesh`. Coalescer CPU 20.2 → 18.2 ms; borders are the remaining half and still
  tessellate per frame.
- **Typing reaches the glyphs through layout, not composition.** `BasicTextField` captured its text
  at composition, so a keystroke changed `TextFieldState` correctly while nothing re-declared the
  node and the painted glyphs stayed stale until an unrelated invalidation. It now reads the text
  during measure and draw, which is the phase discipline Compose's own `BasicTextField` follows.

- **WebGPU renders shadows.** `lit_shadow` was Vulkan-only: WebGPU narrowed the depth pre-pass
  away, so its synced copy was inert. Three things were missing and now aren't — the shader
  declares its map as `texture_depth_2d` (WebGPU's auto layout rejects a `Depth32Float` view
  against the `texture_2d<f32>` spelling; Vulkan accepts both, so it stays one shared source),
  `primaryDraw` writes the full `LitShadowUniformLayout` block and the slot pool is sized for
  it, and the depth target rides to the draw as a group-1 bind group. Proven on a real device
  by `WebGpuShadowBindingTest` (WebGPU validates bind groups against the shader's own declared
  layout, so this could not be argued from reading code) and by the Vulkan shadow-map GPU test
  still passing on the re-recorded shader.

- **`:awake:compose` — `Modifier.offset` made a node unclickable everywhere.** Offset reports its
  child's size and then places it somewhere else, so the node's own box stayed where its parent put
  it while every draw and every pointer link inside it moved. The two boxes never overlapped and
  the hit-test gate tested only the first, so an offset node could not be clicked at the position
  it was drawn at *or* the one it was placed at. The gate is the union of both boxes now; each
  pointer link is still filtered against its own, so nothing gains a hit it should not have.

- **`:awake:compose` — a child that exactly fills its clickable parent no longer swallows the
  click.** Pointer capture followed depth (`path.last()`), which is the consuming node only while
  nothing fills the parent; a `Modifier.weight(1f)` child covers its Row pixel for pixel, took a
  capture it had no handler for, and the Row's release then reported `isCaptureHolder = false` --
  the control highlighted on press and never fired. Capture now follows consumption. An inner
  button inside a clickable card still wins, because it is still the consumer.

### Fixed

- **Every documented module's Dokka publication was broken** — the dokka convention fed plain
  `# Title` READMEs into Dokka's includes, whose format demands a `# Module <name>` first
  heading; `dokkaGeneratePublicationHtml` (and so `publishToMavenLocal`) failed repo-wide
  with "Unexpected classifier". The convention now wraps each README into a generated
  module doc (header prepended, headings demoted), and `moduleName` finally names each
  module instead of "Awake Engine" everywhere. The Vulkan bindings opt out of the
  undocumented-API warning gate: the Khronos spec is the documentation for mirrored `Vk*`
  names.

- **vulkan-kmp: publish chain proven end to end.** The bindings publish as
  `io.github.awake-lab:vulkan-kmp` (Android's NDK half as `vulkan-kmp-android-native`), and a
  standalone consumer project (`tools/vulkan-kmp-smoke`) resolves the published artifact from
  mavenLocal and runs a live `vkCreateInstance` through it — including the MoltenVK
  portability opt-in every external macOS consumer needs. CI's lavapipe job now runs the
  bindings' own conformance suite; the docs site gains a Vulkan KMP page.

### Changed

- **BREAKING (`:awake:compose:foundation`): `Style` applies through a receiver, not a parameter.**
  `fun applyStyle(scope: StyleScope)` became `fun StyleScope.applyStyle()`, so an authored style
  reads as the CSS it translates. Migration: drop the lambda parameter and the `scope.`/`it.`
  prefixes — `Style { scope -> scope.background(c); scope.hovered { it.alpha(0.5f) } }` becomes
  `Style { background(c); hovered { alpha(0.5f) } }`.
- `:awake:compose:runtime` gained `remember(key1, key2) { }`. The keys are stored side by side in
  the slot rather than combined, so it allocates nothing per pass.

- **BREAKING: `VulkanEngine`/`WebGpuEngine` take `contentFeatures` instead of `skyboxShaderSet`.**
  Both engines built `SkyboxRenderPipeline`/`SkyboxRenderFeature` themselves, so each knew what a
  skybox *is* and a fourth content feature meant editing both engines and both bootstraps. They now
  take an ordered, app-supplied `List<VulkanContentFeature>` / `List<WebGpuContentFeature>` recorded
  before opaque geometry, and see only `RenderFeature`. Migration: replace
  `skyboxShaderSet = MyShaders` with `contentFeatures = listOf(skyboxContentFeature(MyShaders))`.
- WebGPU's `Renderer` no longer takes `skyboxRenderPipeline` — no code read it.

### Known issues

- **`UiContext.measuring` is dead** — declared, read by click-suppression guards, never
  set true. A wrap-content trigger's content lambda runs unsuppressed during trial
  passes as a result. Not yet reproduced as an actual swallowed click (see
  `ShadcnPopoverCheckboxClickProbeTest`); full investigation in commit `7c3f54ba`.
  **`ui-core` only.** `ShadcnPopoverSiblingCheckboxTest` proves three sibling checkboxes in a
  popover each take their own click on the compose engine, which has no trial passes and no
  polled return to read a build late. Studio's viewport controls were reverted from a popover
  to an always-visible strip over this; that reason no longer applies.

- **A wrap-height `shadcnToggleGroup` creates a 100,000px hit rectangle.** Its internal
  `fillMaxHeight()` resolves against the trial-measurement sentinel, so a viewport header pushes
  the rail offscreen and then intercepts the popup's clamped-on-screen menu clicks. It is not an
  active/focus claim failure or the nesting defect fixed in `d3f270c7`. **Blocks the viewport
  header**, and matters anywhere a wrap-height toggle group can overlap a popup. Diagnosis and
  the required regression shape are in `docs/tasks/2026-08-11-studio-layout-design.md`.

- **Glyph stem weight varies with sub-pixel phase.** The same character repeated on one line
  renders 1px and 2px stems in alternation (`'i' @14px: [1,1,1,2,1,2,1,2,...]`), which reads as
  "some characters thin, some not". MTSDF was expected to close this and did not: the field
  resolves an edge analytically, but each quad still lands at a different sub-pixel phase as
  fractional advances accumulate. Recorded as `knownStemWidthSpread = 1` in
  `GlyphStemWeightTest` -- which is currently `@Ignore`d, because its probe cannot isolate
  individual stems (a repeated 'i' at 12px collapses into one run at every threshold tried).
  Disabled deliberately rather than left green and lying. **Top open issue.**
- **Studio shows no custom cursor.** `SceneGameRuntime` has the cursor in its frame effects
  and discards it, unlike `GameUiRuntime`, and no service registration exposes the runtime to
  an entry point. `runVulkanDesktopGame`'s `cursor` defaults to null, so every request is
  dropped.
- **Studio does not use the engine's camera system.** `awake:scene:controls` provides
  `CameraMode` (FirstPerson, ThirdPerson, Cinematic, TopDown) with `CameraInputSystem`
  handling drag and scroll per mode, and `scene3d-playground` uses it. Studio defines a
  parallel `CameraPresetMode` (Orbit, Front, Top) with hand-rolled preset math and drag that
  only responds in Orbit, so Cinematic and TopDown are unreachable from studio and the
  viewport is not draggable in the other modes.
- **The resizable handle's geometry does not match upstream shadcn.** Real shadcn is a `w-px`
  in-flow Separator whose grab area is an absolutely positioned pseudo-element, plus
  react-resizable-panels' `hitAreaMargins`. Splitting ours into a 1dp layout cost and a
  separate grab margin was tried and reverted: it re-proportioned every panel, and because it
  required hit-testing outside `interact()` it killed the hover state the resize cursor reads.
  Closing this needs `interact()` to accept a hit rect distinct from its layout rect.
- **`docs/reference/ui-status.md` is stale** — it predates the MTSDF and resizable work.
- **Studio viewport canvas padding is wrong.** Reported, not yet diagnosed.
- **Studio's tool rail is inert.** All five buttons (Layers, Grid, Environment, History, Panels)
  dispatch `SelectTool` and update the store, and `activeTool` is then read only by `IconRail`
  itself to decide which button looks pressed. Nothing else reads it. Its PLACEMENT is fine --
  Blender and Unity both float tools at the viewport edge -- the wiring is what is missing. See
  `docs/tasks/2026-08-11-studio-layout-audit.md`.
- **Studio entity selection is inert.** `InspectorState.selectedEntityId` is written by the store
  and read by nothing; `InspectorPanel` lists every named entity regardless. There is no UI to
  select from, which is what the hierarchy dock is for.
- **`StudioShellLayoutTest.panelsDockFlushToEveryFrameEdge` fails.** Pre-existing, confirmed
  against clean `main` with all local changes stashed. Never diagnosed.

## [0.1.0-dev.6] - 2026-08-23

Rendering-architecture pass, plus the ECS and UI work that accumulated since dev.4 (this branch
never carried the dev.5 tag, which was cut on `vulkan`).

The theme is removing decisions that were being made twice. Pipeline construction moved behind a
shared description with a per-backend factory; `GpuDevice` gave the Vulkan/WebGPU boundary a name
and a rule; and the WebGPU backend became executable on the desktop JVM, so it is testable
outside a browser for the first time. Alongside that: ECS tag storage and a reflection-free
component-mutation path, UI frame-allocation cuts, and continued shadcn parity work.

### Added

- The WebGPU backend now runs on the desktop JVM as a **test target**, and creates a real GPU
  device there. WebGPU code was previously compile-checked and never executed outside a browser.
  Production is unaffected — desktop and Android still ship Vulkan. Note this exercises Awake's
  WebGPU code path over wgpu-native, not a browser's WebGPU implementation, so canvas sizing, JS
  interop and browser driver behaviour still need a browser check.
- `GpuDevice`, the named render-hardware-interface boundary: what a GPU backend can do, with no
  knowledge of what a scene is. `Renderer` extends it. No behavioural change — see
  `docs/reference/render-hardware-interface.md`.
- WebGPU now builds the alpha-blended transparent pipeline companion Vulkan already had, so
  a `DrawCall.transparent` draw blends on both backends instead of silently rendering opaque
  on WebGPU. A format with no transparent companion still falls back to its opaque pipeline.
- A reflection-free cached-`ComponentTypeId` mutation path for ECS add/remove operations.
  Matched JDK 17 measurements improved component churn by 16.9% at 10k and 22.1% at
  100k, while 100k maintained-family churn allocation fell by roughly 90%.
- A benchmark-only pure-archetype ECS control that performs real row migration and a
  256-signature fragmentation suite. Matched JDK 17 measurements keep production Awake on
  sparse sets plus maintained families: stable/query iteration stayed faster and dynamic-tag
  churn avoided archetype migration's roughly 7.6x throughput penalty at 100k entities.
- `EcsTag`, structured storage diagnostics, and payload-free singleton storage across both
  component stores and maintained `Family1`/`Family2` caches. Tag family iteration keeps one
  singleton reference; the existing `components*()` APIs lazily materialize compatibility arrays.
- `shadcnTabs`' `items`/`selected` overload takes a `content: ColumnScope.(String) -> Unit`
  slot, rendering a real content panel below the track instead of modeling the track only.
  Defaults to empty, so existing track-only callers are unaffected.
- `ShadcnSurfaceVariant.Band` — a full-bleed chrome strip (muted, square-cornered,
  horizontal-only inset), for an app toolbar/status-bar row rather than a panel/card.
  `shadcnSurface` also takes an optional `contentPadding: Dp?` override.
- `popup()`/`dialog()` (`ui-headless`) and all 9 designsystem overlay wrappers built on them
  (`shadcnDropdownMenu`, `shadcnTooltip`, `shadcnAlertDialog`, `shadcnContextMenu`,
  `shadcnSheet`, `shadcnDrawer`, `shadcnDialog`, …) take a `modifier: Modifier` param.
  `widthIn(max=)`/`heightIn(max=)` now actually clamp the popup's resolved size — the
  underlying primitive already supported it, the facades just never forwarded it.
- `samples:ui-showcase`/`samples:studio` are now covered by `verifyUiOwnership`, banning
  direct `ui-core` (`ui.modifier.*`) imports and hand-authored `Style { }` blocks in consumer
  code — the same rule that already applied to `ui-shadcn`.
- GPU-based 3D Camera System: a single `CameraComponent` carrying a `CameraMode` enum
  (`FirstPerson`, `ThirdPerson`, `Cinematic`, `TopDown`) plus an `ActiveCamera` tag
  component. One `CameraSystem` drives every mode and only processes entities tagged with
  `ActiveCamera`, preventing input drift in inactive modes.
- `CameraInputSystem`: Handles hotkey switching (F1-F4) between camera modes.
- `MatrixRelativeMovementSystem`: Dynamically transforms movement intents from
  `MovementControl` based on the active camera's orientation, ensuring view-relative WASD.
- `PlayerInputSystem`: Simplified hardware-to-ECS intent mapping for kinematic movement.
- `Vec3` math helpers: `set`, `lerp`, and `add` for easier mutation.
- `awake-ecs` and `awake-scene` as publishable artifacts. `awake-ecs` is now the pure
  sparse-set ECS runtime; Awake-specific scene components and systems moved to
  `awake-scene`.
- Maintained ECS family handles for one- and two-component queries, including
  component-only iteration for systems that do not need entity handles.
- GPU skinning: `Quat` (`awake-base` `core.math`), `GltfSkinning.kt` (skin/animation data
  types) and `SkinnedAnimationPlayer` (a generic glTF animation sampler) in `awake-base`
  `core.mesh.gltf`, `VertexFormat.PositionNormalColorSkin`, and
  `Renderer.drawSkinnedMesh(mesh, material, model, jointPalette)` — a separate staged-draw
  path from `draw()`/`DrawCall` because a skinned mesh's vertex layout doesn't match the
  one shared main 3D pipeline. `Renderer.createMaterial(...)` gained a `uniformFloatCount`
  parameter so a skinned material's joint-palette uniform buffer can be sized correctly.
- Texture sampling: `Bitmap.toRgba8Bytes()` (`awake-base` `core.graphics.BitmapRgba8.kt`),
  materials/textures/images parsing on `GltfDocument`/`GltfParser`/`GltfMesh`
  (`GltfMesh.baseColorImageBytes`, `toInterleavedPositionNormalColorUv()`),
  `VertexFormat.PositionNormalColorUv`, and `Renderer.drawTexturedMesh(mesh, material,
  model)` — same separate staged-draw pattern as GPU skinning.
- `:awake:scene:scene-core` gained `SpinControl`/`SpinSystem` (generic entity rotation) and
  `:awake:scene:controls` gained `LookAtControl`/`LookAtCameraSystem` (rotation-only
  tracking) plus `PrimaryOrbitCamera` (a plain lifecycle helper, not an ECS `System`, for
  a UI-driven debug camera entity) — extracted from boilerplate duplicated across
  `samples:scene3d-playground` demos.
- `Mesh.format` and `DrawCall.extraUniformFloats` — a `MeshRenderer` entity's mesh now
  determines which GPU pipeline draws it (`Renderer.pipelinesByFormat` on the Vulkan
  backend), instead of `RenderSystem`/`draw()` only ever supporting one fixed vertex
  format. `:awake:scene:rendering` gained `SkinnedPose`, an optional `MeshRenderer`
  add-on component carrying a GPU-skinned mesh's joint palette. `OrbitCameraDemoRig`
  gained a public `entity` accessor so a demo can attach these to its placement entity.

- Real-time shadow mapping for the directional scene light: a depth pre-pass renders
  shadow casters from the light's point of view into a `ShadowMap`, and the lit pipeline
  compares against it with manual PCF (this repo's `VkSamplerCreateInfo` binding has no
  `compareEnable`/`compareOp`, so hardware Dref sampling isn't available). Runtime-gated by
  `Renderer.shadowsEnabled`.
- `Renderer.wireframe` mesh view mode. Vulkan uses a `VK_POLYGON_MODE_LINE` companion
  pipeline sharing the fill pipeline's loaded shaders; WebGPU has no polygon-mode
  equivalent, so it derives a line-index buffer from the triangle indices and draws
  `LineList` topology instead.
- Web (wasmJs) bitmap decoding, via `createImageBitmap()` plus an offscreen-canvas
  readback. `Bitmap` creation became `suspend` across all four platform actuals as a
  result — decoding in the browser is inherently asynchronous.
- A build gate (`verifyShaderBinaries`) that fails `check` when a GLSL source drifts from
  its checked-in `.spv`. Vulkan loads the compiled binaries at runtime, never the adjacent
  sources, so a stale binary silently ignores shader edits — this was found the hard way
  after 7 of 10 checked-in binaries turned out to already be stale.

### Removed

- 8 dead-public designsystem style functions narrowed to `internal`: `shadcnAvatarStyle`,
  `shadcnAvatarBadgeStyle`, `shadcnToggleGroupItemStyle`, `shadcnRadioStyle`,
  `shadcnProgressStyle`, `shadcnSkeletonStyle`, `shadcnSpinnerStyle`, `shadcnToastStyle`. Zero
  real callers existed outside their own module.
- Legacy camera systems and controllers: `OrbitCameraController`, `OrbitCameraSystem`,
  `FollowCameraSystem`, `LookAtCameraSystem`, and `FreeFlyCameraSystem`.
- `PlayerControlSystem` and legacy control components (`OrbitControl`, `FreeFlyControl`,
  `FollowControl`, `LookAtControl`).
- `Renderer.drawSkinnedMesh`/`drawTexturedMesh` — the separate staged-draw bypass a skinned
  or textured mesh needed before `RenderSystem`/`draw()` supported more than one vertex
  format. `GltfViewerDemo`/`SkinnedMeshDemo` (`samples:scene3d-playground`) now spawn a real
  `MeshRenderer` entity (plus `SkinnedPose` for the skinned case) instead.
- `DemoApplication`'s per-second `Random.nextInt` drawable switching on desktop. Leftover
  debug code that silently overrode `DemoDrawer`'s real click-to-select mechanism — selecting
  an item in the drawer never stuck for more than a second. Not a feature; just noise.
- `VulkanApplication.glfwWindowHandle` field. Replaced by a real `expect fun createSurface`/
  `destroySurfaceWindow` pair in `awake-vulkan` (see `awake-vulkan/src/*/kotlin/io/github/
  ronjunevaldoz/awake/vulkan/VulkanSurface.kt`) — the field existed only to let `destroy()`
  know whether to call GLFW teardown; now `destroySurfaceWindow()` is called unconditionally
  and is simply a no-op on Android, so there's nothing left to track.
- `buildSrc`'s hand-rolled `signing-publication-conventions.gradle.kts` (`maven-publish` +
  `signing` applied and configured by hand, including a manual sign-task-dependency
  workaround). Replaced by the vanniktech `maven-publish` plugin applied directly in
  `awake-core/build.gradle.kts` — see "Changed" below for why it isn't a shared buildSrc
  convention plugin anymore.

### Changed

- Pipeline construction is described once and built per backend. `PipelineSpec`, `PipelineKey`,
  `PipelineRequest`, `PipelineSet`, `PipelineVariant` and `buildPipelineTable` moved into
  `render:contract`; each backend now implements a one-method `PipelineFactory` that only
  translates an already-decided description into its own API. Previously each backend decided
  independently which pipelines exist, which is how WebGPU came to lack a transparent pipeline
  that Vulkan had.
- UI frame allocation cut 56% (1,151,016 → 507,357 bytes on a 20-row/60-surface scene), and it
  scales linearly with page size again rather than superlinearly. Five fixes, all aimed at work
  that trial measurement passes repeat and then discard: trial contexts read weight answers
  through to the real context instead of copying them; ambient locals copy store-to-store instead
  of through a snapshot object; a trial no longer tessellates clip shapes whose primitives it
  will drop; `Style.resolve` walks its rules by index instead of building two filtered lists; and
  `Modifier` is one shared empty instance with identity-guarded `width`/`height`. `ui-core`'s new
  `UiFrameAllocationProbe` measures this and holds a ceiling that ratchets down as further
  allocation fixes land.
- Kotlin upgraded to 2.4.10, the latest stable bug-fix release. ECS family internals are split
  into focused membership/cache/value-column files with documented tag and payload strategies.
- `awake-ecs` `System` is now scheduler-free: the stale `frequency` property and
  `SystemFrequency` enum were removed from the public ECS core API. Scene scheduling now
  belongs to `awake-scene`/`awake-scene-dsl` registration via `SceneSystemPhase` plus
  explicit fixed-step and per-frame system helpers.
- `awake-scene` now keeps only the small `NavMesh` contract; the demo navmesh bootstrap,
  hardcoded demo geometry, recast4j dependencies, and proof tests moved to
  `samples:scene3d-playground`. `SceneRuntime` is deprecated in favor of
  `SceneGameRuntime`/`sceneGame {}`.
- Authored gameplay systems `ChaseAiSystem` and `PlayerMovementSystem` moved out of
  published `awake-scene` and into `samples:scene3d-playground`. Engine-owned systems should
  be reusable behavior; sample/game-specific systems belong with the authored gameplay that
  owns their rules.
- Scene internals started splitting behind the published `awake-scene` facade:
  `:awake:scene:scene-core` now owns `Transform`/`Name`, and `:awake:scene:rendering` owns
  `Camera`/`Light`/`MeshRenderer` plus `RenderSystem`. `:awake:scene:physics` now owns
  `PhysicsBody`/`PhysicsSystem`, `:awake:scene:controls` now owns
  `OrbitControl`/`FreeFlyControl`/`FollowControl`/`MovementControl` plus their camera
  systems, and `:awake:scene:runtime` now owns `SceneGameRuntime`, `SceneGameSpec`, the
  scene document model, and `SceneAssetLibrary`. `TransformSystem` moved to
  `:awake:scene:scene-core`; `PlayerControlSystem` moved into `:awake:scene-dsl` (it needs
  `ui-core`). `:awake:scene-dsl` now depends on the specific scene leaf modules it uses
  instead of the whole `:awake:scene` facade. Public package names stay stable.
- **Maven Central publishing migrated off Sonatype's legacy OSSRH staging API**
  (`s01.oss.sonatype.org`), which Sonatype sunset in June 2025 — publishing would have failed
  outright had it been run. Now uses the vanniktech `maven-publish` plugin targeting the
  current Central Portal (`./gradlew :awake-core:publishToMavenCentral -PisMainHost=true`).
  Credential property names changed accordingly: `ossrhUsername`/`ossrhPassword` →
  `mavenCentralUsername`/`mavenCentralPassword` (now a Central Portal user token, not a
  Sonatype JIRA account), `signing.keyId`/`signing.secretKey`/`signing.password` →
  `signingInMemoryKeyId`/`signingInMemoryKey`/`signingInMemoryKeyPassword`. CI's
  `.github/workflows/build-and-publish.yml` JDK bumped 11 → 17 to match `jvmToolchain(17)`.
- `awake-demo/desktopApp/src/jvmMain/kotlin/main.kt`'s `fun main()` now launches the real
  Compose UI (`application { Window { MainView() } }`) again — it had been commented out in
  favor of a raw OpenGL/AWT `createFrame` loop, which meant `:awake-demo:desktopApp:run`
  never actually showed the Compose demo (buttons, Enable-Vulkan switch, FPS text) on any
  renderer. That OpenGL loop is preserved as `runOpenGlFrameDemo()` — a plain function, not
  currently wired to a Gradle task — since it's still a legitimate manual smoke test, just no
  longer what `run` should launch by default.
- `VulkanApplication.createSurface()` no longer branches on `window is Long` to distinguish
  Android's `Surface` from desktop's GLFW window handle — it delegates to the new
  `expect fun createSurface` in `awake-vulkan` instead. Behavior is unchanged; this is a
  structural cleanup (see Phase 1c in `docs/mvp-plan.md` for the full rationale).
- `awake-ecs` component stores now use primitive sparse/dense arrays instead of
  `MutableList` storage, reducing structural add/remove overhead in the benchmark harness.
- Glyph coverage-alpha now gets stem darkening (`pow(alpha, 1/1.45)`) on both backends.
  `SwapchainManager` deliberately picks a `_UNORM` (not `_SRGB`) format because authored
  colors are already sRGB-encoded bytes, which means all alpha blending happens on
  gamma-encoded values and text rendered visibly too thin. Darkening the stems is what
  FreeType/Skia do when linear blending isn't available.
- Icon centering offsets snap to whole pixels, matching what text already did via
  `resolveGlyphPx`. An odd width difference otherwise landed a whole glyph on a
  half-pixel, blurring every edge.
- Shadow lookups use a slope-scaled bias (`max(0.0090 * (1 - dot(N,L)), 0.0015)`) instead
  of one constant. A single constant can't serve both face-on and grazing surfaces: large
  enough to stop grazing-angle acne means detaching face-on contact shadows.
- `VulkanGameApplication` takes one `additionalPipelines: Map<VertexFormat, GameShaderSet>`
  instead of paired `skinnedShaderSet`/`skinnedVertexFormat`/`texturedShaderSet`/
  `texturedVertexFormat` parameters, so adding a fourth vertex format no longer means
  adding a fifth and sixth constructor parameter.
- `VulkanGameApplication` no longer builds a whole `Material` purely to borrow its
  descriptor-set layout — a half-constructed instance that `createResources` had to never
  be called on, and that teardown had to partially destroy. `Material.createDescriptorSetLayout`
  is now a companion function and only the layout handle is held.
- Dark-theme `card` and `sidebar` colors corrected to oklch lightness 0.205, matching the
  published shadcn spec (they were 0.168 and 0.158).
- `UiContext.column`/`row` (member) and `UiPrimitiveScope.column`/`row` (extension) — the
  raw-slot forms that take an already-resolved `Rectangle` — renamed to `columnAt`/`rowAt`.
  They shared a name with the unrelated "smart" `column`/`row` family that resolves its own
  slot from id/style/arrangement, an overload-resolution trap. Root-authoring
  `UiContext.column`/`row` (resolves its own slot from a `Modifier`) is unaffected.
- `ui-headless`'s own `Modifier` type (`HeadlessModifier`, plus ~14 duplicate builder
  functions) is gone. `Modifier` and its builders (`width`, `padding`, `clickable`, etc.) are
  now `ui-core`'s real `UiModifier`, re-exported through `ui-headless` for `ui-shadcn`'s
  benefit — behaviorally identical, but any code importing
  `io.github.ronjunevaldoz.awake.ui.headless.Modifier` directly by fully-qualified name (rare)
  should re-check that the symbol still resolves; the star/named import form is unaffected.
- `ShapePainter.kt`'s widget-chrome helpers move from `UiPrimitiveScope` extensions to
  `CanvasScope` extensions and are renamed to match: `emitFillAndBorder` → `drawFillAndBorder`,
  `emitCheckmark` → `drawCheckmark`, `emitRadioDot` → `drawRadioDot`, `emitInsetDash` →
  `drawInsetDash`. They now take resolved `Color` params instead of reading theme defaults
  internally — `CanvasScope` never touches theme, matching how Compose's `DrawScope` works.
  `CanvasScope.context` is fully removed, no longer just relocated.
- `UiPrimitiveScope.emit`/`emitOverlay` are gone — `CanvasScope` (via `canvas { }`) is now the
  only way to submit a draw primitive. Code drawing straight from `UiPrimitiveScope` needs to
  move behind `canvas { }`, same shape as `ShapePainter.kt`'s widget-chrome helpers above.
  `UiPrimitiveScope.context` is unaffected — still there, real usage outside `ui-core`'s draw
  path is much larger and out of scope for this pass.
- `canvas()`'s `id` param is now required (its default was dead — zero real callers used it).
  `separator()`'s `id` is now required on both overloads, including the deprecated `color:`
  bridge — closes a real same-id collision risk the old nullable-last default carried.
- `Modifier.margin()` is deleted. It silently dropped `end`/`bottom` and had zero real callers;
  use `Modifier.padding()`/explicit `width`/`height`/`offset` instead.
- `shadcnIcon`, `shadcnRadioGroup`'s content-slot overload, and `shadcnSidebarGroup`/
  `shadcnSidebarMenu`/`shadcnSidebarMenuSub` now return `Rectangle` instead of `Unit`, so they
  can anchor a popup or be composed into layout math like other recipes. Purely additive —
  existing callers that ignore the return value are unaffected.
- Claiming the same widget `id` twice in one frame now throws immediately instead of the two
  widgets silently sharing one state slot (hover/active/animation bleeding between them).
  Turning this on surfaced 3 real pre-existing collisions in `samples:studio` (a pill
  toolbar's separators, and two toolbar hairlines all defaulting to the same fallback id) —
  fixed alongside the check.

### Fixed

- Both backends leaked one pipeline per vertex format on teardown. Each enumerated its pipeline
  companions by hand in `destroyBackend` and each omitted the transparent one when it was added.
  `PipelineSet.all` now enumerates them, so a companion added later cannot be missed.
- `popup()`'s `id` is now required (headless facade + the underlying primitive). It used to
  default through `id ?: modifier.testTag ?: "popup"` — any popup with neither set silently
  shared one fade-animation state bucket with every other un-ided popup on screen. Fixed the
  same collision-by-default pattern in `shadcnAvatarBadge`, `shadcnAvatarGroup`,
  `shadcnFieldSeparator`, and `shadcnSidebarMenuSub`, whose defaulted `id`s fed a *different*
  child widget's required-id slot.
- A `FillMax`-width `interactiveSurface()`/`button()` (any content-lambda form, not just the
  headless label form) with no explicit width could never resolve a hover-conditional
  `Style` — `surfaceCore` built its interactive style from a placeholder `hovered = false`
  computed before any real slot existed, then never recomputed it once the real slot (and a
  real hit test) existed. `resolveInteractiveSurface`'s headless path never had this bug; it
  now shares the same claim-then-hit-test-then-resolve order. Same fix ports package 5's
  wrap-content/`FillMax` intrinsic-width exception into `surfaceCore`, which had only ever
  reached the headless-only button path.

## [0.1.0-dev.5] - 2026-08-18

UI-core cleanup and correctness pass: dead deprecated-API surface removed, the style-merge
channel collapsed toward `Style` alone, wrap-content+fillMax layout unlocked (button groups
now size and round corners correctly), and a cross-platform input-drop bug fixed at its
shared root instead of per-backend.

### Fixed

- A pointer down+up pair landing within one frame interval (fast taps, synthetic/automation
  clicks) could have its down edge silently erased by the up event, on every platform --
  fixed once in the shared `Input` class rather than per input bridge.
- `shadcnAlertDialog`'s buttons rendered unstyled and never reported which one was pressed.
- `shadcnDrawer`/`shadcnDialog` set `showScrim = true` but never `scrimColor`, so their
  scrim silently never drew.
- Two tooltips in one frame shared one state bucket (a default `id` on a stateful widget),
  causing visible frame-to-frame instability.
- A vertical button group filled its enclosing frame instead of wrapping to its content,
  and members had no per-corner rounding. Fixed at the root: a wrap-content parent now sees
  a `FillMax` child's real intrinsic size during measurement instead of a placeholder.

### Changed

- `UiContext`'s 31-member deprecated mirror layer removed (821 -> 648 lines); ~55 external
  call sites migrated to their real API.
- ~330 lines of dead headless composites and ui-core publics removed.
- Every headless/design-system widget now resolves its visible properties through `Style`
  alone -- no remaining ambient/ex-ambient theme fallback.

## [0.1.0-dev.4] - 2026-08-11

UI correctness and the first piece of the studio layout redesign. Ships the general fix for a
bug class that had produced three separate shipped defects.

### Fixed

- **Widget-state writes made during a measuring pass are dropped.** `column()` re-executes its
  content against a scratch context sharing the real, persisted `WidgetState` but with blank
  input, so anything writing state from that pass corrupted what the real pass read moments
  later in the same frame. It had shipped three times: the resizable handle's drag anchor was
  deleted every frame so dragging did nothing, `animatedHeight` kept a stale height across a
  collapse, and a popup nested one container deeper would not open. The first two were fixed
  per-widget, which left every other stateful widget exposed. Guarding `UiStateValue`'s setter
  covers every hook at once, so a new widget cannot reintroduce it by forgetting to guard itself.
- **Studio's display toggles moved to a viewport-edge pill.** Wireframe and shadows sat in the
  top bar, which was a scoping error -- they govern how one viewport draws, not the document.
  Kept out of the tool rail deliberately: that rail is modal, these are independent booleans.
- **`popup()` honours `Modifier.widthIn()`/`heightIn()`**, so `max-w-*` is expressible for
  popup-based components instead of hard-coded. `maxWidth` applies before measurement as well as
  after, so wrapped content reflows within the cap rather than being clipped.

### Added

- Studio layout audit, target design and an SVG wireframe --
  `docs/tasks/2026-08-11-studio-layout-audit.md`, `-design.md` and `-layout.svg`. The audit found
  three inert controls; the design maps every region to components that already exist and
  sequences the work in independently shippable phases.

## [0.1.0-dev.3] - 2026-08-11

Font rendering: the atlas moved to MTSDF and the verification gaps that let font bugs ship were
closed. Text rendering is NOT finished at this tag -- stem weight still varies with sub-pixel
phase, tracked under Unreleased / Known issues.

### Fixed

- **`rasterize()`'s missing-font placeholder is no longer glyph-shaped.** It drew a filled rect
  in the glyph's own colour, inset 25%, which reads as a blob of text and -- worse -- measures as
  one: probes scanning for ink found placeholder geometry and reported it as glyph metrics. That
  produced a confident but wrong "glyphs render at 0.6x their metrics" investigation and left two
  font gates green while they measured placeholders. Now a solid magenta box over the glyph's
  full bounds, so neither a reader nor a pixel measurement can mistake it for text, and the
  `font` parameter documents that it is required whenever a frame contains glyphs.

- **Glyph ink rendered at ~0.90x of its own metrics** (sub-pixel at 12-14px, past a pixel from
  16px up): the font-atlas generator sized render quads to the glyph outline but UV rects to
  outline + crop bleed + a texel snap, squeezing the padded atlas region into an outline-sized
  quad. Quads are now derived from the snapped sample rect (quad and UV cover the same texels
  1:1) and outline-true `inkMetricsEm` ships separately so `capHeightEm`/baseline/advance
  metrics stay ink-exact. The per-glyph snap slack was also what scattered baselines; the
  Chromium baseline-fidelity drift map is re-measured with an honest probe (transparent
  background, alpha-channel coverage, degenerate-run guard) and every text-bearing snapshot
  signature is re-recorded. `GlyphAbsoluteSizeTest` now gates absolute ink size against
  `capHeightEm * size` -- the external-truth check this repo never had.

  Verified on screen by Ron June Valdoz, 2026-08-11. That confirms this fix specifically, not
  text rendering overall -- stem weight still varies with sub-pixel phase, see Known issues.

## [1.0.0-SNAPSHOT] - not released

Placeholder retained from the Keep a Changelog template. It was never filled in, which is why
every entry above accumulated in Unreleased instead of being cut into a section at tag time --
`v0.1.0-dev.1` through `dev.3` all shipped without one. Sections are cut at tag time from here
on.

### TODO

- [ ] Render triangle via Vulkan (Android, iOS, Desktop)
- [ ] To improve game loop
- [ ] Implement Fixed & Variable timestep
- [ ] Create ECS (Entity, Component, System)
- [ ] Create renderer that can draw single and batch instance
- [ ] Model instancing to provide faster rendering for batch models
- [ ] Animation instancing to provide faster rendering for batch model with animations
- [ ] UI Text, etc. (similar to compose-ui??)
- [ ] Physics Networking (low priority)

### Changes

- Improve vulkan struct to java vice-versa converter

### Added

- Vulkan Android support.
- Initial support for Android, iOS, and desktop OpenGL.
- Implemented using Compose Multiplatform for increased flexibility.
- Experimental high-quality text rendering (TTF).
- GlslValidator plugin for pre-compiling glsl to spv in the demo project.

### Known Issues

- Possible memory leak when using Vulkan.
- Incomplete text rendering.
- Desktop OpenGL texture rendering is leaking.

[unreleased]: https://github.com/awake-lab/awake/compare/v1.0.0...HEAD

[1.0.0-SNAPSHOT]: https://github.com/awake-lab/awake/compare/v0.0.1...v0.0.2
