/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef VULKANACCESSARENA_H
#define VULKANACCESSARENA_H

#include <cstddef>
#include <jni.h>
#include <vector>

namespace awake {

/**
 * Thread-local RAII scratch arena for temporary C arrays and copied UTF-8 strings produced while
 * marshalling Java/Kotlin Vulkan CreateInfo graphs into native Vulkan structs.
 *
 * Accessors in vulkan-kotlin are often constructed as temporaries (or nested inside loops across
 * parent/child structs), so backing allocations cannot be owned by individual accessor instances
 * without causing use-after-free before the enclosing vkCreate* call executes. Entering a
 * VulkanAccessArena::Scope at the JNI entry point keeps every nested allocation alive for the
 * duration of the Vulkan call and releases them in reverse order when the scope exits.
 */
class VulkanAccessArena {
public:
    class Scope;

    VulkanAccessArena() = default;
    ~VulkanAccessArena();
    VulkanAccessArena(const VulkanAccessArena &) = delete;
    VulkanAccessArena &operator=(const VulkanAccessArena &) = delete;

    static VulkanAccessArena &current();

    template<typename T>
    T *allocArray(std::size_t count) {
        if (count == 0) {
            return nullptr;
        }
        T *ptr = new T[count]();
        cleanups_.push_back({ptr, [](void *p) { delete[] static_cast<T *>(p); }});
        return ptr;
    }

    const char *copyString(const char *str);
    const char *copyJString(JNIEnv *env, jstring strObj);

    void reset();

private:
    struct Cleanup {
        void *ptr;
        void (*deleter)(void *);
    };
    std::vector<Cleanup> cleanups_;
};

class VulkanAccessArena::Scope {
public:
    Scope();
    ~Scope();
    Scope(const Scope &) = delete;
    Scope &operator=(const Scope &) = delete;

private:
    VulkanAccessArena arena_;
    VulkanAccessArena *previous_;
};

} // namespace awake

#endif // VULKANACCESSARENA_H
