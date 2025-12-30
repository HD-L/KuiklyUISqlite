package net.shantu.kuiklysqlite

/**
 * 跨平台后台任务执行器
 */
expect object SqlWorker {
    /**
     * 在后台线程执行任务
     * @param block 需要执行的任务，返回结果类型 T
     * @param callback 任务完成后的回调，在任意线程调用（通常是后台线程，除非特定实现调度回主线程）
     */
    fun <T> execute(block: () -> T, callback: (T) -> Unit)
}
