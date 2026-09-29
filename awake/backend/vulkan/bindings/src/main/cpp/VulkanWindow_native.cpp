/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Desktop-only, like VulkanWindow_jni.gen.cpp: only desktop-native's CMake build compiles it.
//
// GLFW scroll input is push/callback-based, so these accumulate every callback tick until the
// render thread polls them. One process-wide pair is fine for the same reason a single global
// GLFWwindow* would be: this codebase runs one GLFW window per process. Not thread-guarded --
// the callback fires synchronously inside glfwPollEvents(), and the consume calls poll from the
// same render thread right after, per this project's "one thread owns every Vulkan/GLFW call"
// rule.
#include <jni.h>
#include <GLFW/glfw3.h>

#if defined(__APPLE__)
// VulkanWindow_native_macos.mm: GLFW does not say whether a scroll came from a trackpad.
extern "C" void awake_macos_watch_scroll_precision(void);
extern "C" int awake_macos_last_scroll_precise(void);
#endif

// Matches Kotlin's ScrollSource ordinals: 0 unknown, 1 wheel, 2 trackpad.
static const int SCROLL_SOURCE_UNKNOWN = 0;
static const int SCROLL_SOURCE_WHEEL = 1;
static const int SCROLL_SOURCE_TRACKPAD = 2;

static double g_scrollAccumulatorX = 0.0;
static double g_scrollAccumulatorY = 0.0;
static int g_scrollSource = SCROLL_SOURCE_UNKNOWN;

static void awake_glfwScrollCallback(GLFWwindow* window, double xoffset, double yoffset) {
    (void)window;
    g_scrollAccumulatorX += xoffset;
    g_scrollAccumulatorY += yoffset;
#if defined(__APPLE__)
    int precise = awake_macos_last_scroll_precise();
    // Trackpad wins within one poll: a stray wheel tick mid-swipe should not flip it.
    if (precise == 1) g_scrollSource = SCROLL_SOURCE_TRACKPAD;
    else if (precise == 0 && g_scrollSource == SCROLL_SOURCE_UNKNOWN) g_scrollSource = SCROLL_SOURCE_WHEEL;
#endif
}

extern "C" void awake_glfw_set_scroll_callback(JNIEnv* env, jlong window) {
    (void)env;
#if defined(__APPLE__)
    awake_macos_watch_scroll_precision();
#endif
    glfwSetScrollCallback(reinterpret_cast<GLFWwindow*>(window), awake_glfwScrollCallback);
}

extern "C" jdouble awake_glfw_consume_scroll_delta_y(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    double delta = g_scrollAccumulatorY;
    g_scrollAccumulatorY = 0.0;
    return static_cast<jdouble>(delta);
}

extern "C" jdouble awake_glfw_consume_scroll_delta_x(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    double delta = g_scrollAccumulatorX;
    g_scrollAccumulatorX = 0.0;
    return static_cast<jdouble>(delta);
}

extern "C" jint awake_glfw_consume_scroll_source(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    int source = g_scrollSource;
    g_scrollSource = SCROLL_SOURCE_UNKNOWN;
    return static_cast<jint>(source);
}
