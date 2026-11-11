package net.shantu.kuiklysqlite

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t

/**
 * pthread 递归锁。iOS 与鸿蒙（musl）都提供 POSIX 线程接口。
 * 锁与连接同生命周期，数量极少，不主动释放。
 */
@OptIn(ExperimentalForeignApi::class)
actual class SqlReentrantLock actual constructor() {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>()

    init {
        memScoped {
            val attr = alloc<pthread_mutexattr_t>()
            pthread_mutexattr_init(attr.ptr)
            pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE.convert())
            pthread_mutex_init(mutex.ptr, attr.ptr)
            pthread_mutexattr_destroy(attr.ptr)
        }
    }

    actual fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    actual fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }
}
