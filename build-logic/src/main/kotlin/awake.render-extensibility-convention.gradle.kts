// Applied to the shader CONTRACT module only. See VerifyRenderExtensibilityTask's own doc
// comment for what drifting back would cost.
val verifyRenderExtensibility = tasks.register<VerifyRenderExtensibilityTask>("verifyRenderExtensibility") {
    group = "verification"
    description = "Reject authored shader content in the backend-neutral shader contract module."
    modulePath.set(project.path)
    contentModulePath.set(":awake:asset:shader-pack")
    // .wgsl is authored shader text; *UniformLayout.kt describes one specific shader's Uniforms
    // struct, so it is content too -- LitShadowUniformLayout described a shader that was never
    // even in this module.
    forbiddenFileSuffixes.set(listOf(".wgsl", "UniformLayout.kt"))
    sourceFiles.from(fileTree("src"))
}

tasks.named("check").configure {
    dependsOn(verifyRenderExtensibility)
}
