package net.shantu.kuiklysqlite

/**
 * 数据库日志接口
 */
interface SqlLogger {
    fun log(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

/**
 * 默认的控制台日志实现
 */
class ConsoleSqlLogger : SqlLogger {
    override fun log(message: String) {
        println("[KuiklySqlite] $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        println("[KuiklySqlite] ERROR: $message")
        throwable?.printStackTrace()
    }
}
