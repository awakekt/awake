# R8 rules every app that depends on the shader compiler gets: Naga's native library binds to
# NagaJni's external functions by class and method name.
-keepclasseswithmembernames class com.awakekt.awake.asset.shadercompiler.** {
    native <methods>;
}
