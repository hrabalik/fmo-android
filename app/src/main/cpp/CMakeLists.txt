cmake_minimum_required(VERSION 3.4.1)

find_package(OpenCV REQUIRED core videoio)
include_directories(${OpenCV_INCLUDE_DIRS})
find_library(lib-gles GLESv3)

add_library(test1 SHARED
    assert.hpp
    assert.cpp
    stats.hpp
    stats.cpp
    java_interface.hpp
    java_interface.cpp
    java_classes.hpp
    java_classes.cpp
    ocvrec.hpp
    ocvrec.cpp
    ocvrec2.cpp
    recording2.cpp)
target_link_libraries(test1 ${lib-gles} ${OpenCV_LIBS})
