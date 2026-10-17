/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// JNI for GlfwGamepad.kt: the first connected controller, through GLFW's gamepad API, which maps
// every controller it knows to one layout.

#include <jni.h>
#include <GLFW/glfw3.h>

namespace {
constexpr int kLeftTriggerBit = GLFW_GAMEPAD_BUTTON_LAST + 1;
constexpr int kRightTriggerBit = GLFW_GAMEPAD_BUTTON_LAST + 2;
}

// Bit n is GLFW gamepad button n (GLFW_GAMEPAD_BUTTON_A to GLFW_GAMEPAD_BUTTON_DPAD_LEFT); the two
// bits after them are the left and right triggers, pulled past half way (an axis rests at -1 and
// reaches 1). -1 when no connected joystick is a gamepad GLFW has a mapping for.
extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwGamepad_glfwPollGamepadButtons(
        JNIEnv* env,
        jclass clazz) {
    for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; ++jid) {
        GLFWgamepadstate state;
        if (!glfwJoystickIsGamepad(jid) || !glfwGetGamepadState(jid, &state)) continue;
        jint bits = 0;
        for (int button = 0; button <= GLFW_GAMEPAD_BUTTON_LAST; ++button) {
            if (state.buttons[button] == GLFW_PRESS) bits |= 1 << button;
        }
        if (state.axes[GLFW_GAMEPAD_AXIS_LEFT_TRIGGER] > 0.0f) bits |= 1 << kLeftTriggerBit;
        if (state.axes[GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER] > 0.0f) bits |= 1 << kRightTriggerBit;
        return bits;
    }
    return -1;
}
