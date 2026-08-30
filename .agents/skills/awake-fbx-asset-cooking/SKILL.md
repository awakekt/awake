---
name: awake-fbx-asset-cooking
description: Convert FBX source assets, including Mixamo characters and clips, into Awake-compatible GLB assets. Use when importing, validating, or troubleshooting an FBX-to-glTF asset workflow; do not use for runtime animation-player work.
metadata:
  author: awake
  last-updated: '2026-08-24'
---

# Awake FBX Asset Cooking

FBX is an authoring interchange format, not an Awake runtime asset format. Cook it offline to
GLB, then use the existing `:awake:asset:gltf` loader. Do not add an FBX parser, an FBX runtime
dependency, or FBX-specific types to `:awake:core:animation` or the scene runtime.

## Default converter

Use upstream [FBX2glTF](https://github.com/facebookincubator/FBX2glTF) unless an asset needs a
manual Blender repair. Its standard command is:

```bash
FBX2glTF --binary --anim-framerate bake30 \
  --input source.fbx \
  --output cooked/character.glb
```

- `--binary` produces one distributable `.glb` file.
- `bake30` is the project default: it makes FBX curves deterministic for the current animation
  player. Use `bake60` only when the source demonstrably needs it and record the file-size cost.
- Do not enable Draco compression until Awake's glTF loader explicitly supports it.
- Do not use `--skinning-weights`; that flag belongs to Godot's unmaintained fork, not upstream
  FBX2glTF.
- Treat the converter binary as a host development tool. Check its source and bundled Autodesk
  FBX SDK licensing before redistributing it.

The Godot FBX2glTF fork is useful only to diagnose a converter discrepancy; it is no longer
actively maintained. `ufbx` is a parser library, not a drop-in GLB converter. Propose a separate
offline cooker only after FBX2glTF and Blender have demonstrated a recurring, reproducible gap.

## Mixamo workflow

1. Download the character and/or animation as FBX from Mixamo.
2. Convert each source FBX into a named `.glb`; use meaningful output names such as
   `adventurer-idle.glb` and `adventurer-run.glb`.
3. Load the GLB through `:awake:asset:gltf`; adapt the loaded scene to `AnimationLibrary`, then
   select clips via `AnimationPlayer`/`Animator`.
4. Keep the mesh-and-skeleton asset and its animation-only assets together in the consumer asset
   pack. Retargeting, gameplay state selection, and asset catalog policy remain consumer concerns.

## Acceptance check

Before accepting a cooked asset, verify all of the following in a focused sample or test:

- The mesh is in the expected orientation and scale.
- The bind pose is intact; no exploding or offset limbs.
- Every intended clip is present, named predictably, and plays for its full duration.
- Skinning respects Awake's current shader joint limit (`MAX_JOINTS = 64`).
- Materials/textures render acceptably under Awake's current glTF support.

If a check fails, retain the original FBX and generated GLB, record the converter version and full
command, and reduce the issue to one asset before changing engine code. Prefer an asset-side fix or
Blender re-export before expanding the runtime format boundary.

## Escalation boundary

Use `awake-framework-boundary` before proposing `:awake:asset:fbx`, native bindings, or a shared
FBX cooking module. A future cooker must output GLB and preserve the one-way dependency:

```text
FBX source -> offline cooker -> GLB -> :awake:asset:gltf -> core animation / scene rendering
```
