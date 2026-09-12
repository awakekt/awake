# About Awake

## Why

Awake is built for developers who prioritize code-first visibility, precision,
and control over editor-first abstractions. Hand-written code gives direct
insight and predictability into what the engine, render passes, and simulation loops
are actually doing every frame.

Kotlin Multiplatform is the core foundation. One unified codebase targeting
every platform — Desktop, Mobile, and Web — without maintaining separate engines
or bolting a scripting layer onto a native core.

## The name

"Awake" represents the awake developer — someone who wants to see, understand,
and control the entire build and runtime instead of delegating it to opaque black boxes.

## Lineage

Awake is the third attempt at this. The earlier two didn't make it far enough
to matter, but they're why this one leads with Vulkan and WebGPU instead of
retrofitting a renderer later, and why the ECS and scene layers were designed
before any sample game was built on top of them.

Work started in 2023 (repo history here is shallow and doesn't reach back
that far, so treat the year as approximate).

## Who's building this

Ron June Valdoz — a Senior Software Engineer and long-time Kotlin user, which
is the whole reason this is a KMP engine instead of a C++/C# one. Awake is a
solo project, not a studio: no company behind it, one person deciding what
gets built next.

## Where this goes

The near-term goal is an engine solid enough to ship real games on. Longer
term, the aim is a studio built around it — the same hand-written, editor-optional
approach, scaled up once the engine has the capability to support it.
