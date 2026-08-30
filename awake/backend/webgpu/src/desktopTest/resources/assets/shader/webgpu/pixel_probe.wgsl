// Test-only scene shader for RendererHeadlessPixelTest.
//
// Deliberately unlit: the test asserts exact channel values, so any shading term would make the
// expected pixel depend on light direction rather than on the path under test. The colour that
// comes out is the colour that went in.
//
// This backend's own resources ship only UI and debug-line shaders -- no 3D scene shader -- so
// a pixel test needs one of its own. WGSL needs no compilation step, unlike Vulkan's equivalent
// probe, which had to be run through naga and committed as SPIR-V.

// Matches InstancedUniformLayout, which is what `performRenderToTexture` writes for every
// primary-format draw: mvp, then the directional light's two vec4f. The light fields are never
// read here; they exist so this shader's block matches the buffer it is handed.
struct Uniforms {
  mvp : mat4x4<f32>,
  lightDirection : vec4f,
  lightColor : vec4f,
}
@binding(0) @group(0) var<uniform> uniforms : Uniforms;

struct VertexOutput {
  @builtin(position) position : vec4f,
  @location(0) color : vec3f,
}

// VertexFormat.PositionColorUv: position vec3 @ 0, colour vec3 @ 1, uv vec2 @ 2.
@vertex
fn vertexMain(
  @location(0) inPosition : vec3f,
  @location(1) inColor : vec3f,
  @location(2) inUv : vec2f,
) -> VertexOutput {
  var output : VertexOutput;
  output.position = uniforms.mvp * vec4f(inPosition, 1.0);
  output.color = inColor;
  return output;
}

@fragment
fn fragmentMain(@location(0) color : vec3f) -> @location(0) vec4f {
  return vec4f(color, 1.0);
}
