package net.shantu.kuiklysqlite

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import net.shantu.kuiklysqlite.SqliteJnaLib.Companion.INSTANCE
import net.shantu.kuiklysqlite.SqliteOpenFlags.SQLITE_OPEN_CREATE
import net.shantu.kuiklysqlite.SqliteOpenFlags.SQLITE_OPEN_FULLMUTEX
import net.shantu.kuiklysqlite.SqliteOpenFlags.SQLITE_OPEN_READWRITE

internal class AndroidSqlDriver(path: String) : BaseSqlDriver() {

    private val lib = INSTANCE

    // 只在持有连接锁时读写
    private var dbPointer: Pointer? = null

    init {
        // 1. 准备一个引用指针，用于接收 SQLite 返回的数据库句柄 (sqlite3**)
        val ppDb = PointerByReference()

        // 2. 调用 C 函数打开数据库 (使用 sqlite3_open_v2)
        // SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
        // 0x00000002 | 0x00000004 | 0x00010000
        val flags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX
        val rc = lib.sqlite3_open_v2(path, ppDb, flags, null)

        if (rc != 0) { // 0 == SQLITE_OK
            throw SqlDatabaseOpenException("Failed to open database: $path. Error code: $rc")
        }

        // 3. 保存数据库指针
        dbPointer = ppDb.value
        logger?.log("Database opened: $path")
        
        // 4. 启用 WAL 模式 (并发读写关键)
        enableWalMode()
    }
    
    private fun enableWalMode() {
        try {
            execute("PRAGMA journal_mode=WAL;")
            execute("PRAGMA synchronous=NORMAL;")
            logger?.log("WAL mode enabled")
        } catch (e: Exception) {
            logger?.error("Failed to enable WAL mode", e)
        }
    }

    override fun close() = withConnection {
        val db = dbPointer ?: return@withConnection
        lib.sqlite3_close(db)
        dbPointer = null
        logger?.log("Database closed")
    }

    override fun execute(sql: String) = withConnection {
        val db = checkOpen()
        logger?.log("EXECUTE: $sql")

        val rc = lib.sqlite3_exec(db, sql, null, null, null)

        if (rc != 0) {
            throwSqliteError(rc, "Execute failed: $sql")
        }
    }

    override fun prepare(sql: String): SqlStatement = withConnection {
        val db = checkOpen()
        logger?.log("PREPARE: $sql")

        val ppStmt = PointerByReference()
        val rc = lib.sqlite3_prepare_v2(db, sql, -1, ppStmt, null)

        if (rc != 0) {
            throwSqliteError(rc, "Prepare failed: $sql")
        }

        val stmtPtr = ppStmt.value ?: throw SqlExecutionException("Prepared statement pointer is null")
        AndroidSqlStatement(stmtPtr, this)
    }

    override fun getChanges(): Int = withConnection { lib.sqlite3_changes(checkOpen()) }

    override fun getLastInsertId(): Long = withConnection { lib.sqlite3_last_insert_rowid(checkOpen()) }

    // --- 辅助方法 ---

    private fun checkOpen(): Pointer {
        return dbPointer ?: throw SqlDatabaseOpenException("Database is closed")
    }

    private fun throwSqliteError(errorCode: Int, contextMessage: String) {
        val db = dbPointer ?: throw SqlDatabaseOpenException("Database is closed during error handling")

        // 获取 SQLite 的错误描述字符串
        val msgPtr = lib.sqlite3_errmsg(db)
        val msg = msgPtr?.getString(0, "UTF-8") ?: "Unknown error"
        
        val fullMsg = "$contextMessage. SQLite error (code $errorCode): $msg"
        logger?.error(fullMsg)

        // 根据错误码抛出具体异常
        // SQLITE_CONSTRAINT (19)
        if (errorCode == 19) {
            throw SqlConstraintException(fullMsg)
        }
        
        throw SqlExecutionException(fullMsg)
    }
}