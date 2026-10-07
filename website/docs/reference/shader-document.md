# Shader document

<p class="awake-lede">Every key, surface, blend, value type, expression op, statement, input, built-in function and limit a shader document can use. A test fails when the document model has a name this page lacks, or this page lists one the model no longer has.</p>

A shader document is the JSON file a project ships. A scene document's
[`shader_effect`](scene-document-components.md#shader_effect) names it. For how to use one, see
[Ship a shader with a project](../guides/shaders.md#ship-a-shader-with-a-project). The model is
`ShaderDocument` in `com.awakekt.awake.asset:shader-document`.

## Document

A document is one JSON object. An unknown key is an error.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `$schema` | string | none | The JSON Schema the file names, for editors. Loading ignores it. |
| `formatVersion` | integer | `1` | The format version. Only `1` loads. |
| `name` | string | required | A display name for logs and tools. It never reaches the shader source. |
| `surface` | string: `background` · `overlay` · `plane` | required | What the document draws. See [Surfaces](#surfaces). |
| `blend` | string: `opaque` · `alpha` · `additive` | the surface's default | How the colour combines with what is drawn already. See [Blends](#blends). |
| `plane` | object | none | The rectangle a `plane` surface draws. Required for that surface, rejected for the others. See [`plane`](#plane). |
| `parameters` | list of objects | `[]` | Values a scene sets by name, within the [limits](#limits). See [`parameters`](#parameters). |
| `textures` | list of objects | `[]` | Images a scene supplies by name, read with `sample`. See [`textures`](#textures). |
| `vertex` | object | none | A plane's vertex stage. Rejected for the other surfaces. See [`vertex`](#vertex). |
| `fragment` | object | required | The fragment stage. See [`fragment`](#fragment). |

## Surfaces

| Surface | Draws | Default blend | Takes |
| --- | --- | --- | --- |
| `background` | The whole screen, behind the scene, like a sky: drawn before scene geometry, with no depth test. | `opaque` | `opaque` |
| `overlay` | The whole screen, over the scene: alpha-blended over what is drawn, with no depth test. | `alpha` | `alpha` |
| `plane` | A flat rectangle in the scene, placed by its node and depth-tested against scene geometry. | `opaque` | `opaque`, `alpha`, `additive` |

## Blends

| Blend | What it does | Used by |
| --- | --- | --- |
| `opaque` | Replaces what is behind. | `background`, and `plane` by default |
| `alpha` | Mixes by the colour's alpha. | `overlay`, and `plane` when asked |
| `additive` | Adds the colour to what is behind, for glows and light effects. | `plane` only |

## Value types

The `type` of a parameter.

| Type | Numbers | What it holds |
| --- | --- | --- |
| `float` | 1 | One number. |
| `vec2` | 2 | Two numbers. |
| `vec3` | 3 | Three numbers. |
| `vec4` | 4 | Four numbers. |
| `color` | 4 | Red, green, blue and alpha. A `vec4` that tools show as a colour. |

## `plane`

The rectangle a `plane` surface draws. It lies in its node's XZ plane and faces +Y.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `size` | list of two numbers | required | Width along X and depth along Z, in metres, centred on the node. Both are positive. |
| `segments` | integer | `1` | Quads along each side, from 1 to the plane segments [limit](#limits). More of them give a vertex displacement more points to move. |

## `parameters`

Each entry of `parameters`. A scene sets a parameter by `name`, and a parameter it leaves out takes
its `default`.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | string | required | The name a scene sets it by: a letter, then letters, digits or underscores, 32 at most. Unique in the document. |
| `type` | string: `float` · `vec2` · `vec3` · `vec4` · `color` | required | What it holds. See [Value types](#value-types). |
| `default` | list of numbers | required | The value when a scene sets none: as many finite numbers as `type` holds. |

## `textures`

Each entry of `textures`. A scene gives every texture a project path to a single 2D image. A texture
that no `sample` reads is an error.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `name` | string | required | The name a scene supplies it by: a letter, then letters, digits or underscores, 32 at most. Unique in the document. |

## `vertex`

A plane's vertex stage.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `statements` | list of [statements](#statements) | `[]` | Locals and loops the `displacement` reads. |
| `displacement` | [expression](#expressions) | none | How far to move each vertex along the plane's normal, in metres: one number, or none. |

## `fragment`

The fragment stage.

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `statements` | list of [statements](#statements) | `[]` | Locals, loops, branches and discards, run in order before `color`. |
| `color` | [expression](#expressions) | required | The pixel's colour: four numbers, red, green, blue and alpha. |

## Expressions

An expression is an object whose `op` key names it. A value is one to four numbers, a scalar or a
2-, 3- or 4-component vector, or a condition. Numbers combine component by component, and a scalar
combines with a vector by applying to each component.

| Op | Fields | Gives | What it does |
| --- | --- | --- | --- |
| `const` | `value` | The numbers given | A literal of one to four finite numbers. |
| `vec2` | `args` | 2 numbers | A vector built from scalars and smaller vectors whose components add up to 2, or from one scalar repeated. |
| `vec3` | `args` | 3 numbers | The same, adding up to 3. |
| `vec4` | `args` | 4 numbers | The same, adding up to 4. |
| `param` | `name` | The parameter's type | The value a scene set for one of the document's parameters, or its default. |
| `input` | `input` | The input's numbers | A value the engine supplies each frame. See [Inputs](#inputs). |
| `local` | `name` | The local's shape | A `let` or `var` declared earlier in the stage, or a loop's counter. |
| `add` | `a`, `b` | The operands' shape | `a + b`. Two numbers of one shape, or a scalar with a vector. |
| `sub` | `a`, `b` | The operands' shape | `a - b`, with the operands `add` takes. |
| `mul` | `a`, `b` | The operands' shape | `a * b`, component by component, with the operands `add` takes. |
| `div` | `a`, `b` | The operands' shape | `a / b`, component by component, with the operands `add` takes. |
| `neg` | `value` | The operand's shape | `-value`. |
| `lt` | `a`, `b` | A condition | `a < b`. Two scalars. |
| `le` | `a`, `b` | A condition | `a <= b`. Two scalars. |
| `gt` | `a`, `b` | A condition | `a > b`. Two scalars. |
| `ge` | `a`, `b` | A condition | `a >= b`. Two scalars. |
| `eq` | `a`, `b` | A condition | `a == b`. Two scalars. |
| `ne` | `a`, `b` | A condition | `a != b`. Two scalars. |
| `and` | `a`, `b` | A condition | Both conditions hold. |
| `or` | `a`, `b` | A condition | Either condition holds. |
| `not` | `value` | A condition | The condition does not hold. |
| `swizzle` | `value`, `components` | One number per letter | Components of a vector, picked and reordered: `xy`, `zyx`, `rgb`, `w`. One to four of `xyzw`, or one to four of `rgba`, within the vector's size. |
| `call` | `fn`, `args` | As the function says | A built-in function. See [Functions](#functions). |
| `sample` | `texture`, `uv` | 4 numbers | The colour of one of the document's textures at `uv`, a 2-component vector, 0 to 1 across the image. Reads the image's base level. Fragment stage only. |

## Statements

A statement is an object whose `statement` key names it. A local is visible from its declaration to
the end of the block it is in, and a name cannot be declared again while it is visible.

| Statement | Fields | What it does |
| --- | --- | --- |
| `let` | `name`, `value` | A local that keeps its first value. |
| `var` | `name`, `value` | A local that `set` can change. |
| `set` | `name`, `value` | Gives a `var` a new value of the same shape. |
| `if` | `condition`, `then` | Runs `then`, a list of statements, when `condition` holds. |
| `for` | `counter`, `from`, `until`, `body` | Runs `body`, a list of statements, once for each whole number from `from` up to, not including, `until`, with `counter` holding it as a scalar. Both bounds are literal numbers: `from` is 0 or more, and `until` is more than `from`. |
| `discard_if` | `condition` | Draws nothing for this pixel when `condition` holds. Fragment stage only, and not inside an `if` or a `for`. |

## Inputs

The `input` of an `input` expression: a value the engine supplies each frame.

| Input | Numbers | Where | What it is |
| --- | --- | --- | --- |
| `uv` | 2 | Any surface | On a plane, the position across it, 0 to 1 along X and Z. On a full-screen surface, the same as `screenUv`. |
| `screenUv` | 2 | Fragment stage | The pixel's position on the screen, 0 to 1 from the top-left corner. |
| `worldPosition` | 3 | `plane` only | The point on the plane in world space, before any displacement. |
| `normal` | 3 | `plane` only | The plane's facing direction in world space, unit length. |
| `viewDirection` | 3 | Fragment stage | The unit direction from the camera through the pixel. |
| `cameraPosition` | 3 | Any surface | The camera's position in world space. |
| `sunDirection` | 3 | Any surface | The scene light's direction, as the engine's scene light reports it. |
| `time` | 1 | Any surface | Seconds since the effect started. |
| `deltaTime` | 1 | Any surface | Seconds since the previous frame. |
| `resolution` | 2 | Any surface | The render target's width and height, in pixels. |

## Functions

The `fn` of a `call` expression. Each is the WGSL built-in of the same name. Where the table says
"a scalar or the same shape", a scalar applies to every component.

| Function | Arguments | Gives | What it does |
| --- | --- | --- | --- |
| `sin` | `x` | `x`'s shape | `sin(x)`, in radians. |
| `cos` | `x` | `x`'s shape | `cos(x)`, in radians. |
| `abs` | `x` | `x`'s shape | `abs(x)`. |
| `floor` | `x` | `x`'s shape | `floor(x)`. |
| `fract` | `x` | `x`'s shape | `x - floor(x)`. |
| `sqrt` | `x` | `x`'s shape | `sqrt(x)`. |
| `exp` | `x` | `x`'s shape | e to the power `x`. |
| `saturate` | `x` | `x`'s shape | `x` clamped to 0 to 1. |
| `normalize` | `v` | `v`'s shape | `v` scaled to unit length. A vector only. |
| `length` | `v` | A scalar | A vector's length. |
| `dot` | `a`, `b` | A scalar | The dot product of two vectors of one size. |
| `cross` | `a`, `b` | 3 numbers | The cross product of two 3-component vectors. |
| `min` | `a`, `b` | `a`'s shape | The smaller of `a` and `b`. `b` is a scalar or the same shape as `a`. |
| `max` | `a`, `b` | `a`'s shape | The larger of `a` and `b`. `b` is a scalar or the same shape as `a`. |
| `pow` | `a`, `b` | `a`'s shape | `a` to the power `b`. `b` is a scalar or the same shape as `a`. |
| `step` | `edge`, `x` | `x`'s shape | 0 where `x < edge`, else 1. `edge` is a scalar or the same shape as `x`. |
| `clamp` | `x`, `low`, `high` | `x`'s shape | `x` held between `low` and `high`, each a scalar or the same shape as `x`. |
| `smoothstep` | `low`, `high`, `x` | `x`'s shape | A smooth 0 to 1 ramp from `low` to `high`, each a scalar or the same shape as `x`. |
| `mix` | `a`, `b`, `t` | `a`'s shape | `a` and `b` blended by `t`. `a` and `b` are of one shape, and `t` is a scalar or that shape. |
| `select` | `ifFalse`, `ifTrue`, `condition` | `ifFalse`'s shape | `ifTrue` where `condition` holds, else `ifFalse`. The two values are of one shape. |

## Limits

The properties of `ShaderDocumentLimits`, with the values of `ShaderDocumentLimits.Default`, which
the project loader uses. A document beyond any limit is rejected: its size and JSON nesting before it
is parsed, the rest before anything compiles. A weighted count multiplies each node by the iterations
of the loops around it.

!!! warning "The default limits are provisional"
    They bound the cost of a document, but nobody has measured them on a slow device. Expect them to
    change.

| Limit | Default | What it bounds |
| --- | --- | --- |
| `maxBytes` | 65,536 | The document's size in UTF-8 bytes. |
| `maxJsonNesting` | 128 | How deep objects and arrays may nest in the JSON. |
| `maxExpressionDepth` | 32 | How deep expressions may nest. |
| `maxNodes` | 512 | Expressions and statements in the document, each counted once. |
| `maxWeightedCost` | 4,096 | Expressions and statements, each counted once per time it runs for a pixel or a vertex. |
| `maxLoopIterations` | 32 | Iterations of one loop. |
| `maxLoopNesting` | 2 | Loops inside loops: 2 allows one loop inside another. |
| `maxWeightedSamples` | 16 | Texture reads per pixel, each counted once per time it runs. |
| `maxParameters` | 16 | Parameters. |
| `maxTextures` | 4 | Textures. Never more than 4: a document binds them to four fixed slots. |
| `maxLocals` | 64 | Locals and loop counters across the document. |
| `maxPlaneSegments` | 128 | Quads along each side of a plane. |

## See also

- [Ship a shader with a project](../guides/shaders.md#ship-a-shader-with-a-project) for writing a document and drawing it.
- [`shader_effect`](scene-document-components.md#shader_effect) for the scene component that names one.
