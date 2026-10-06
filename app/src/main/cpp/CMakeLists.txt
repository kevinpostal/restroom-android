cmake_minimum_required(VERSION 3.22)
project(restroomjni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(RESTROOM_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../core core)

add_library(restroomjni SHARED jni.cpp)
target_link_libraries(restroomjni PRIVATE restroomcore log)
target_compile_options(restroomjni PRIVATE -Wall -Wextra)
