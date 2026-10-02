/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// JNI for GlfwWindow.kt: the desktop window, its input, scroll, clipboard and cursor.
// Moved from the Vulkan bindings' VulkanWindow_jni.gen.cpp (first written by
// jni-binding-generator, then hand-edited); the two calls that need a VkInstance stay there.
// Scroll and clipboard delegate to GlfwWindow_native.cpp.

#include <jni.h>
#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>
#include <cstdint>
#include <GLFW/glfw3.h>

#include "jni-utils.h"

// Native implementations are kept outside generated JNI code.
extern "C" void awake_window_set_scroll_callback(JNIEnv* env, jlong window);
extern "C" jdouble awake_window_consume_scroll_delta_y(JNIEnv* env, jlong window);
extern "C" jdouble awake_window_consume_scroll_delta_x(JNIEnv* env, jlong window);
extern "C" jint awake_window_consume_scroll_source(JNIEnv* env, jlong window);
extern "C" jstring awake_window_get_clipboard_string(JNIEnv* env, jlong window);
extern "C" void awake_window_set_clipboard_string(JNIEnv* env, jlong window, jstring text);


extern "C" JNIEXPORT jboolean JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwInit(
        JNIEnv* env,
        jclass clazz) {
    return glfwInit() ? JNI_TRUE : JNI_FALSE;
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwTerminate(
        JNIEnv* env,
        jclass clazz) {
    glfwTerminate();
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwWindowHint(
        JNIEnv* env,
        jclass clazz,
        jint hint,
        jint value) {
    // --- Marshalling ---
    int32_t hint_val = static_cast<int32_t>(hint);
    int32_t value_val = static_cast<int32_t>(value);

    glfwWindowHint(hint_val, value_val);
}


extern "C" JNIEXPORT jlong JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwCreateWindow(
        JNIEnv* env,
        jclass clazz,
        jint width,
        jint height,
        jstring title) {
    // --- Marshalling ---
    int32_t width_val = static_cast<int32_t>(width);
    int32_t height_val = static_cast<int32_t>(height);
    std::string title_val = jstring2string(env, title);
    if (env->ExceptionCheck()) return 0;

    // --- Error handling ---
    if (title_val.empty()) {
        throw_illegal_argument(env, "glfwCreateWindow: title is required");
        return 0;
    }

    GLFWwindow* window = glfwCreateWindow(width_val, height_val, title_val.c_str(), nullptr, nullptr);
    if (!window) {
        throw_illegal_state(env, "glfwCreateWindow: window creation failed");
        return 0;
    }
    return reinterpret_cast<jlong>(window);
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwDestroyWindow(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwDestroyWindow: window not initialized");
        return;
    }

    glfwDestroyWindow(reinterpret_cast<GLFWwindow*>(window_ptr));
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwFocusWindow(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwFocusWindow: window not initialized");
        return;
    }

    glfwFocusWindow(reinterpret_cast<GLFWwindow*>(window_ptr));
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwWindowShouldClose(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwWindowShouldClose: window not initialized");
        return JNI_FALSE;
    }

    return glfwWindowShouldClose(reinterpret_cast<GLFWwindow*>(window_ptr)) ? JNI_TRUE : JNI_FALSE;
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwPollEvents(
        JNIEnv* env,
        jclass clazz) {
    glfwPollEvents();
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetFramebufferWidth(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetFramebufferWidth: window not initialized");
        return 0;
    }

    int width = 0, height = 0;
    glfwGetFramebufferSize(reinterpret_cast<GLFWwindow*>(window_ptr), &width, &height);
    return static_cast<jint>(width);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetFramebufferHeight(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetFramebufferHeight: window not initialized");
        return 0;
    }

    int width = 0, height = 0;
    glfwGetFramebufferSize(reinterpret_cast<GLFWwindow*>(window_ptr), &width, &height);
    return static_cast<jint>(height);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetWindowWidth(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetWindowWidth: window not initialized");
        return 0;
    }

    int width = 0, height = 0;
    glfwGetWindowSize(reinterpret_cast<GLFWwindow*>(window_ptr), &width, &height);
    return static_cast<jint>(width);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetWindowHeight(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetWindowHeight: window not initialized");
        return 0;
    }

    int width = 0, height = 0;
    glfwGetWindowSize(reinterpret_cast<GLFWwindow*>(window_ptr), &width, &height);
    return static_cast<jint>(height);
}




extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetKey(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint key) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);
    int32_t key_val = static_cast<int32_t>(key);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetKey: window not initialized");
        return 0;
    }

    return static_cast<jint>(glfwGetKey(reinterpret_cast<GLFWwindow*>(window_ptr), key_val));
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetMouseButton(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint button) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);
    int32_t button_val = static_cast<int32_t>(button);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetMouseButton: window not initialized");
        return 0;
    }

    return static_cast<jint>(glfwGetMouseButton(reinterpret_cast<GLFWwindow*>(window_ptr), button_val));
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetClipboardString(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetClipboardString: window not initialized");
        return nullptr;
    }

    return awake_window_get_clipboard_string(env, window);
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetClipboardString(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jstring text) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);
    std::string text_val = jstring2string(env, text);
    if (env->ExceptionCheck()) return;

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwSetClipboardString: window not initialized");
        return;
    }
    if (text_val.empty()) {
        throw_illegal_argument(env, "glfwSetClipboardString: text is required");
        return;
    }

    awake_window_set_clipboard_string(env, window, text);
    return;
}


extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetCursorPos(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetCursorPos: window not initialized");
        return nullptr;
    }

    double x = 0.0, y = 0.0;
    glfwGetCursorPos(reinterpret_cast<GLFWwindow*>(window_ptr), &x, &y);
    jdouble values[2] = { x, y };
    jdoubleArray result = env->NewDoubleArray(2);
    env->SetDoubleArrayRegion(result, 0, 2, values);
    return result;
}


extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetScrollCallback(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwSetScrollCallback: window not initialized");
        return;
    }

    awake_window_set_scroll_callback(env, window);
    return;
}


extern "C" JNIEXPORT jdouble JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwConsumeScrollDeltaY(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwConsumeScrollDeltaY: window not initialized");
        return 0.0;
    }

    return awake_window_consume_scroll_delta_y(env, window);
}


extern "C" JNIEXPORT jdouble JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwConsumeScrollDeltaX(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwConsumeScrollDeltaX: window not initialized");
        return 0.0;
    }

    return awake_window_consume_scroll_delta_x(env, window);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwConsumeScrollSource(
        JNIEnv* env,
        jclass clazz,
        jlong window) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwConsumeScrollSource: window not initialized");
        return 0;
    }

    return awake_window_consume_scroll_source(env, window);
}


// Hand-written, same reasoning as awake_glfwScrollCallback above: doesn't fit the generator's
// "one GLFW call per function" template -- this lazily creates each standard cursor exactly
// once (glfwCreateStandardCursor) and caches it here, keyed by GLFW's own shape constant, so a
// real per-frame glfwSetCursorShape call never re-creates a cursor object. One process-wide
// cache is fine for the same "one GLFW window per process" reason GlfwWindow_native.cpp's
// scroll accumulators give. Never freed (glfwTerminate() tears down the whole GLFW context anyway, taking
// every outstanding GLFWcursor* with it).
static std::unordered_map<int32_t, GLFWcursor*> g_cursorCache;

extern "C" JNIEXPORT void JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwSetCursorShape(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint shape) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);
    int32_t shape_val = static_cast<int32_t>(shape);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwSetCursorShape: window not initialized");
        return;
    }

    auto cached = g_cursorCache.find(shape_val);
    GLFWcursor* cursor;
    if (cached != g_cursorCache.end()) {
        cursor = cached->second;
    } else {
        cursor = glfwCreateStandardCursor(shape_val);
        g_cursorCache[shape_val] = cursor;
    }
    glfwSetCursor(reinterpret_cast<GLFWwindow*>(window_ptr), cursor);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_awakekt_awake_engine_window_GlfwWindow_glfwGetWindowAttrib(
        JNIEnv* env,
        jclass clazz,
        jlong window,
        jint attrib) {
    // --- Marshalling ---
    void* window_ptr = reinterpret_cast<void*>(window);

    // --- Error handling ---
    if (!window_ptr) {
        throw_illegal_state(env, "glfwGetWindowAttrib: window not initialized");
        return 0;
    }

    return static_cast<jint>(glfwGetWindowAttrib(reinterpret_cast<GLFWwindow*>(window_ptr), static_cast<int>(attrib)));
}

