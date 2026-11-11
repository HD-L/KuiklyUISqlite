package net.shantu.kuiklysqlite

/**
 * 跨平台后台任务执行器：进程内**单个**后台线程，按提交顺序（FIFO）执行。
 *
 * 单线程是有意为之：所有 Driver 共用一个 SQLite 连接，串行执行既避免锁竞争，
 * 也保证「先 insertAsync 再 selectAllAsync」一定能读到刚插入的数据。
 * 任务抛出的异常会被捕获并打印，不会让后台线程退出。
 */
expect object SqlWorker {

    /** 把任务投递到后台线程执行 */
    fun post(task: () -> Unit)

    /**
     * 旧版 API，仅为兼容 1.0.x 生成的 DAO 保留：异常时 callback 不会被调用。
     * 请改用 [SqlDriver.async]，它会把异常交给 onError。
     */
    @Deprecated("Use SqlDriver.async(), which reports failures via onError")
    fun <T> execute(block: () -> T, callback: (T) -> Unit)
}
