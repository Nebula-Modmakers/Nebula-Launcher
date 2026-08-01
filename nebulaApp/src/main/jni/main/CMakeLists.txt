project(main)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED True)

set(SOURCE_FILES main.cpp)

add_library(main SHARED
        main.cpp
)

target_link_libraries(
        main
        PRIVATE
        android
        log
)
