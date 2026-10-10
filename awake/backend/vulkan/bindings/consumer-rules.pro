# Keep rules every app that depends on these bindings gets, so a shrunk release still finds what
# libawake-vulkan reaches by name. verifyKeepRules fails when its C++ names a class no rule here
# keeps.

# The bindings' own types: the native code finds them with FindClass, reads their fields with
# GetFieldID and builds them through their constructors, all by name.
-keep class com.awakekt.awake.vulkan.* { *; }
-keep class com.awakekt.awake.vulkan.enums.** { *; }
-keep class com.awakekt.awake.vulkan.gen.** { *; }
-keep class com.awakekt.awake.vulkan.handles.** { *; }
-keep class com.awakekt.awake.vulkan.models.** { *; }
-keep class com.awakekt.awake.vulkan.utils.** { *; }

# The Kotlin type of VkDebugUtilsMessengerCreateInfoEXT.pfnUserCallback, whose invoke the native
# debug messenger calls.
-keep interface kotlin.jvm.functions.Function4 { *; }
