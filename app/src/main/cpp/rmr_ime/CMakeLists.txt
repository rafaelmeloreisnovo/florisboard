cmake_minimum_required(VERSION 3.22)
project(rmr_ime_probe C)
set(RMR_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../rmr_lowlevel")
add_library(rmr_ime_probe SHARED
    "${CMAKE_CURRENT_LIST_DIR}/rmr_ime_jni.c"
    "${RMR_ROOT}/src/rmr_ime.c")
target_include_directories(rmr_ime_probe PRIVATE "${RMR_ROOT}/include")
target_compile_features(rmr_ime_probe PRIVATE c_std_11)
target_compile_options(rmr_ime_probe PRIVATE -Wall -Wextra -Werror -ffreestanding -fno-builtin -fvisibility=hidden)
target_link_options(rmr_ime_probe PRIVATE -Wl,--no-undefined)
