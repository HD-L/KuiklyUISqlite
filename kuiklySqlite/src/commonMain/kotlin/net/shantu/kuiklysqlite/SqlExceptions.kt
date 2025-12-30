package net.shantu.kuiklysqlite

/**
 * 数据库操作基础异常
 */
open class SqlException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 数据库打开失败异常
 */
class SqlDatabaseOpenException(message: String, cause: Throwable? = null) : SqlException(message, cause)

/**
 * SQL 执行异常（语法错误、表不存在等）
 */
class SqlExecutionException(message: String, cause: Throwable? = null) : SqlException(message, cause)

/**
 * 事务操作异常
 */
class SqlTransactionException(message: String, cause: Throwable? = null) : SqlException(message, cause)

/**
 * 约束违反异常（唯一性冲突、非空检查失败等）
 */
class SqlConstraintException(message: String, cause: Throwable? = null) : SqlException(message, cause)
