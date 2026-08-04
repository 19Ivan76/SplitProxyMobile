cmake_minimum_required(VERSION 3.22.1)
project(splitproxy_mobile)

add_library(tun2proxy SHARED IMPORTED)
set_target_properties(tun2proxy PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_LIST_DIR}/src/main/jniLibs/${ANDROID_ABI}/libtun2proxy.so")

add_library(splitproxy_jni SHARED src/main/cpp/native_bridge.cpp)

find_library(log-lib log)
target_link_libraries(splitproxy_jni tun2proxy ${log-lib})
