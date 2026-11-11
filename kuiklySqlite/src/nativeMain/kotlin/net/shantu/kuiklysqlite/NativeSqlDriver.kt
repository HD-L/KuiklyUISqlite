package net.shantu.kuiklysqlite

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import cnames.structs.sqlite3_stmt

// ---------------- Actual Driver (Connection) ----------------
@kotlinx.cinterop.ExperimentalForeignApi
class NativeSqlDriver(path: String) : BaseSqlDriver() {

    // 只在持有连接锁时读写（BaseSqlDriver 提供可重入的连接锁）
    private var dbPointer: CPointer<sqlite3>? = null

    init {
        memScoped {
            val dbPtrVar = alloc<CPointerVar<sqlite3>>()
            // SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
            val flags = 0x00000002 or 0x00000004 or 0x00010000
            val rc = sqlite3_open_v2(path, dbPtrVar.ptr, flags, null)
            
            if (rc != 0) {
                throw SqlDatabaseOpenException("Cannot open database: $path. Error code: $rc")
            }
            dbPointer = dbPtrVar.value
        }
        logger?.log("Database opened: $path")
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
        val ptr = dbPointer ?: return@withConnection
        sqlite3_close(ptr)
        dbPointer = null
        logger?.log("Database closed")
    }

    override fun execute(sql: String) = withConnection {
        val db = checkOpen()
        logger?.log("EXECUTE: $sql")
        
        val rc = sqlite3_exec(db, sql, null, null, null)
        if (rc != 0) {
            throwSqliteError(rc, "Execute failed: $sql")
        }
    }

    override fun prepare(sql: String): SqlStatement = withConnection {
        val db = checkOpen()
        logger?.log("PREPARE: $sql")

        memScoped {
            val stmtPtrVar = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(db, sql, -1, stmtPtrVar.ptr, null)

            if (rc != 0) {
                throwSqliteError(rc, "Prepare failed: $sql")
            }

            val stmtPtr = stmtPtrVar.value ?: throw SqlExecutionException("Prepared statement pointer is null")
            NativeSqlStatement(stmtPtr, this@NativeSqlDriver)
        }
    }

    override fun getChanges(): Int = withConnection { sqlite3_changes(checkOpen()) }

    override fun getLastInsertId(): Long = withConnection { sqlite3_last_insert_rowid(checkOpen()) }
    
    private fun checkOpen(): CPointer<sqlite3> {
        return dbPointer ?: throw SqlDatabaseOpenException("Database is closed")
    }
    
    private fun throwSqliteError(errorCode: Int, contextMessage: String) {
        val db = checkOpen()
        val errorMsg = sqlite3_errmsg(db)?.toKString() ?: "Unknown error"
        
        val fullMsg = "$contextMessage. SQLite error (code $errorCode): $errorMsg"
        logger?.error(fullMsg)
        
        if (errorCode == 19) {
             throw SqlConstraintException(fullMsg)
        }
        throw SqlExecutionException(fullMsg)
    }
}