package net.shantu.kuiklysqlite

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

@OptIn(ObsoleteWorkersApi::class)
actual object SqlWorker {

    // Worker 内部是单线程 FIFO 队列，正好满足串行执行的要求（新内存模型下无需 freeze）
    private val worker: Worker by lazy { Worker.start(name = "KuiklySqlite-Worker") }

    actual fun post(task: () -> Unit) {
        worker.execute(TransferMode.SAFE, { task }) { job ->
            try {
                job()
            } catch (e: Throwable) {
                // 兜底：正常情况下 SqlDriver.async 已把异常交给 onError，这里只防止 Worker 被意外终止
                println("[KuiklySqlite] Uncaught error in SqlWorker: $e")
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Use SqlDriver.async(), which reports failures via onError")
    actual fun <T> execute(block: () -> T, callback: (T) -> Unit) {
        post { callback(block()) }
    }
}
