cmake_minimum_required(VERSION 3.22)
project(restroomcore LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(RESTROOM_BUILD_TESTS "Build the GoogleTest suite (host only)" OFF)

include(FetchContent)
FetchContent_Declare(nlohmann_json
    URL https://github.com/nlohmann/json/releases/download/v3.11.3/json.tar.xz
    URL_HASH SHA256=d6c65aca6b1ed68e7a182f4757257b107ae403032760ed6ef121c9d55e81757d)
set(JSON_BuildTests OFF CACHE INTERNAL "")
set(JSON_Install OFF CACHE INTERNAL "")
FetchContent_MakeAvailable(nlohmann_json)

add_library(restroomcore STATIC
    src/access.cpp
    src/geo.cpp
    src/json.cpp
    src/tilecache.cpp)
target_include_directories(restroomcore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(restroomcore PRIVATE nlohmann_json::nlohmann_json)
target_compile_options(restroomcore PRIVATE -Wall -Wextra)
set_target_properties(restroomcore PROPERTIES POSITION_INDEPENDENT_CODE ON)

if(RESTROOM_BUILD_TESTS)
    FetchContent_Declare(googletest
        URL https://github.com/google/googletest/releases/download/v1.15.2/googletest-1.15.2.tar.gz
        URL_HASH SHA256=7b42b4d6ed48810c5362c265a17faebe90dc2373c885e5216439d37927f02926)
    set(INSTALL_GTEST OFF CACHE INTERNAL "")
    FetchContent_MakeAvailable(googletest)
    enable_testing()
    add_executable(restroomcore_tests
        tests/access_test.cpp
        tests/parsers_test.cpp
        tests/cell_rank_test.cpp
        tests/tilecache_test.cpp)
    target_link_libraries(restroomcore_tests PRIVATE restroomcore nlohmann_json::nlohmann_json gtest_main)
    include(GoogleTest)
    gtest_discover_tests(restroomcore_tests)
endif()
