# Keep rules every app that depends on the shader compiler gets, so a shrunk release still finds
# what Naga's native library reaches by name. verifyKeepRules fails when its Rust names a class no
# rule here keeps.

# Naga's native library binds to NagaJni's external functions by class and method name.
-keepclasseswithmembernames class com.awakekt.awake.asset.shadercompiler.** {
    native <methods>;
}

# A shader that doesn't compile is thrown from Rust by class name, through this constructor.
-keep class com.awakekt.awake.asset.shadercompiler.NagaException {
    <init>(java.lang.String);
}
