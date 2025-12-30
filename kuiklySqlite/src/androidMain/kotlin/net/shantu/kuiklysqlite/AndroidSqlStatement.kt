package net.shantu.kuiklysqlite

import com.sun.jna.Pointer
import net.shantu.kuiklysqlite.SqliteJnaLib.Companion.INSTANCE
import net.shantu.kuiklysqlite.SqliteJnaLib.Companion.SQLITE_TRANSIENT

internal class AndroidSqlStatement(
    private val stmtPointer: Pointer,
    private val driver: SqlDriver // Add driver reference for logging
) : SqlStatement {

    // 缓存 JNA 实例引用，方便调用
    private val lib = INSTANCE

    // =========================================================================
    // Bind Methods (输入)
    // =========================================================================

    override fun bindLong(index: Int, value: Long?) {
        driver.logger?.log("  BIND [$index] (Long) -> $value")
        if (value == null) {
            bindNull(index)
        } else {
            val rc = lib.sqlite3_bind_int64(stmtPointer, index, value)
            checkBindError(rc, index)
        }
    }

    override fun bindDouble(index: Int, value: Double?) {
        driver.logger?.log("  BIND [$index] (Double) -> $value")
        if (value == null) {
            bindNull(index)
        } else {
            val rc = lib.sqlite3_bind_double(stmtPointer, index, value)
            checkBindError(rc, index)
        }
    }

    override fun bindString(index: Int, value: String?) {
        driver.logger?.log("  BIND [$index] (String) -> \"$value\"")
        if (value == null) {
            bindNull(index)
        } else {
            // JNA 会自动处理 String 到 C 字符串的编码转换 (默认 UTF-8)
            // -1 表示长度自动计算
            // SQLITE_TRANSIENT 表示让 SQLite 内部拷贝一份字符串
            val rc = lib.sqlite3_bind_text(stmtPointer, index, value, -1, SQLITE_TRANSIENT)
            checkBindError(rc, index)
        }
    }

    override fun bindBlob(index: Int, value: ByteArray?) {
        driver.logger?.log("  BIND [$index] (Blob) -> [${value?.size} bytes]")
        if (value == null) {
            bindNull(index)
        } else {
            if (value.isEmpty()) {
                val rc = lib.sqlite3_bind_zeroblob(stmtPointer, index, 0)
                checkBindError(rc, index)
            } else {
                // JNA 直接支持传递 ByteArray，它会锁定数组内存并传递指针
                val rc = lib.sqlite3_bind_blob(stmtPointer, index, value, value.size, SQLITE_TRANSIENT)
                checkBindError(rc, index)
            }
        }
    }

    override fun bindNull(index: Int) {
        // Only log if not already logged by other bind methods calling this
        // But usually bindNull is called directly for explicit nulls or internally.
        // We can just log it. If called internally, we might see duplicate logs or "null" then "null".
        // To avoid duplicate logs for "bindLong(null)" which calls "bindNull", we should be careful.
        // Actually, in bindLong(null), we log "BIND ... -> null" then call bindNull.
        // So here we can check or just log. Let's log "BIND ... -> NULL" for explicit calls.
        // However, since bindLong/Double/String/Blob all call this for nulls, we might want to avoid logging here if possible,
        // or accept the redundancy.
        // A better way is to move logging to the specific bind methods and remove it from here?
        // No, because bindNull can be called directly.
        // Let's just log it. Redundancy is acceptable for debugging.
        // Or better: don't log in bindNull, let callers log? No, bindNull is part of interface.
        // We will log here only if it seems it wasn't logged? No state.
        // Let's just log "BIND [$index] -> NULL" here.
        // But wait, bindLong(null) logs "-> null" then calls this.
        // If we log here, we get two logs.
        // Let's modify bindLong/etc to NOT call bindNull but call lib.sqlite3_bind_null directly?
        // bindNull implementation calls lib.sqlite3_bind_null.
        // So we can just use lib.sqlite3_bind_null in other methods.
        val rc = lib.sqlite3_bind_null(stmtPointer, index)
        checkBindError(rc, index)
    }
    
    private fun checkBindError(rc: Int, index: Int) {
        if (rc != 0) {
            val msg = "Failed to bind argument at index $index. Error code: $rc"
            driver.logger?.error(msg)
            throw SqlExecutionException(msg)
        }
    }


    // =========================================================================
    // Execution (执行)
    // =========================================================================

    override fun step(): Boolean {
        // SQLITE_ROW = 100
        val rc = lib.sqlite3_step(stmtPointer)
        return rc == 100
    }

    // =========================================================================
    // Column Methods (输出)
    // =========================================================================

    override fun getColumnLong(index: Int): Long {
        return lib.sqlite3_column_int64(stmtPointer, index)
    }

    override fun getColumnDouble(index: Int): Double {
        return lib.sqlite3_column_double(stmtPointer, index)
    }

    override fun getColumnString(index: Int): String {
        // 返回的是 C 语言 char* 指针
        val ptr = lib.sqlite3_column_text(stmtPointer, index)

        // 如果指针为 NULL，返回空字符串
        if (ptr == null) return ""

        // JNA 提供的 getString 方法，指定 UTF-8 编码读取 C 字符串
        return ptr.getString(0, "UTF-8")
    }

    override fun getColumnBlob(index: Int): ByteArray {
        // 1. 获取指针
        val blobPtr = lib.sqlite3_column_blob(stmtPointer, index)
        // 2. 获取长度
        val size = lib.sqlite3_column_bytes(stmtPointer, index)

        if (blobPtr == null || size == 0) {
            return ByteArray(0)
        }

        // 3. 从指针地址读取指定长度的字节到 Java 数组
        return blobPtr.getByteArray(0, size)
    }

    override fun getColumnType(index: Int): ColumnType {
        // 1:INTEGER, 2:FLOAT, 3:TEXT, 4:BLOB, 5:NULL
        val code = lib.sqlite3_column_type(stmtPointer, index)
        return ColumnType.byCode(code)
    }

    override fun getColumnName(index: Int): String {
        val ptr = lib.sqlite3_column_name(stmtPointer, index)
        return ptr?.getString(0, "UTF-8") ?: ""
    }

    override fun getColumnCount(): Int {
        return lib.sqlite3_column_count(stmtPointer)
    }

    // =========================================================================
    // Lifecycle (生命周期)
    // =========================================================================

    override fun reset() {
        lib.sqlite3_reset(stmtPointer)
        lib.sqlite3_clear_bindings(stmtPointer)
    }

    override fun close() {
        lib.sqlite3_finalize(stmtPointer)
    }
}