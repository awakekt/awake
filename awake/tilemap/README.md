# Awake Tilemap

`awake:tilemap` owns finite orthogonal tile grids, atlas chunk geometry and visibility without a
scene, ECS or renderer dependency. Rows count down from the top left; -1 is empty and other values
select atlas frames in reading order. `TilemapGrid` copies its input and increments only the
affected chunk's revision when a cell changes.

`TilemapAtlas` maps whole pixel cells to local XY quads. The grid's origin is its top left, +X runs
right and -Y down. Its geometry uses bottom-up texture UVs and `PositionUv`. Empty chunks return
no geometry. Visibility tests the transformed chunk rectangle against supplied frustum planes,
including node rotation, scaling, translation and depth.

The portable `TilemapRenderBatch` in `engine:render:passes` owns cached GPU meshes and one nearest
filtered atlas material. It submits one draw per visible non-empty chunk and rebuilds only dirty
chunks, waiting for outstanding GPU work before replacing owned resources. Requests and their
uniform arrays are borrowed until the next collection. Call `destroy()` before renderer teardown.

`scene:scene2d` binds these capabilities as `tilemap` in a scene document. This slice supports one
atlas per finite layer; tile animation, per-cell flips, collision generation, infinite maps and
authoring-tool file import are separate capabilities.
