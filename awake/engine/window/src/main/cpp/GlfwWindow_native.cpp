/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// Desktop-only, like GlfwWindow_jni.cpp: only desktop-native's CMake build compiles it.
//
// GLFW scroll input is push/callback-based, so these accumulate every callback tick until the
// render thread polls them. One process-wide pair is fine for the same reason a single global
// GLFWwindow* would be: this codebase runs one GLFW window per process. Not thread-guarded --
// the callback fires synchronously inside glfwPollEvents(), and the consume calls poll from the
// same render thread right after, per this project's "one thread owns every GLFW call"
// rule.
#include <jni.h>
#include <GLFW/glfw3.h>

#include <cstdint>
#include <string>

#if defined(__APPLE__)
// GlfwWindow_native_macos.mm: GLFW does not say whether a scroll came from a trackpad.
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

extern "C" void awake_window_set_scroll_callback(JNIEnv* env, jlong window) {
    (void)env;
#if defined(__APPLE__)
    awake_macos_watch_scroll_precision();
#endif
    glfwSetScrollCallback(reinterpret_cast<GLFWwindow*>(window), awake_glfwScrollCallback);
}

extern "C" jdouble awake_window_consume_scroll_delta_y(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    double delta = g_scrollAccumulatorY;
    g_scrollAccumulatorY = 0.0;
    return static_cast<jdouble>(delta);
}

extern "C" jdouble awake_window_consume_scroll_delta_x(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    double delta = g_scrollAccumulatorX;
    g_scrollAccumulatorX = 0.0;
    return static_cast<jdouble>(delta);
}

extern "C" jint awake_window_consume_scroll_source(JNIEnv* env, jlong window) {
    (void)env;
    (void)window;
    int source = g_scrollSource;
    g_scrollSource = SCROLL_SOURCE_UNKNOWN;
    return static_cast<jint>(source);
}

// --- Clipboard ---------------------------------------------------------------------------------
//
// GLFW speaks standard UTF-8 and Java strings are UTF-16. JNI's own *UTF* string calls use
// "modified" UTF-8, which encodes a character outside the Basic Multilingual Plane (an emoji) as
// two 3-byte surrogates rather than one 4-byte sequence, so either direction through them garbles
// such text. These convert explicitly; malformed input becomes U+FFFD rather than a crash.

static const char16_t REPLACEMENT_CHARACTER = 0xFFFD;

static std::u16string awake_utf8_to_utf16(const std::string& in) {
    std::u16string out;
    out.reserve(in.size());
    size_t i = 0;
    while (i < in.size()) {
        unsigned char lead = static_cast<unsigned char>(in[i]);
        uint32_t codePoint;
        size_t length;
        if (lead < 0x80) { codePoint = lead; length = 1; }
        else if ((lead & 0xE0) == 0xC0) { codePoint = lead & 0x1F; length = 2; }
        else if ((lead & 0xF0) == 0xE0) { codePoint = lead & 0x0F; length = 3; }
        else if ((lead & 0xF8) == 0xF0) { codePoint = lead & 0x07; length = 4; }
        else { out.push_back(REPLACEMENT_CHARACTER); i += 1; continue; }
        bool valid = i + length <= in.size();
        for (size_t k = 1; valid && k < length; ++k) {
            unsigned char next = static_cast<unsigned char>(in[i + k]);
            if ((next & 0xC0) != 0x80) valid = false;
            else codePoint = (codePoint << 6) | (next & 0x3F);
        }
        // Overlong forms, surrogates and values past U+10FFFF are not characters.
        static const uint32_t minimumForLength[] = { 0, 0, 0x80, 0x800, 0x10000 };
        if (!valid || codePoint < minimumForLength[length] || codePoint > 0x10FFFF ||
            (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            out.push_back(REPLACEMENT_CHARACTER);
            i += 1;
            continue;
        }
        if (codePoint >= 0x10000) {
            codePoint -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (codePoint >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (codePoint & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(codePoint));
        }
        i += length;
    }
    return out;
}

static std::string awake_utf16_to_utf8(const char16_t* in, size_t length) {
    std::string out;
    out.reserve(length);
    for (size_t i = 0; i < length; ++i) {
        uint32_t codePoint = in[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF && i + 1 < length &&
            in[i + 1] >= 0xDC00 && in[i + 1] <= 0xDFFF) {
            codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (in[i + 1] - 0xDC00);
            i += 1;
        } else if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            codePoint = REPLACEMENT_CHARACTER; // A lone surrogate.
        }
        if (codePoint < 0x80) {
            out.push_back(static_cast<char>(codePoint));
        } else if (codePoint < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
            out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else if (codePoint < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
            out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
            out.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
        }
    }
    return out;
}

extern "C" jstring awake_window_get_clipboard_string(JNIEnv* env, jlong window) {
    // NULL, with a GLFW_FORMAT_UNAVAILABLE error, when the clipboard is empty or not text.
    const char* text = glfwGetClipboardString(reinterpret_cast<GLFWwindow*>(window));
    if (text == nullptr) return nullptr;
    std::u16string utf16 = awake_utf8_to_utf16(text);
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}

extern "C" void awake_window_set_clipboard_string(JNIEnv* env, jlong window, jstring text) {
    const jsize length = env->GetStringLength(text);
    const jchar* chars = env->GetStringChars(text, nullptr);
    if (chars == nullptr) return; // OutOfMemoryError is already pending.
    std::string utf8 = awake_utf16_to_utf8(reinterpret_cast<const char16_t*>(chars), static_cast<size_t>(length));
    env->ReleaseStringChars(text, chars);
    glfwSetClipboardString(reinterpret_cast<GLFWwindow*>(window), utf8.c_str());
}
