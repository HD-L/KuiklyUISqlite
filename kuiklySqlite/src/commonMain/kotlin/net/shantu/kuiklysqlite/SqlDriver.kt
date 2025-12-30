package net.shantu.kuiklysqlite

/**
 * 2. 数据库连接接口 (Connection)
 * 对应 C 语言的 sqlite3* 指针
 */
interface SqlDriver : AutoCloseable {
    
    // 日志记录器（可选）
    var logger: SqlLogger?

    // 开启/关闭
    // (createDriver 时通常已经 open 了，这里保留 close)
    override fun close()

    // 简单执行：不带参数，不返回结果 (例如: CREATE TABLE, BEGIN TRANSACTION)
    fun execute(sql: String)

    // 预编译执行：带参数，或者需要查询结果 (例如: SELECT, INSERT with params)
    // 返回一个 Statement 对象，后续的操作都在 Statement 上进行
    fun prepare(sql: String): SqlStatement

    // 获取受影响的行数 (INSERT/UPDATE/DELETE)
    fun getChanges(): Int

    // 获取最后插入的 RowId
    fun getLastInsertId(): Long
    
    // 事务支持
    fun beginTransaction() {
        logger?.log("BEGIN TRANSACTION")
        try {
            execute("BEGIN TRANSACTION")
        } catch (e: Exception) {
            logger?.error("Failed to begin transaction", e)
            throw SqlTransactionException("Failed to begin transaction", e)
        }
    }
    
    fun endTransaction() {
        logger?.log("COMMIT")
        try {
            execute("COMMIT")
        } catch (e: Exception) {
            logger?.error("Failed to commit transaction", e)
            throw SqlTransactionException("Failed to commit transaction", e)
        }
    }
    
    fun rollbackTransaction() {
        logger?.log("ROLLBACK")
        try {
            execute("ROLLBACK")
        } catch (e: Exception) {
            logger?.error("Failed to rollback transaction", e)
            // Rollback failure is critical but we usually just log it as the original error is more important
        }
    }

    /**
     * 事务执行辅助函数
     * 自动管理事务生命周期：自动开启，成功提交，失败回滚。
     */
    fun <R> transaction(block: () -> R): R {
        beginTransaction()
        try {
            val result = block()
            endTransaction()
            return result
        } catch (e: Throwable) {
            rollbackTransaction()
            // 如果已经是自定义异常，直接抛出；否则包装
            if (e is SqlException) throw e
            throw SqlTransactionException("Transaction failed", e)
        }
    }

    // --- 观察者支持 (Observer Support) ---

    /**
     * 注册表变更监听器
     * @param tableName 表名
     * @param listener 回调函数
     */
    fun addListener(tableName: String, listener: () -> Unit)

    /**
     * 移除表变更监听器
     */
    fun removeListener(tableName: String, listener: () -> Unit)

    /**
     * 通知表发生了变更
     * 通常由 Dao 在写操作成功后调用
     */
    fun notifyListeners(tableName: String)
}
