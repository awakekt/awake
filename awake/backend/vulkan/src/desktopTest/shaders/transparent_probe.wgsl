// Test-only shader for RendererHeadlessTransparencyTest. Regenerate the committed SPIR-V with:
//
//   naga src/desktopTest/shaders/transparent_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/transparent_probe.vert.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point vertexMain --shader-stage vertex
//   naga src/desktopTest/shaders/transparent_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/transparent_probe.frag.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point fragmentMain --shader-stage fragment
//
// Deliberately unlit: the test asserts exact channel values, so any shading term would make the
// expected blend result depend on light direction rather than on the pipeline under test.
//
// Alpha rides in uv.x rather than being a constant, so ONE shader covers both an opaque draw
// (uv.x = 1) and a half-transparent one (uv.x = 0.5) in the same frame. `triangle.wgsl` cannot
// serve this test at all -- it returns a hardcoded alpha of 1.0, and src-alpha blending at
// alpha 1 is a no-op, so a blend assertion against it would pass whether or not the transparent
// pipeline was ever selected.

// Matches triangle.wgsl's struct exactly -- 24 floats, the `Renderer.createMaterial` default,
// which is what the opaque feature writes for a plain draw. The light fields are never read
// here; they exist so the buffer this shader is handed has the layout the renderer already
// writes.
struct Uniforms {
  mvp : mat4x4<f32>,
  lightDirection : vec4f,
  lightColor : vec4f,
}
@binding(0) @group(0) var<uniform> uniforms : Uniforms;

struct VertexOutput {
  @builtin(position) position : vec4f,
  @location(0) color : vec3f,
  @location(1) uv : vec2f,
}

// VertexFormat.PositionColorUv: position vec3 @ 0, color vec3 @ 1, uv vec2 @ 2.
@vertex
fn vertexMain(
  @location(0) inPosition : vec3f,
  @location(1) inColor : vec3f,
  @location(2) inUv : vec2f,
) -> VertexOutput {
  var output : VertexOutput;
  output.position = uniforms.mvp * vec4f(inPosition, 1.0);
  output.color = inColor;
  output.uv = inUv;
  return output;
}

@fragment
fn fragmentMain(@location(0) color : vec3f, @location(1) uv : vec2f) -> @location(0) vec4f {
  return vec4f(color, uv.x);
}
