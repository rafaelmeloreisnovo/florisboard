cmake_minimum_required(VERSION 3.22)

project(florisboard)

if(CMAKE_HOST_WIN32)
    set(USER_HOME "$ENV{USERPROFILE}")
else()
    set(USER_HOME "$ENV{HOME}")
endif()

# Find Rust toolchain with better error messages
find_program(RUSTUP_EXECUTABLE NAMES rustup HINTS "${USER_HOME}/.cargo/bin")
find_program(CARGO_EXECUTABLE NAMES cargo HINTS "${USER_HOME}/.cargo/bin")
if(NOT RUSTUP_EXECUTABLE OR NOT CARGO_EXECUTABLE)
    message(FATAL_ERROR "Rust toolchain not found on this system. Please install Rust toolchain from https://rustup.rs/, then try again.")
else()
    message(STATUS "Using Rust toolchain: ${RUSTUP_EXECUTABLE}")
    message(STATUS "Using Cargo: ${CARGO_EXECUTABLE}")
endif()

### FlorisBoard Native Library ###

add_library(fl_native SHARED src/lib.c)

# Determine target architecture and Rust target triple
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(ANDROID_TARGET "armv7a-linux-androideabi")
    set(RUST_TARGET "armv7-linux-androideabi")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(ANDROID_TARGET "aarch64-linux-android")
    set(RUST_TARGET "aarch64-linux-android")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(ANDROID_TARGET "i686-linux-android")
    set(RUST_TARGET "i686-linux-android")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(ANDROID_TARGET "x86_64-linux-android")
    set(RUST_TARGET "x86_64-linux-android")
else()
    message(FATAL_ERROR "Unsupported ABI: ${CMAKE_ANDROID_ARCH_ABI}. Supported ABIs: arm64-v8a, armeabi-v7a, x86, x86_64")
endif()

message(STATUS "Building for ABI: ${CMAKE_ANDROID_ARCH_ABI}")
message(STATUS "Android target: ${ANDROID_TARGET}")
message(STATUS "Rust target: ${RUST_TARGET}")

get_filename_component(LLVM_TOOLCHAIN ${CMAKE_C_COMPILER} DIRECTORY)

set(FL_NATIVE_RUST_PATH "${CMAKE_CURRENT_SOURCE_DIR}/target/${RUST_TARGET}/release/libfl_native_rust.a")

add_custom_target(
    setup_rust_target ALL
    COMMAND ${RUSTUP_EXECUTABLE} target add ${RUST_TARGET}
)
add_custom_target(
    fl_native_rust_build ALL
    COMMAND ${CARGO_EXECUTABLE} rustc --release --locked --target ${RUST_TARGET} --
        -C linker="${LLVM_TOOLCHAIN}/${ANDROID_TARGET}${CMAKE_ANDROID_API}-clang"
    DEPENDS setup_rust_target
    BYPRODUCTS ${FL_NATIVE_RUST_PATH}
    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
)

add_dependencies(fl_native fl_native_rust_build)

# Link with proper symbol resolution to prevent crashes from duplicate symbols
# Using --whole-archive only for the Rust static library to ensure all symbols are included
# Proper symbol scoping with --no-whole-archive improves runtime stability
# SHA1 build-id enables better crash stack trace analysis on ARM64 and all Android versions
target_link_libraries(fl_native
    android log -Wl,--whole-archive ${FL_NATIVE_RUST_PATH} -Wl,--no-whole-archive -Wl,--build-id=sha1
)
