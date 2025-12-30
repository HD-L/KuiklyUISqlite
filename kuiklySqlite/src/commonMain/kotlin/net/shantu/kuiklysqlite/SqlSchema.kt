package net.shantu.kuiklysqlite

/**
 * 数据库 Schema 定义接口
 * 负责定义数据库的版本、创建逻辑和迁移逻辑
 */
interface SqlSchema {
    /**
     * 当前 Schema 版本号
     */
    val version: Int

    /**
     * 创建数据库时回调 (当 version 为 0 时调用)
     * 通常在这里调用 Dao.initTable()
     */
    fun create(driver: SqlDriver)

    /**
     * 数据库升级时回调
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号 (即 [version])
     * @return SqlDriver (方便链式调用，虽然通常不需要返回)
     */
    fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int)
}

/**
 * 数据库生命周期回调接口
 */
interface DatabaseCallback {
    /**
     * 数据库创建完成后调用
     */
    fun onCreate(driver: SqlDriver) {}

    /**
     * 数据库升级完成后调用
     */
    fun onUpgrade(driver: SqlDriver, oldVersion: Int, newVersion: Int) {}

    /**
     * 数据库打开完成后调用 (每次连接建立后都会调用)
     */
    fun onOpen(driver: SqlDriver) {}
}
