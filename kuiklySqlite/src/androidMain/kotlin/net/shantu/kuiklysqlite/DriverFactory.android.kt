package net.shantu.kuiklysqlite

import com.sun.jna.Native
import kotlin.jvm.java

actual object DriverFactory {
    actual fun createDriver(path: String): SqlDriver {
        return AndroidSqlDriver(path)
    }
}