package net.shantu.kuiklysqlite

import com.sun.jna.Library
import com.sun.jna.Native

import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

// JNA 接口定义
internal interface SqliteJnaLib : Library {
    // --- 连接管理 ---
    fun sqlite3_open(filename: String, ppDb: PointerByReference): Int

    /**
     * 增强版打开数据库方法（补全核心）
     * 对应 C 签名：int sqlite3_open_v2(
     *   const char *filename,   // 数据库路径
     *   sqlite3 **ppDb,         // 输出：数据库连接指针
     *   int flags,              // 打开标志（见 SqliteOpenFlags）
     *   const char *zVfs        // VFS 名称（null = 默认VFS）
     * );
     * @param filename 数据库路径（":memory:" 或文件路径）
     * @param ppDb 输出参数：sqlite3* 指针的引用
     * @param flags 打开模式标志（如 SQLITE_OPEN_DEFAULT）
     * @param zVfs VFS 名称（传 null 使用系统默认）
     * @return 错误码（0 = 成功，非0 = 失败）
     */
    fun sqlite3_open_v2(
        filename: String,
        ppDb: PointerByReference,
        flags: Int,
        zVfs: String? // JNA 自动映射 null 到 C 的 NULL
    ): Int

    fun sqlite3_close(db: Pointer): Int
    fun sqlite3_errmsg(db: Pointer): Pointer? // 获取错误信息
    fun sqlite3_exec(
        db: Pointer, sql: String, cb: Pointer?, arg: Pointer?, errMsg: PointerByReference?
    ): Int

    // --- 语句准备 ---
    // pzTail (最后一个参数) 可以传 null
    fun sqlite3_prepare_v2(
        db: Pointer, zSql: String, nByte: Int, ppStmt: PointerByReference, pzTail: Pointer?
    ): Int

    // --- 辅助信息 ---
    fun sqlite3_changes(db: Pointer): Int
    fun sqlite3_last_insert_rowid(db: Pointer): Long

    // Bind Functions
    fun sqlite3_bind_null(stmt: Pointer, i: Int): Int
    fun sqlite3_bind_int64(stmt: Pointer, i: Int, value: Long): Int
    fun sqlite3_bind_double(stmt: Pointer, i: Int, value: Double): Int

    // String 会自动转换为 C 字符串 (const char*)
    fun sqlite3_bind_text(stmt: Pointer, i: Int, value: String, n: Int, free: Long): Int

    // ByteArray 会自动转换为 void*
    fun sqlite3_bind_blob(stmt: Pointer, i: Int, value: ByteArray, n: Int, free: Long): Int
    fun sqlite3_bind_zeroblob(stmt: Pointer, i: Int, n: Int): Int

    // Execution
    fun sqlite3_step(stmt: Pointer): Int

    // Column Get Functions
    fun sqlite3_column_int64(stmt: Pointer, iCol: Int): Long
    fun sqlite3_column_double(stmt: Pointer, iCol: Int): Double

    // 注意：返回值是指针，需要手动转 String
    fun sqlite3_column_text(stmt: Pointer, iCol: Int): Pointer?
    fun sqlite3_column_blob(stmt: Pointer, iCol: Int): Pointer?
    fun sqlite3_column_bytes(stmt: Pointer, iCol: Int): Int
    fun sqlite3_column_type(stmt: Pointer, iCol: Int): Int
    fun sqlite3_column_count(stmt: Pointer): Int
    fun sqlite3_column_name(stmt: Pointer, iCol: Int): Pointer?

    // Lifecycle
    fun sqlite3_reset(stmt: Pointer): Int
    fun sqlite3_clear_bindings(stmt: Pointer): Int
    fun sqlite3_finalize(stmt: Pointer): Int

    companion object {
        // 加载名为 "sqlite3" 的 so 库 (对应 libsqlite3.so)
        val INSTANCE: SqliteJnaLib by lazy {
            Native.load("sqlite3", SqliteJnaLib::class.java)
        }

        // SQLITE_TRANSIENT: (void*)-1
        // 告诉 SQLite 复制数据，不要持有 Java 内存的引用
        val SQLITE_TRANSIENT: Long = -1
    }
}