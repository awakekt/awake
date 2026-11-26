/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <includes/VulkanAccessArena.h>

#include <cstring>

namespace awake {

namespace {
thread_local VulkanAccessArena *activeArena = nullptr;
} // namespace

VulkanAccessArena::Scope::Scope() : previous_(activeArena) {
    activeArena = &arena_;
}

VulkanAccessArena::Scope::~Scope() {
    activeArena = previous_;
}

VulkanAccessArena::~VulkanAccessArena() {
    reset();
}

VulkanAccessArena &VulkanAccessArena::current() {
    if (activeArena != nullptr) {
        return *activeArena;
    }
    // Defensive fallback if an accessor is ever invoked outside an explicit Scope: keep the
    // pointer valid until thread exit rather than dereferencing null.
    static thread_local VulkanAccessArena fallbackArena;
    return fallbackArena;
}

const char *VulkanAccessArena::copyString(const char *str) {
    if (str == nullptr) {
        return nullptr;
    }
    const std::size_t len = std::strlen(str) + 1;
    char *copy = allocArray<char>(len);
    std::memcpy(copy, str, len);
    return copy;
}

const char *VulkanAccessArena::copyJString(JNIEnv *env, jstring strObj) {
    if (strObj == nullptr) {
        return nullptr;
    }
    const char *str = env->GetStringUTFChars(strObj, nullptr);
    if (str == nullptr) {
        return nullptr;
    }
    const char *copy = copyString(str);
    env->ReleaseStringUTFChars(strObj, str);
    return copy;
}

void VulkanAccessArena::reset() {
    for (auto it = cleanups_.rbegin(); it != cleanups_.rend(); ++it) {
        it->deleter(it->ptr);
    }
    cleanups_.clear();
}

} // namespace awake
