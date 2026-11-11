package net.shantu.kuiklysqlite

/**
 * 异步回调的投递方式：决定 onSuccess / onError 在哪个线程执行。
 *
 * 本库不依赖 Kuikly，Kuikly 页面用 kuiklyx 协程库把回调送回 Kuikly 线程（之后才能改 observable）：
 * ```kotlin
 * val toKuikly = SqlCallbackDispatcher { task ->
 *     KuiklyContextScheduler.runOnKuiklyThread(pagerId) { cancel -> if (!cancel) task() }
 * }
 * ```
 */
fun interface SqlCallbackDispatcher {
    fun dispatch(task: () -> Unit)

    companion object {
        /** 直接在后台线程回调（默认值，与 1.0.x 行为一致） */
        val WORKER: SqlCallbackDispatcher = SqlCallbackDispatcher { it() }
    }
}

/**
 * 在后台线程执行 [block]（持有连接锁），完成后经 [dispatcher] 回调。
 *
 * - 成功 → [onSuccess]；失败 → [onError]，保证二者之一一定被调用；
 * - 未提供 [onError] 时异常交给 [SqlDriver.logger]（无 logger 则打印），不会静默丢失；
 * - 所有异步任务在同一后台线程按提交顺序执行。
 *
 * ```kotlin
 * driver.async({ userDao.selectAll() }, onError = { showError(it) }) { users -> render(users) }
 * ```
 */
fun <T> SqlDriver.async(
    block: () -> T,
    onError: ((Throwable) -> Unit)? = null,
    dispatcher: SqlCallbackDispatcher = callbackDispatcher,
    onSuccess: (T) -> Unit,
) {
    val driver = this
    SqlWorker.post {
        val outcome = runCatching { driver.withConnection(block) }
        dispatcher.dispatch {
            outcome.fold(
                onSuccess = onSuccess,
                onFailure = { error ->
                    if (onError != null) {
                        onError(error)
                    } else {
                        driver.reportUnhandled(error)
                    }
                },
            )
        }
    }
}

/** 异步事务：[block] 在事务中执行，失败自动回滚后交给 [onError] */
fun <T> SqlDriver.transactionAsync(
    block: () -> T,
    onError: ((Throwable) -> Unit)? = null,
    dispatcher: SqlCallbackDispatcher = callbackDispatcher,
    onSuccess: (T) -> Unit,
) {
    async({ transaction(block) }, onError, dispatcher, onSuccess)
}

private fun SqlDriver.reportUnhandled(error: Throwable) {
    val log = logger
    if (log != null) {
        log.error("Async database task failed (no onError provided)", error)
    } else {
        println("[KuiklySqlite] Async database task failed (no onError provided): $error")
    }
}
