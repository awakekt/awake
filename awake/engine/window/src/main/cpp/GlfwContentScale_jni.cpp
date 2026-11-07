/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// JNI for GlfwWindow.kt's display-scale calls: the window's content scale (the OS display scale,
// 1.25 at Windows' 125%) and GLFW's content scale callback, which fires when that scale changes:
// WM_DPICHANGED on Windows, a window moving to a monitor with another backing scale on macOS, an
// Xft.dpi or output-scale change on Linux. DisplayDensity.kt reads both.

#include <jni.h>
#include <mutex>
#include <unordered_set>
#include <GLFW/glfw3.h>

#include "jni-utils.h"

namespace {
// Windows whose content scale changed since the last glfwConsumeContentScaleChanged. The callback
// runs inside glfwPollEvents on the frame-loop thread; the lock only guards a teardown elsewhere.
std::mutex g_changedLock;
std::unordered_set<GLFWwindow*> g_changed;

void on_content_scale(GLFWwindow* window, float, float) {
    std::lock_guard<std::mutex> lock(g_changedLock);
    g_changed.insert(window);
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetContentScaleCallback(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    if (!handle) {
        throw_illegal_state(env, "glfwSetContentScaleCallback: window not initialized");
        return;
    }
    glfwSetWindowContentScaleCallback(handle, on_content_scale);
}

// Whether [window]'s content scale changed since the last call, then cleared.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwConsumeContentScaleChanged(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    std::lock_guard<std::mutex> lock(g_changedLock);
    return g_changed.erase(handle) > 0 ? JNI_TRUE : JNI_FALSE;
}

// The window's horizontal content scale: the ratio of the monitor's current DPI to the platform's
// default (96 on Windows and X11, 72 on macOS, whose Retina panels report 2).
extern "C" JNIEXPORT jfloat JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetWindowContentScale(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    if (!handle) {
        throw_illegal_state(env, "glfwGetWindowContentScale: window not initialized");
        return 0;
    }
    float scale_x = 0.0f, scale_y = 0.0f;
    glfwGetWindowContentScale(handle, &scale_x, &scale_y);
    return static_cast<jfloat>(scale_x);
}
