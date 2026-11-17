
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("awake-vulkan")

# Add the 'common/utils' subdirectory
add_subdirectory(vulkan-kotlin)
# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        awake-vulkan.cpp
        # Generated via jni-binding-generator (see D10 in docs/decisions/), not the
        # legacy awake-vulkan-generator that backs the rest of this target's sources.
        # See that file's own header comment for the hand-edit workflow.
        generated/com_awakekt_awake_vulkan_gen_VulkanBuffers_jni.gen.cpp
        generated/com_awakekt_awake_vulkan_gen_VulkanDescriptors_jni.gen.cpp
        generated/com_awakekt_awake_vulkan_gen_VulkanImages_jni.gen.cpp
        generated/com_awakekt_awake_vulkan_gen_VulkanTextureRegions_jni.gen.cpp
        VulkanImages_native.cpp
        generated/com_awakekt_awake_vulkan_gen_VulkanQueries_jni.gen.cpp
        VulkanQueries_native.cpp)

# 16 KB LOAD alignment, required by Google Play for Android 15+ (NDK r26 defaults to 4 KB).
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

# jni-utils.h (vendored jni-binding-generator helper header the generated file needs).
# Path depth: cpp -> main -> src -> vulkan -> backend -> awake -> repo root (6 levels) --
# this module lives at awake/backend/vulkan/src/main/cpp since the Phase 0 nested-module
# rename (previously awake-backend-vulkan/src/main/cpp, 4 levels).
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../../tools/jni-binding-generator/scripts)

find_library(log-lib log)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        vulkan_kotlin
        android
        log)

# Swappy, Android's frame pacer (#1699), linked from games-frame-pacing's static library without
# prefab: prefab refuses a library built for c++_shared in a c++_static build, and Swappy's API is
# C, so no C++ runtime type crosses between the two. android-native's extractSwappy task unpacks
# the pinned AAR and passes its directory here; scripts/swappy-symbols.sh checks the result.
if(ANDROID)
    if(NOT AWAKE_SWAPPY_DIR)
        message(FATAL_ERROR "AWAKE_SWAPPY_DIR is not set: run android-native's extractSwappy task")
    endif()
    set(SWAPPY_LIBRARY ${AWAKE_SWAPPY_DIR}/libs/android.${ANDROID_ABI}/libswappy_static.a)
    if(NOT EXISTS ${SWAPPY_LIBRARY})
        message(FATAL_ERROR "No Swappy library for ${ANDROID_ABI} at ${SWAPPY_LIBRARY}")
    endif()
    add_library(swappy_static STATIC IMPORTED)
    set_target_properties(swappy_static PROPERTIES IMPORTED_LOCATION ${SWAPPY_LIBRARY})
    target_sources(${CMAKE_PROJECT_NAME} PRIVATE SwappyVk_native.cpp)
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${AWAKE_SWAPPY_DIR}/include)
    target_link_libraries(${CMAKE_PROJECT_NAME} swappy_static)
    # Swappy's symbols stay inside this library: exported, they could bind to another copy an app
    # loads (games-frame-pacing's own libswappy.so), or it to ours.
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,--exclude-libs,libswappy_static.a")
endif()
