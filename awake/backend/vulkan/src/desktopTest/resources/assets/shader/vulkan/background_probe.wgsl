// Test-only shader for RendererHeadlessBackgroundPipelineTest. Regenerate the committed SPIR-V:
//
//   naga src/desktopTest/shaders/background_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/background_probe.vert.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point vertexMain --shader-stage vertex
//   naga src/desktopTest/shaders/background_probe.wgsl \
//     src/desktopTest/resources/assets/shader/vulkan/background_probe.frag.spv \
//     --input-kind wgsl --keep-coordinate-space --entry-point fragmentMain --shader-stage fragment
//
// Exercises the three primitives a content feature is built from, and nothing else: no vertex
// buffer (VertexFormat.None), a uniform block the pipeline owns rather than a material's
// (PipelineSpec.uniforms), and PipelineVariant.Background's depth test/write both off.
//
// A vertical gradient rather than a flat fill on purpose. A flat fill would let the test pass
// while reading a single wrong-but-uniform colour; asserting that top and bottom DIFFER, and
// that each matches its own declared colour, fails if the uniform block is misbound, packed in
// the wrong order, or never written.
//
// Deliberately not skybox.wgsl: that shader is content, and a GPU backend must not carry it even
// as a test resource -- see docs/reference/render-extensibility.md.

struct Uniforms {
  bottomColor : vec4f,
  topColor : vec4f,
}
@binding(0) @group(0) var<uniform> uniforms : Uniforms;

struct VertexOutput {
  @builtin(position) position : vec4f,
  // 0 at the bottom of the viewport, 1 at the top, in Vulkan's Y-down clip space.
  @location(0) verticalMix : f32,
}

// One triangle covering the viewport, generated from the vertex index -- no vertex buffer bound.
// (-1,-1), (3,-1), (-1,3) in NDC: the two off-screen corners keep the visible area fully covered
// without a second triangle's shared edge.
@vertex
fn vertexMain(@builtin(vertex_index) index : u32) -> VertexOutput {
  let x = select(-1.0, 3.0, index == 1u);
  let y = select(-1.0, 3.0, index == 2u);
  var output : VertexOutput;
  output.position = vec4f(x, y, 0.0, 1.0);
  // y = -1 lands on the TOP row of the read-back image, so that end must mix to topColor.
  // Verified against real pixels rather than reasoned from the clip-space convention: the
  // first version of this line had it inverted and the test caught it.
  output.verticalMix = (y + 1.0) * 0.5;
  return output;
}

@fragment
fn fragmentMain(@location(0) verticalMix : f32) -> @location(0) vec4f {
  return mix(uniforms.topColor, uniforms.bottomColor, clamp(verticalMix, 0.0, 1.0));
}
