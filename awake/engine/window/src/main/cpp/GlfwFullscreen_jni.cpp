/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// JNI for GlfwWindow.kt's fullscreen calls: whether a window is on a monitor, its windowed bounds,
// and moving it onto its monitor or back. GlfwFullscreen.kt keeps the windowed bounds to restore.

#include <jni.h>
#include <GLFW/glfw3.h>

#include "GlfwMonitor.h"
#include "jni-utils.h"

namespace {

GLFWwindow* window_or_throw(JNIEnv* env, jlong window, const char* call) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    if (!handle) throw_illegal_state(env, call);
    return handle;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwIsWindowFullscreen(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    GLFWwindow* handle = window_or_throw(env, window, "glfwIsWindowFullscreen: window not initialized");
    if (!handle) return JNI_FALSE;
    return glfwGetWindowMonitor(handle) ? JNI_TRUE : JNI_FALSE;
}

// The windowed position and size, as [x, y, width, height] in screen coordinates. Wayland has no
// window position, so x and y read 0 there.
extern "C" JNIEXPORT jintArray JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetWindowBounds(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    GLFWwindow* handle = window_or_throw(env, window, "glfwGetWindowBounds: window not initialized");
    if (!handle) return nullptr;
    jint bounds[4] = {0, 0, 0, 0};
    int x = 0, y = 0, width = 0, height = 0;
    glfwGetWindowPos(handle, &x, &y);
    glfwGetWindowSize(handle, &width, &height);
    bounds[0] = x;
    bounds[1] = y;
    bounds[2] = width;
    bounds[3] = height;
    jintArray result = env->NewIntArray(4);
    if (result) env->SetIntArrayRegion(result, 0, 4, bounds);
    return result;
}

// Puts the window on the monitor under its centre at that monitor's current video mode: GLFW's
// "windowed full screen", with no mode switch. Returns false when there is no monitor or mode.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetWindowFullscreen(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    GLFWwindow* handle = window_or_throw(env, window, "glfwSetWindowFullscreen: window not initialized");
    if (!handle) return JNI_FALSE;
    GLFWmonitor* monitor = awake_monitor_under(handle);
    const GLFWvidmode* mode = monitor ? glfwGetVideoMode(monitor) : nullptr;
    if (!mode) return JNI_FALSE;
    glfwSetWindowMonitor(handle, monitor, 0, 0, mode->width, mode->height, mode->refreshRate);
    return JNI_TRUE;
}

// Takes the window off its monitor and back to the windowed [x], [y], [width] and [height].
extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetWindowWindowed(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint x,
        jint y,
        jint width,
        jint height) {
    GLFWwindow* handle = window_or_throw(env, window, "glfwSetWindowWindowed: window not initialized");
    if (!handle) return;
    glfwSetWindowMonitor(handle, nullptr, x, y, width, height, GLFW_DONT_CARE);
}
