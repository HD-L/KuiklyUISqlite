package net.shantu.kuiklysqlite

import kotlinx.cinterop.*
import net.shantu.kuiklysqlite.* // 引用 cinterop 生成的包
import cnames.structs.sqlite3_stmt
/**
 * 辅助常量：SQLITE_TRANSIENT
 * 告诉 SQLite：这个数据指针由你（SQLite）自己复制一份，不要依赖调用者的内存。
 * 因为 Kotlin 的对象（String/ByteArray）可能会被 GC 移动或回收。
 * 在 C 中它是 (void*)-1
 */
//@kotlinx.cinterop.ExperimentalForeignApi
//private val SQLITE_TRANSIENT = (-1L).toCPointer<CPointed>()
//private val SQLITE_TRANSIENT = (-1L).toCPointer()
@kotlinx.cinterop.ExperimentalForeignApi
internal class NativeSqlStatement(
    private val stmtPointer: CPointer<sqlite3_stmt>,
    private val driver: SqlDriver? = null // Add driver reference for logging (optional to maintain compatibility if needed, but better required)
) : SqlStatement {

    // Helper to access logger safely
    private val logger: SqlLogger?
        get() = driver?.logger

    // =========================================================================
    // Bind Methods (输入)
    // =========================================================================

    override fun bindLong(index: Int, value: Long?) {
        logger?.log("  BIND [$index] (Long) -> $value")
        if (value == null) {
            bindNull(index)
        } else {
            val rc = sqlite3_bind_int64(stmtPointer, index, value)
            checkBindError(rc, index)
        }
    }

    override fun bindDouble(index: Int, value: Double?) {
        logger?.log("  BIND [$index] (Double) -> $value")
        if (value == null) {
            bindNull(index)
        } else {
            val rc = sqlite3_bind_double(stmtPointer, index, value)
            checkBindError(rc, index)
        }
    }

    override fun bindString(index: Int, value: String?) {
        logger?.log("  BIND [$index] (String) -> \"$value\"")
        if (value == null) {
            bindNull(index)
        } else {
            // 关键点：使用 SQLITE_TRANSIENT 确保 SQLite 内部拷贝字符串
            // 第4个参数 -1 表示让 SQLite 自动计算字符串长度 (遇到 \0 结束)
            val rc = sqlite3_bind_text(stmtPointer, index, value, -1, (-1L).toCPointer())
            checkBindError(rc, index)
        }
    }

    override fun bindBlob(index: Int, value: ByteArray?) {
        logger?.log("  BIND [$index] (Blob) -> [${value?.size} bytes]")
        if (value == null) {
            bindNull(index)
        } else {
            // 关键点：value.refTo(0) 获取 ByteArray 的首地址
            // value.size 是长度
            // SQLITE_TRANSIENT 确保拷贝
            if (value.isEmpty()) {
                // 处理空 Blob 的特殊情况
                val rc = sqlite3_bind_zeroblob(stmtPointer, index, 0)
                checkBindError(rc, index)
            } else {
                val rc = sqlite3_bind_blob(
                    stmtPointer,
                    index,
                    value.refTo(0),
                    value.size,
                    (-1L).toCPointer()
                )
                checkBindError(rc, index)
            }
        }
    }

    override fun bindNull(index: Int) {
        // Only log if explicit null bind (implied by call site logic)
        // See Android implementation for rationale
        val rc = sqlite3_bind_null(stmtPointer, index)
        checkBindError(rc, index)
    }
    
    private fun checkBindError(rc: Int, index: Int) {
        if (rc != 0) {
            val db = sqlite3_db_handle(stmtPointer)
            val errorMsg = sqlite3_errmsg(db)?.toKString() ?: "Unknown error"
            val msg = "Failed to bind argument at index $index. Error code: $rc. Msg: $errorMsg"
            logger?.error(msg)
            throw SqlExecutionException(msg)
        }
    }


    // =========================================================================
    // Execution (执行)
    // =========================================================================

    override fun step(): Boolean {
        // SQLITE_ROW (100) 表示查到了一行数据
        // SQLITE_DONE (101) 表示执行完成（没有更多数据）
        val rc = sqlite3_step(stmtPointer)
        return rc == 100 // SQLITE_ROW
    }

    // =========================================================================
    // Column Methods (输出)
    // =========================================================================

    override fun getColumnLong(index: Int): Long {
        return sqlite3_column_int64(stmtPointer, index)
    }

    override fun getColumnDouble(index: Int): Double {
        return sqlite3_column_double(stmtPointer, index)
    }

    override fun getColumnString(index: Int): String {
        // sqlite3_column_text 返回的是 unsigned char* (UByteVar)
        val ptr = sqlite3_column_text(stmtPointer, index) ?: return ""

        // 如果是 NULL，返回空字符串
        // 必须 reinterpret 为 ByteVar 才能调用 toKString()
        return ptr.reinterpret<ByteVar>().toKString()
    }

    override fun getColumnBlob(index: Int): ByteArray {
        // 1. 获取 Blob 的指针 (void*)
        val blobPtr = sqlite3_column_blob(stmtPointer, index)
        // 2. 获取 Blob 的字节长度
        val size = sqlite3_column_bytes(stmtPointer, index)

        if (blobPtr == null || size == 0) {
            return ByteArray(0)
        }

        // 3. 将 C 内存块复制到 Kotlin ByteArray
        return blobPtr.readBytes(size)
    }

    override fun getColumnType(index: Int): ColumnType {
        // SQLITE_INTEGER(1), SQLITE_FLOAT(2), SQLITE_TEXT(3), SQLITE_BLOB(4), SQLITE_NULL(5)
        val code = sqlite3_column_type(stmtPointer, index)
        return ColumnType.byCode(code)
    }

    override fun getColumnName(index: Int): String {
        val ptr = sqlite3_column_name(stmtPointer, index)
        return ptr?.toKString() ?: ""
    }

    override fun getColumnCount(): Int {
        return sqlite3_column_count(stmtPointer)
    }

    // =========================================================================
    // Lifecycle (生命周期)
    // =========================================================================

    override fun reset() {
        // 重置 Statement 以便再次执行 (例如在循环中绑定新参数)
        sqlite3_reset(stmtPointer)
        // 清除之前的绑定参数，防止残留
        sqlite3_clear_bindings(stmtPointer)
    }

    override fun close() {
        // 销毁 Statement，防止内存泄漏
        sqlite3_finalize(stmtPointer)
    }
}