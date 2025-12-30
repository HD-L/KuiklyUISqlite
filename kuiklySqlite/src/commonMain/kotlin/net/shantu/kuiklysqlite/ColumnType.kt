package net.shantu.kuiklysqlite

/**
 * SQLite 的 5 种基础类型枚举
 */
enum class ColumnType(val code: Int) {
    INTEGER(1),
    FLOAT(2),
    TEXT(3),
    BLOB(4),
    NULL(5);

    companion object {
        fun byCode(code: Int): ColumnType = entries.find { it.code == code } ?: NULL
    }
}