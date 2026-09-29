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

static double g_scrollAccumulatorX = 0.0;
static double g_scrollAccumulatorY = 0.0;

static void awake_glfwScrollCallback(GLFWwindow* window, double xoffset, double yoffset) {
    (void)window;
    g_scrollAccumulatorX += xoffset;
    g_scrollAccumulatorY += yoffset;
}

extern "C" void awake_glfw_set_scroll_callback(JNIEnv* env, jlong window) {
    (void)env;
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
