package net.shantu.kuiklysqlite

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

actual object SqlWorker {

    private val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "KuiklySqlite-Worker").apply { isDaemon = true }
        }
    }

    actual fun post(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (e: Throwable) {
                // 兜底：正常情况下 SqlDriver.async 已把异常交给 onError，这里只防止线程被意外终止
                System.err.println("[KuiklySqlite] Uncaught error in SqlWorker: $e")
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Use SqlDriver.async(), which reports failures via onError")
    actual fun <T> execute(block: () -> T, callback: (T) -> Unit) {
        post { callback(block()) }
    }
}
