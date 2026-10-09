# R8 rules every app that depends on this backend gets, so a shrunk release still finds what
# jolt-jni's native library reaches by name.

# libjoltjni.so calls back into jolt-jni's Java classes, such as CustomContactListener, by class,
# method and field name; jolt-jni ships no rules of its own.
-keep class com.github.stephengold.joltjni.** { *; }

# This backend's own classes, including the listener libjoltjni.so calls back into.
-keep class com.awakekt.awake.physics.jolt.** { *; }
