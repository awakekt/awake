// Test-only diagnostic for RendererHeadlessShadowMapTest. Regenerate the committed SPIR-V:
//
//   naga src/desktopTest/shaders/shadow_sample_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/shadow_sample_probe.vert.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point vertexMain --shader-stage vertex
//   naga src/desktopTest/shaders/shadow_sample_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/shadow_sample_probe.frag.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point fragmentMain --shader-stage fragment
//
// Answers one question lit_shadow.wgsl cannot: is the depth pre-pass writing anything at all?
// This outputs the RAW sampled shadow-map depth as greyscale, with no lighting, no bias, no PCF
// and no comparison. A uniform frame means the map holds one value -- the pre-pass never ran or
// never reached this descriptor. A frame showing the caster's silhouette means the map is fine
// and the fault is downstream, in lit_shadow's own comparison.
//
// Uniforms must match lit_shadow.wgsl field for field: it binds the same per-draw buffer that
// prepareDrawCalls already writes, and reads only mvp and lightMvp.

struct Uniforms {
  mvp : mat4x4<f32>,
  lightDirection : vec4f,
  lightColor : vec4f,
  pointLightPositions : array<vec4f, 4>,
  pointLightColors : array<vec4f, 4>,
  lightMvp : mat4x4<f32>,
  model : mat4x4<f32>,
  cameraPosition : vec4f,
  material : vec4f,
  fogColor : vec4f,
}
@binding(0) @group(0) var<uniform> uniforms : Uniforms;
@binding(3) @group(0) var shadowMap : texture_2d<f32>;
@binding(4) @group(0) var shadowMapSampler : sampler;

struct VertexOutput {
  @builtin(position) position : vec4f,
  @location(0) shadowPos : vec4f,
}

@vertex
fn vertexMain(
  @location(0) inPosition : vec3f,
  @location(1) inNormal : vec3f,
  @location(2) inColor : vec3f,
) -> VertexOutput {
  var output : VertexOutput;
  output.position = uniforms.mvp * vec4f(inPosition, 1.0);
  output.shadowPos = uniforms.lightMvp * vec4f(inPosition, 1.0);
  return output;
}

@fragment
fn fragmentMain(@location(0) shadowPos : vec4f) -> @location(0) vec4f {
  // GREEN when the light-space W is degenerate. A zero lightMvp gives w = 0, the divide below
  // yields NaN, and NaN fails every comparison -- so the out-of-range guard would let it through
  // and sample garbage. The first version of this probe had exactly that hole.
  if (!(shadowPos.w > 0.0)) {
    return vec4f(0.0, 1.0, 0.0, 1.0);
  }
  let projected = shadowPos.xyz / shadowPos.w;
  let uv = projected.xy * 0.5 + vec2f(0.5, 0.5);
  // Outside the map's own extent is magenta, so "sampled off the edge" is never mistaken for
  // "sampled a far depth" -- the two would otherwise both read as near-white.
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
    return vec4f(1.0, 0.0, 1.0, 1.0);
  }
  let depth = textureSampleLevel(shadowMap, shadowMapSampler, uv, 0.0).r;
  return vec4f(depth, depth, depth, 1.0);
}
