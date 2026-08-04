cmake_minimum_required(VERSION 3.22.1)
project(splitproxy_mobile)

# Do not link the JNI bridge directly against tun2proxy.
# Older Android loaders can fail the Java class initialization before we can
# show the real dlopen error. The bridge opens libtun2proxy.so explicitly.
add_library(splitproxy_jni SHARED src/main/cpp/native_bridge.cpp)

find_library(log-lib log)
find_library(dl-lib dl)
target_link_libraries(splitproxy_jni ${log-lib} ${dl-lib})
