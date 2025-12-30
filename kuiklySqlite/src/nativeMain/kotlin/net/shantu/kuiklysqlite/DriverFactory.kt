package net.shantu.kuiklysqlite

import kotlinx.cinterop.*
import platform.posix.*


actual object DriverFactory {

    @OptIn(ExperimentalForeignApi::class)
    actual fun createDriver(path: String): SqlDriver {
        return NativeSqlDriver(path)
    }
}