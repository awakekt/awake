# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GLFW for the desktop natives (libawake-window, libawake-vulkan): sets GLFW_LIBRARY, to link, and
# GLFW_INCLUDE_DIR.
#
# macOS and Linux link the system's shared GLFW (`brew install glfw`, `libglfw3-dev`). Windows has
# no package to link, so GLFW 3.4 is fetched, checked against its release's hash, and built as
# glfw3.dll, which awake:engine:window's jar ships. One DLL, as the shared library is elsewhere, so
# both natives share one GLFW: a static GLFW would give each its own, and a surface made through
# one would not know the window the other opened.
if(WIN32)
    include(FetchContent)
    set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(GLFW_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(GLFW_BUILD_DOCS OFF CACHE BOOL "" FORCE)
    set(GLFW_INSTALL OFF CACHE BOOL "" FORCE)
    set(GLFW_LIBRARY_TYPE SHARED CACHE STRING "" FORCE)
    # The C runtime linked in, as the natives link theirs (DESKTOP_NATIVE_DEFINES in build-logic).
    set(USE_MSVC_RUNTIME_LIBRARY_DLL OFF CACHE BOOL "" FORCE)
    FetchContent_Declare(glfw
            URL https://github.com/glfw/glfw/releases/download/3.4/glfw-3.4.zip
            URL_HASH SHA256=b5ec004b2712fd08e8861dc271428f048775200a2df719ccf575143ba749a3e9
            DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
    FetchContent_MakeAvailable(glfw)
    set(GLFW_LIBRARY glfw)
    set(GLFW_INCLUDE_DIR ${glfw_SOURCE_DIR}/include)
else()
    find_library(GLFW_LIBRARY NAMES glfw glfw3 PATHS /opt/homebrew/opt/glfw/lib /usr/local/opt/glfw/lib)
    find_path(GLFW_INCLUDE_DIR GLFW/glfw3.h PATHS /opt/homebrew/opt/glfw/include /usr/local/opt/glfw/include)
    if(NOT GLFW_LIBRARY OR NOT GLFW_INCLUDE_DIR)
        message(FATAL_ERROR "GLFW not found -- install via 'brew install glfw' (macOS) or 'sudo apt-get install libglfw3-dev' (Linux)")
    endif()
endif()
