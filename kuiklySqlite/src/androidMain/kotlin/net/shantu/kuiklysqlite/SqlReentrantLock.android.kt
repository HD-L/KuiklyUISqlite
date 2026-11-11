package net.shantu.kuiklysqlite

import java.util.concurrent.locks.ReentrantLock

actual class SqlReentrantLock actual constructor() {
    private val delegate = ReentrantLock()

    actual fun lock() = delegate.lock()

    actual fun unlock() = delegate.unlock()
}
