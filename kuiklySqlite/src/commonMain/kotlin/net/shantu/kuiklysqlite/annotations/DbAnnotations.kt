package net.shantu.kuiklysqlite.annotations

import net.shantu.kuiklysqlite.ColumnType

/**
 * 表实体注解
 * @param tableName 表名。如果不填，默认使用类名。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SqlEntity(val tableName: String = "")

/**
 * 主键注解
 * @param autoGenerate 是否自增。如果是 true，插入时会忽略该字段，并返回生成的 ID。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class PrimaryKey(val autoGenerate: Boolean = true)

/**
 * 列映射注解
 * @param name 自定义列名。如果不填，默认使用属性名。
 * @param defaultValue SQL 默认值（例如 "0", "'unknown'", "CURRENT_TIMESTAMP"）。
 * @param notNull 是否添加 NOT NULL 约束。
 * @param type 指定列类型（例如 ColumnType.TEXT）。如果不填，默认自动推断 (ColumnType.NULL)。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class SqlColumn(
    val name: String = "",
    val defaultValue: String = "",
    val notNull: Boolean = false,
    val type: ColumnType = ColumnType.NULL
)

/**
 * 忽略字段注解
 * 标记该属性不参与数据库映射（不建表、不读写）。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class SqlIgnore

/**
 * 单字段索引注解
 * @param name 索引名称。如果不填，默认自动生成。
 * @param unique 是否唯一索引。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class SqlIndex(
    val name: String = "",
    val unique: Boolean = false
)



/**
 * 复合索引容器（用于支持 Repeatable）
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SqlCompositeIndexes(val value: Array<SqlCompositeIndex>)

/**
 * 复合索引定义
 * @param name 索引名称。
 * @param columns 包含的列名数组（注意：这里填的是列名，不是属性名）。
 * @param unique 是否唯一索引。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable()
annotation class SqlCompositeIndex(
    val name: String,
    val columns: Array<String>,
    val unique: Boolean = false
)
