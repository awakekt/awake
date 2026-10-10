# Keep rules every app that depends on the window gets, so a shrunk desktop release still finds
# what libawake-window reaches by name. verifyKeepRules fails when its C++ names a class no rule
# here keeps.

# The native library binds to GlfwWindow's external functions by class and method name.
-keepclasseswithmembernames class com.awakekt.awake.engine.window.GlfwWindow {
    native <methods>;
}
