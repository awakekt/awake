/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// The monitor lookup GlfwWindow_jni.cpp (the window's DPI) and GlfwFullscreen_jni.cpp share.
#pragma once

#include <GLFW/glfw3.h>

// The monitor a windowed window's centre is on, or the primary one when none holds it.
inline GLFWmonitor* awake_monitor_under(GLFWwindow* window) {
    int x = 0, y = 0, width = 0, height = 0;
    glfwGetWindowPos(window, &x, &y);
    glfwGetWindowSize(window, &width, &height);
    const int centre_x = x + width / 2;
    const int centre_y = y + height / 2;
    int count = 0;
    GLFWmonitor** monitors = glfwGetMonitors(&count);
    for (int i = 0; i < count; i++) {
        int left = 0, top = 0;
        glfwGetMonitorPos(monitors[i], &left, &top);
        const GLFWvidmode* mode = glfwGetVideoMode(monitors[i]);
        if (mode && centre_x >= left && centre_x < left + mode->width && centre_y >= top && centre_y < top + mode->height) {
            return monitors[i];
        }
    }
    return glfwGetPrimaryMonitor();
}
