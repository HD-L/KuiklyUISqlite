package net.shantu.kuiklysqlite

/**
 * 3. 预编译语句接口 (Statement / Cursor)
 * 对应 C 语言的 sqlite3_stmt* 指针
 *
 * 这个接口身兼两职：
 * 1. 负责 Bind 数据 (输入)
 * 2. 负责 Get Column 数据 (输出)
 */
interface SqlStatement : AutoCloseable {
    // --- 输入阶段 (Bind) ---
    // index 通常从 1 开始 (为了符合 JDBC/SQLite 标准习惯，建议在实现层做处理)
    fun bindLong(index: Int, value: Long?)
    fun bindDouble(index: Int, value: Double?)
    fun bindString(index: Int, value: String?)
    fun bindBlob(index: Int, value: ByteArray?)
    fun bindNull(index: Int)

    // --- 执行阶段 (Step) ---
    // 执行一步。
    // 如果是 SELECT，返回 true 表示读到了一行数据 (SQLITE_ROW)
    // 如果是 INSERT/UPDATE 或读完了，返回 false (SQLITE_DONE)
    fun step(): Boolean

    // --- 输出阶段 (Column) ---
    // index 从 0 开始
    fun getColumnLong(index: Int): Long
    fun getColumnDouble(index: Int): Double
    fun getColumnString(index: Int): String
    fun getColumnBlob(index: Int): ByteArray

    // 获取列的类型 (用于 ORM 动态判断)
    // 返回值对应 SQLITE_INTEGER, SQLITE_TEXT 等常量
    fun getColumnType(index: Int): ColumnType

    // 获取列名 (可选，ORM 映射时可能需要)
    fun getColumnName(index: Int): String

    // 获取列数
    fun getColumnCount(): Int

    // --- 生命周期 ---
    // 重置状态，以便复用该 Statement (sqlite3_reset)
    fun reset()

    // 释放资源 (sqlite3_finalize)
    override fun close()
}