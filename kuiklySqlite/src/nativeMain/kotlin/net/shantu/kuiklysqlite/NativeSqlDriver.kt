package net.shantu.kuiklysqlite

import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.Worker
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
class NativeSqlDriver(path: String) : SqlDriver {
    
    // Use a dedicated Worker for all database operations to ensure thread safety
    // This serializes all access to the database connection
    private val worker = Worker.start(name = "KuiklySqlite-Driver-Worker")
    
    // Use kotlin.concurrent.AtomicReference (available in Kotlin 1.9+)
    // No need for freeze() in new memory model
    private val dbPointer = AtomicReference<CPointer<sqlite3>?>(null)
    
    override var logger: SqlLogger? = null

    init {
        memScoped {
            val dbPtrVar = alloc<CPointerVar<sqlite3>>()
            // SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX
            val flags = 0x00000002 or 0x00000004 or 0x00010000
            val rc = sqlite3_open_v2(path, dbPtrVar.ptr, flags, null)
            
            if (rc != 0) {
                throw SqlDatabaseOpenException("Cannot open database: $path. Error code: $rc")
            }
            dbPointer.value = dbPtrVar.value
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

    override fun close() {
        // Atomic compare and set to ensure we only close once
        val ptr = dbPointer.value
        if (ptr != null) {
            if (dbPointer.compareAndSet(ptr, null)) {
                sqlite3_close(ptr)
                logger?.log("Database closed")
                worker.requestTermination()
            }
        }
    }

    override fun execute(sql: String) {
        val db = checkOpen()
        logger?.log("EXECUTE: $sql")
        
        val rc = sqlite3_exec(db, sql, null, null, null)
        if (rc != 0) {
            throwSqliteError(rc, "Execute failed: $sql")
        }
    }

    override fun prepare(sql: String): SqlStatement {
        val db = checkOpen()
        logger?.log("PREPARE: $sql")

        return memScoped {
            val stmtPtrVar = alloc<CPointerVar<sqlite3_stmt>>()
            val rc = sqlite3_prepare_v2(db, sql, -1, stmtPtrVar.ptr, null)

            if (rc != 0) {
                throwSqliteError(rc, "Prepare failed: $sql")
            }

            val stmtPtr = stmtPtrVar.value ?: throw SqlExecutionException("Prepared statement pointer is null")
            NativeSqlStatement(stmtPtr, this@NativeSqlDriver)
        }
    }

    override fun getChanges(): Int = sqlite3_changes(checkOpen())

    override fun getLastInsertId(): Long = sqlite3_last_insert_rowid(checkOpen())
    
    // --- Observer Support (Thread Safe via AtomicReference & Copy-On-Write) ---
    private val listeners = AtomicReference(mapOf<String, List<() -> Unit>>())

    override fun addListener(tableName: String, listener: () -> Unit) {
        while (true) {
            val current = listeners.value
            val list = current[tableName] ?: emptyList()
            val newMap = current + (tableName to (list + listener))
            if (listeners.compareAndSet(current, newMap)) break
        }
    }

    override fun removeListener(tableName: String, listener: () -> Unit) {
        while (true) {
            val current = listeners.value
            val list = current[tableName] ?: return
            val newList = list - listener
            val newMap = if (newList.isEmpty()) current - tableName else current + (tableName to newList)
            if (listeners.compareAndSet(current, newMap)) break
        }
    }

    override fun notifyListeners(tableName: String) {
        val current = listeners.value
        current[tableName]?.forEach { it.invoke() }
    }
    
    private fun checkOpen(): CPointer<sqlite3> {
        return dbPointer.value ?: throw SqlDatabaseOpenException("Database is closed")
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