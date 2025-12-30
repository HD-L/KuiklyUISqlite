package net.shantu.kuiklysqlite


import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze

//actual object SqlWorker {
//    // 创建一个单线程 Worker 用于数据库操作（SQLite 往往在单线程写时更安全高效）
//    // 注意：Worker 是 Kotlin Native 的旧并发原语，但仍广泛使用。
//    // 如果是新版 Kotlin Native (1.9+)，建议开启新内存模型。
//    private val worker = Worker.start(name = "KuiklySqlite-Worker")
//
//    actual fun <T> execute(block: () -> T, callback: (T) -> Unit) {
//        // freeze() 在新内存模型下通常是空操作，但在旧模型下是必须的。
//        // 为了兼容性，我们假设用户可能开启了新内存模型，但也可能没开。
//        // execute 接收一个 producer，我们在 producer 中返回 block
//        // 然后 process 中执行 block。
//
//        worker.execute(TransferMode.SAFE, { block.freeze() }) { task ->
//            task()
//        }.consume { result ->
//            // consume 在调用 execute 的线程（即调用者线程，可能是主线程）或者 Future 被消耗的线程执行？
//            // 不，Future.consume 是阻塞的或者用于获取结果。
//            // 实际上 Worker.execute 返回 Future。
//            // 我们希望 callback 在任务完成后执行。
//            // 由于 Future 没有异步回调 API，我们需要在 Worker 内部或者通过另一个机制来回调。
//            // 但 Worker API 本身是基于 Future 的。
//            // 如果我们想实现异步回调，通常是在 Worker 内部做完后，再调度回原线程？
//            // 但 Kotlin Native 没有通用的 "Main Thread Dispatcher" 除非引用 Coroutines。
//            // 既然我们要移除 Coroutines，我们只能：
//            // 1. 阻塞等待（违背初衷）
//            // 2. 忽略回调（不满足需求）
//            // 3. 在 Worker 线程执行 callback (目前最可行，但 callback 注意线程安全)
//
//            // 修正：上面的 execute 返回 Future。
//            // 如果要异步执行且不阻塞，我们不能在主线程调用 result.consume。
//            // 实际上，Worker 没有内置的 "执行完后运行这个 lambda" 的链式调用。
//            // 我们可以把 callback 也传给 Worker 执行。
//            // 即：Worker 执行 { block(); callback() }。
//            // 这样 callback 会在 Worker 线程执行。
//        }
//    }
//
//    // 重新实现 execute，将 callback 放入 worker 执行
//    fun <T> executeInternal(block: () -> T, callback: (T) -> Unit) {
//         worker.execute(TransferMode.SAFE, { Pair(block, callback).freeze() }) { (blk, cb) ->
//             val result = blk()
//             cb(result)
//         }
//    }
//}

// 实际上由于泛型擦除和 freeze 问题，传递 Pair 可能复杂。
// 简化实现：
actual object SqlWorker {
    private val worker = Worker.start(name = "KuiklySqlite-Worker")

    actual fun <T> execute(block: () -> T, callback: (T) -> Unit) {
        worker.execute(TransferMode.SAFE, { Pair(block, callback) }) { (task, cb) ->
            try {
                val result = task()
                cb(result)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}