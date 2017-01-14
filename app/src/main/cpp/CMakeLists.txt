cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED main.cpp)
set_property(TARGET native-lib PROPERTY CXX_STANDARD 14)
