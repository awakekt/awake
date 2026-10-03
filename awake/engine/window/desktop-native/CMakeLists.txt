# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# libawake-window: the desktop window and its input (GLFW), loaded by GlfwWindow.kt. No Vulkan or
# other GPU API: a backend creates its own surface from the window handle this library returns.
cmake_minimum_required(VERSION 3.22.1)
project(awake-window-desktop)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)

# GLFW (`brew install glfw` on macOS, `libglfw3-dev` on Linux).
find_library(GLFW_LIBRARY NAMES glfw glfw3 PATHS /opt/homebrew/opt/glfw/lib /usr/local/opt/glfw/lib)
find_path(GLFW_INCLUDE_DIR GLFW/glfw3.h PATHS /opt/homebrew/opt/glfw/include /usr/local/opt/glfw/include)
if(NOT GLFW_LIBRARY OR NOT GLFW_INCLUDE_DIR)
    message(FATAL_ERROR "GLFW not found -- install via 'brew install glfw' (macOS) or 'sudo apt-get install libglfw3-dev' (Linux)")
endif()

add_library(awake-window SHARED
        ../src/main/cpp/GlfwWindow_jni.cpp
        ../src/main/cpp/GlfwWindow_native.cpp)

target_include_directories(awake-window PRIVATE
        ${JNI_INCLUDE_DIRS}
        ${GLFW_INCLUDE_DIR}
        # jni-utils.h, shared with the generated JNI bindings.
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../tools/jni-binding-generator/scripts)

target_link_libraries(awake-window ${GLFW_LIBRARY})

# macOS: tell a trackpad from a wheel (NSEvent.hasPreciseScrollingDeltas), which GLFW does not
# report -- see GlfwWindow_native_macos.mm.
if(APPLE)
    enable_language(OBJCXX)
    target_sources(awake-window PRIVATE ../src/main/cpp/GlfwWindow_native_macos.mm)
    set_source_files_properties(../src/main/cpp/GlfwWindow_native_macos.mm PROPERTIES COMPILE_OPTIONS "-fobjc-arc")
    target_link_libraries(awake-window "-framework AppKit")
endif()
