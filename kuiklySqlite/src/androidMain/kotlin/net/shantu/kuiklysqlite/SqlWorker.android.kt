package net.shantu.kuiklysqlite

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

actual object SqlWorker {
    private val executor: ExecutorService by lazy {
        val cpuCount = Runtime.getRuntime().availableProcessors()
        val poolSize = (cpuCount - 1).coerceIn(2, 4)

        Executors.newFixedThreadPool(poolSize, object : ThreadFactory {
            private val count = AtomicInteger(0)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "KuiklySqlite-Worker-${count.getAndIncrement()}")
            }
        })
    }

    actual fun <T> execute(block: () -> T, callback: (T) -> Unit) {
        executor.submit {
            try {
                val result = block()
                callback(result)
            } catch (e: Exception) {
                // TODO: Handle exception or propagate via callback?
                // Currently SqlWorker interface doesn't support error callback,
                // assuming block handles its own try-catch or we crash.
                e.printStackTrace()
            }
        }
    }
}
