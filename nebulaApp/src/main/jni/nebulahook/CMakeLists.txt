project(nebulahook)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)
set(LIB_FOLDER ${CMAKE_SOURCE_DIR}/../jniLibs/${CMAKE_ANDROID_ARCH_ABI})

find_package(lsplant REQUIRED CONFIG)
find_package(xdl REQUIRED CONFIG)

add_library(nebulahook SHARED nebulahook.cpp)

target_include_directories(nebulahook PRIVATE ../fusion/include)
target_link_libraries(nebulahook PRIVATE
        lsplant::lsplant
        xdl::xdl
        ${LIB_FOLDER}/libdobby.so
        android
        log)
