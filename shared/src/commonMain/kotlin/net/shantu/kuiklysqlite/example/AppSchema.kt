package net.shantu.kuiklysqlite.example

import net.shantu.kuiklysqlite.SqlDriver
import net.shantu.kuiklysqlite.SqlSchema
import net.shantu.kuiklysqlite.example.dao.UserDao

// 1. 定义 Schema
object AppSchema : SqlSchema {
    override val version = 1

    override fun create(driver: SqlDriver) {
        UserDao(driver).initTable()
        // 其他 DAO 初始化...
    }

    override fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
//            driver.execute("ALTER TABLE t_user ADD COLUMN t_email_new TEXT DEFAULT ''")
        }
    }
}