# Keep rules every app that depends on this module gets, so a shrunk and obfuscated desktop release
# (ProGuard) builds with lwjgl-ktx on its classpath.

# lwjgl-ktx's KTXVulkan helper uploads textures through LWJGL's own Vulkan bindings, org.lwjgl.vulkan,
# which Awake doesn't use (its Vulkan renderer has bindings of its own), so lwjgl-vulkan isn't on the
# classpath. This module never calls KTXVulkan, so its references to org.lwjgl.vulkan are dead.
-dontwarn org.lwjgl.vulkan.**
