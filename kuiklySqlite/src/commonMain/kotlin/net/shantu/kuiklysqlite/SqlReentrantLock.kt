package net.shantu.kuiklysqlite

/**
 * 可重入互斥锁（各端 actual：Android 为 ReentrantLock，Native 为 pthread 递归锁）。
 *
 * 用于把同一个数据库连接上的「多条语句组成的操作」串行化：
 * 例如 INSERT 后读取 lastInsertId、事务从 BEGIN 到 COMMIT 期间，其他线程必须等待，
 * 否则别的线程的写入会被卷进当前事务，或读到别人的 lastInsertId。
 */
expect class SqlReentrantLock() {
    fun lock()
    fun unlock()
}

inline fun <R> SqlReentrantLock.withLock(block: () -> R): R {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
