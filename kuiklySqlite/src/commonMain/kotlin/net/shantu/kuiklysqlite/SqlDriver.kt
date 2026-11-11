package net.shantu.kuiklysqlite

/**
 * 数据库连接接口 (Connection)，对应 C 语言的 sqlite3* 指针。
 *
 * 线程模型：
 * - 一个 Driver 只持有一个连接，可被任意线程同步调用；
 * - 由多条语句组成的操作（DAO 方法、事务）通过 [withConnection] / [transaction] 持有连接锁，
 *   期间其他线程等待，保证不会互相卷入对方的事务或读到对方的 lastInsertId；
 * - 异步调用见 [async]：统一在 [SqlWorker] 的单个后台线程上按提交顺序执行，
 *   回调经 [callbackDispatcher] 投递（Kuikly 中应投递回 Kuikly 线程）。
 */
interface SqlDriver : AutoCloseable {

    /** 日志记录器（可选） */
    var logger: SqlLogger?

    /**
     * 异步回调的默认投递方式。默认 [SqlCallbackDispatcher.WORKER]（直接在后台线程回调）。
     * Kuikly 页面应改为投递回 Kuikly 线程，见 README「在 Kuikly 中使用异步 API」。
     */
    var callbackDispatcher: SqlCallbackDispatcher

    override fun close()

    /** 简单执行：不带参数，不返回结果 (例如: CREATE TABLE) */
    fun execute(sql: String)

    /**
     * 预编译：带参数，或者需要查询结果。
     * 注意：从 prepare 到 step/close 是多步操作，跨线程使用时请放在 [withConnection] 中。
     */
    fun prepare(sql: String): SqlStatement

    /** 获取受影响的行数 (INSERT/UPDATE/DELETE)，需与写语句在同一个 [withConnection] 内调用 */
    fun getChanges(): Int

    /** 获取最后插入的 RowId，需与 INSERT 在同一个 [withConnection] 内调用 */
    fun getLastInsertId(): Long

    // --- 连接锁 ---

    /** 获取连接锁（可重入）。一般通过 [withConnection] 使用。 */
    fun lock()

    /** 释放连接锁 */
    fun unlock()

    // --- 事务（支持嵌套：外层 BEGIN/COMMIT，内层 SAVEPOINT） ---

    /** 开始事务并持有连接锁，直到对应的 [endTransaction] / [rollbackTransaction] */
    fun beginTransaction()

    /** 提交当前层事务并释放连接锁；提交失败会回滚该层后抛出 [SqlTransactionException] */
    fun endTransaction()

    /** 回滚当前层事务并释放连接锁 */
    fun rollbackTransaction()

    /**
     * 事务执行辅助函数：自动开启，成功提交，失败回滚。
     * 可嵌套：内层失败只回滚内层（SAVEPOINT），异常继续向外抛出。
     */
    fun <R> transaction(block: () -> R): R {
        beginTransaction()
        val result = try {
            block()
        } catch (e: Throwable) {
            rollbackTransaction()
            if (e is SqlException) throw e
            throw SqlTransactionException("Transaction failed", e)
        }
        endTransaction()
        return result
    }

    // --- 观察者支持 (Observer Support) ---

    /** 注册表变更监听器 */
    fun addListener(tableName: String, listener: () -> Unit)

    /** 移除表变更监听器 */
    fun removeListener(tableName: String, listener: () -> Unit)

    /** 通知表发生了变更，通常由 Dao 在写操作成功后调用 */
    fun notifyListeners(tableName: String)
}

/**
 * 在持有连接锁的情况下执行 [block]（可重入）。
 * 生成的 DAO 方法都包在其中；手写「prepare → step → getLastInsertId」这类多步操作时也应使用。
 */
inline fun <R> SqlDriver.withConnection(block: () -> R): R {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * 各端 Driver 的公共实现：连接锁、嵌套事务、监听器。
 * 子类只需实现 SQLite 原语（execute / prepare / changes / lastInsertId / close）。
 */
abstract class BaseSqlDriver : SqlDriver {

    private val connectionLock = SqlReentrantLock()
    private val listenerLock = SqlReentrantLock()

    /** 当前事务嵌套层数，只在持有 [connectionLock] 时读写 */
    private var transactionDepth = 0

    /** 写时复制，通知时不持锁 */
    private var listeners: Map<String, List<() -> Unit>> = emptyMap()

    override var logger: SqlLogger? = null

    override var callbackDispatcher: SqlCallbackDispatcher = SqlCallbackDispatcher.WORKER

    override fun lock() = connectionLock.lock()

    override fun unlock() = connectionLock.unlock()

    override fun beginTransaction() {
        lock()
        try {
            val sql = if (transactionDepth == 0) "BEGIN TRANSACTION" else "SAVEPOINT ${savepoint(transactionDepth)}"
            logger?.log(sql)
            execute(sql)
            transactionDepth++
        } catch (e: Throwable) {
            unlock()
            logger?.error("Failed to begin transaction", e)
            throw SqlTransactionException("Failed to begin transaction", e)
        }
    }

    override fun endTransaction() {
        check(transactionDepth > 0) { "endTransaction() called without beginTransaction()" }
        val level = transactionDepth - 1
        try {
            val sql = if (level == 0) "COMMIT" else "RELEASE ${savepoint(level)}"
            logger?.log(sql)
            execute(sql)
        } catch (e: Throwable) {
            logger?.error("Failed to commit transaction", e)
            rollbackLevel(level)
            throw SqlTransactionException("Failed to commit transaction", e)
        } finally {
            transactionDepth = level
            unlock()
        }
    }

    override fun rollbackTransaction() {
        check(transactionDepth > 0) { "rollbackTransaction() called without beginTransaction()" }
        val level = transactionDepth - 1
        try {
            rollbackLevel(level)
        } finally {
            transactionDepth = level
            unlock()
        }
    }

    private fun rollbackLevel(level: Int) {
        try {
            if (level == 0) {
                logger?.log("ROLLBACK")
                execute("ROLLBACK")
            } else {
                val name = savepoint(level)
                logger?.log("ROLLBACK TO $name")
                execute("ROLLBACK TO $name")
                execute("RELEASE $name")
            }
        } catch (e: Throwable) {
            // 回滚失败时原始异常更重要，这里只记录
            logger?.error("Failed to rollback transaction", e)
        }
    }

    private fun savepoint(level: Int) = "kuikly_sp_$level"

    override fun addListener(tableName: String, listener: () -> Unit) {
        listenerLock.withLock {
            val list = listeners[tableName] ?: emptyList()
            listeners = listeners + (tableName to (list + listener))
        }
    }

    override fun removeListener(tableName: String, listener: () -> Unit) {
        listenerLock.withLock {
            val list = listeners[tableName] ?: return
            val newList = list - listener
            listeners = if (newList.isEmpty()) listeners - tableName else listeners + (tableName to newList)
        }
    }

    override fun notifyListeners(tableName: String) {
        val callbacks = listenerLock.withLock { listeners[tableName] } ?: return
        callbacks.forEach { listener ->
            try {
                listener()
            } catch (e: Throwable) {
                logger?.error("Table listener failed: $tableName", e)
            }
        }
    }
}
