package net.shantu.kuiklysqlite


expect object DriverFactory {
    // MD5哈希（输入字符串，输出32位小写十六进制）
    fun createDriver(path: String): SqlDriver
}