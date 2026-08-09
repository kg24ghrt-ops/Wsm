cmake_minimum_required(VERSION 3.22)
project(editor)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(editor SHARED
    editor.cpp
    lexer.cpp
    jni_bridge.cpp
)

target_include_directories(editor PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(editor
    log
)