# Trails

<p class="awake-lede">A ribbon that follows a path: a strip of point pairs whose points, colours and visible length a system changes every frame, rewritten in place in one GPU buffer.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:trail</code></span>
<span class="awake-badge">module: <code>awake:scene:trail</code></span>
<span class="awake-badge">ECS: <code>TrailRenderer</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A trail is a strip of up to `capacity` point pairs. Pair `i` is two points, `a` and `b`, each with its
own straight-alpha colour, and the ribbon runs from pair to pair as one triangle strip. It covers a
melee swing's ribbon, a projectile's streak, a tyre mark, a beam or a lightning arc.

- Its GPU buffer is created once, at `capacity`, the first frame the trail is drawn. A frame that
  changed pairs uploads only the range that changed; a frame that only changes `visible` uploads
  nothing; once warm, nothing is allocated per frame, with dozens of trails on screen.
- It is drawn unlit and depth tested in the transparent pass, through its entity's world matrix.
  Trails are double-sided by default and depth writes are opt-in. A trail casts no shadow.
- It is culled by the box around its visible pairs.
- An optional texture is sampled with `u` along the strip and `v` across it: `v` is 0 at `a` and 1
  at `b`. Without one, the colour is the vertices' alone. `tint` multiplies every vertex's colour.

## Place a trail from a scene document

```json title="Scene document"
{
  "component": "trail_renderer",
  "capacity": 300,
  "blend": "Additive",
  "texture": "textures/ribbon.png",
  "depthWrite": false,
  "doubleSided": true,
  "tint": {"r": 1, "g": 0.6, "b": 0.2, "a": 1}
}
```

| Field | Default | What it does |
| --- | --- | --- |
| `capacity` | `64` | The most pairs it holds, 2 to 65536. |
| `blend` | `Additive` | `Additive` adds to what is behind it; `Alpha` covers it by its alpha. |
| `tint` | white | Multiplied into every vertex's colour. |
| `texture` | none | Scene texture name or project-relative image path. |
| `depthWrite` | `false` | Records the trail's depth so it can occlude blended draws that follow it. |
| `doubleSided` | `true` | Set to `false` to cull back faces. The front is the side from which, with each pair's `b` above its `a`, the pairs run right to left: the side `(b - a) x (next a - a)` points to. |

The document says how the trail is drawn. Its pairs, how many are visible and their colours are
written by a game's systems. A trail that names a texture starts drawing once the image is ready.
Setting `TrailRenderer.texture` in code overrides the document's texture while it is set.

## Drive it from a system

```kotlin
val trail = world.get<TrailRenderer>(entity) ?: return

// When the swing starts: smooth a few sampled key pairs (base and tip) into a ribbon.
val pairs = trail.strip.setSmoothed(baseKeys, tipKeys, steps = 12, Color.White, Color.White, scratch)
trail.visible = 0

// Every frame: reveal a few more pairs and fade from the newest back towards black.
trail.visible = minOf(pairs, trail.visible + 6)
val newest = trail.visible - 1
for (pair in 0..newest) {
    val fade = 1f - (newest - pair).toFloat() / newest.coerceAtLeast(1)
    trail.strip.setColor(pair, fade, fade, fade, 1f)
}
```

`setSmoothed` runs a uniform Catmull-Rom curve through the keys, `steps` pairs from each key to the
next, and continues the path in a straight line past its first and last keys. Keep one
`SmoothingScratch` to smooth without allocating; `catmullRom` evaluates one point.

## Without a scene

`awake:trail` has no scene dependency. An app with its own world draws trails through a
`TrailBatch`, given each trail's world matrix and the view's frustum planes:

```kotlin
val batch = TrailBatch(renderer)
val frustum = Frustum.planes(lens, aspect)
batch.collect(trail, worldMatrix, frustum, draws)
batch.releaseUnseen() // at the end of the frame: frees trails this frame did not draw
```

The draws need `trailScenePipeline()` (`awake:asset:shader-pack`) in the renderer's plan, which
`ProjectRenderPlan` includes. On a device without `GpuDynamicMeshes`, such as a headless host with
no renderer, a batch draws nothing and allocates no GPU memory.

## Under the hood

The buffer is a `DynamicMesh` from the render contract's optional `GpuDynamicMeshes` capability:
vertices rewritten in place, a fixed index buffer, and a count of indices to draw. Vulkan keeps one
host-visible vertex buffer per frame in flight and brings each up to date, over the range written
since, when a frame that draws it is prepared. WebGPU writes the range on the queue at once, which
is ordered after the frames already submitted.

A trail is drawn by one batch: the batch takes the strip's record of changed pairs as it uploads.
