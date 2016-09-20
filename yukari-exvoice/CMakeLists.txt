# for CLion Project
# NOT build script
cmake_minimum_required(VERSION 3.5)
project(yukari_exvoice)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c99")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

include_directories(
        ${PROJECT_SOURCE_DIR}/mruby/include
        $ENV{JAVA_HOME}/include
        $ENV{JAVA_HOME}/include/win32
        $ENV{ANDROID_NDK_HOME}/platforms/android-24/arch-arm/usr/include
)

file(GLOB jni_sources "src/main/jni/*.c")
file(GLOB jni_headers "src/main/jni/*.h")

set(SOURCE_FILES ${jni_sources} ${jni_headers})

add_executable(yukari_exvoice ${SOURCE_FILES})