cmake_minimum_required(VERSION 3.4.1)

find_library(lib-gles GLESv3)

add_library(test1 SHARED test1.cpp)
target_link_libraries(test1 ${lib-gles})
