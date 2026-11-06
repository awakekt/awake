/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// JNI for GlfwWindow.kt's window-frame calls: turning the OS title bar and border on or off, and
// moving the window, which a frameless window's own drag area needs (WindowDecoration.kt).

#include <jni.h>
#include <GLFW/glfw3.h>

#include "jni-utils.h"

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetWindowDecorated(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jboolean decorated) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    if (!handle) {
        throw_illegal_state(env, "glfwSetWindowDecorated: window not initialized");
        return;
    }
    glfwSetWindowAttrib(handle, GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
}

// Moves the window's content area to [x], [y] in screen coordinates. Wayland lets no client place
// its window, so this does nothing there.
extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetWindowPos(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint x,
        jint y) {
    auto* handle = reinterpret_cast<GLFWwindow*>(window);
    if (!handle) {
        throw_illegal_state(env, "glfwSetWindowPos: window not initialized");
        return;
    }
    glfwSetWindowPos(handle, x, y);
}
