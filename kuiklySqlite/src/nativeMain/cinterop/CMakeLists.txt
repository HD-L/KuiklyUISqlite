cmake_minimum_required(VERSION 3.10.2)

# 库名设为 "sqlite3" (生成的将是 libsqlite3.so)
project("sqlite3")

# 1. 设置 C 源码路径
# ${CMAKE_SOURCE_DIR} 是当前文件所在目录
# 我们指向 nativeMain/c 目录
set(C_SOURCE_DIR ${CMAKE_SOURCE_DIR}/../c)

# 2. 编译动态库
add_library(
        sqlite3
        SHARED
        # 直接引用共用的 C 文件
        ${C_SOURCE_DIR}/sqlite3.c
)

# 3. 包含头文件路径
target_include_directories(
        sqlite3 PRIVATE
        ${C_SOURCE_DIR}
)