package net.shantu.kuiklysqlite

/**
 * 数据库管理器
 * 负责创建 Driver、管理版本升级和生命周期回调
 */
class DatabaseManager(
    private val path: String,
    private val schema: SqlSchema,
    private val callback: DatabaseCallback? = null,
    private val logger: SqlLogger? = null
) {
    /**
     * 懒加载的 SqlDriver 实例
     * 第一次访问时会触发数据库初始化流程 (Create/Migrate)
     */
    val driver: SqlDriver by lazy {
        val driver = DriverFactory.createDriver(path)
        driver.logger = logger
        
        try {
            initDatabase(driver)
        } catch (e: Exception) {
            logger?.error("Database initialization failed", e)
            driver.close()
            throw e
        }
        
        driver
    }

    private fun initDatabase(driver: SqlDriver) {
        val currentVersion = getUserVersion(driver)
        val targetVersion = schema.version

        logger?.log("Initializing database: $path. Version: $currentVersion -> $targetVersion")

        if (currentVersion == 0) {
            logger?.log("Creating database (Version 0 -> $targetVersion)")
            driver.transaction {
                schema.create(driver)
                setUserVersion(driver, targetVersion)
            }
            callback?.onCreate(driver)
        } else if (currentVersion < targetVersion) {
            logger?.log("Upgrading database (Version $currentVersion -> $targetVersion)")
            driver.transaction {
                schema.migrate(driver, currentVersion, targetVersion)
                setUserVersion(driver, targetVersion)
            }
            callback?.onUpgrade(driver, currentVersion, targetVersion)
        } else if (currentVersion > targetVersion) {
            // 降级暂不支持，直接抛出异常
            val msg = "Database version mismatch: current ($currentVersion) > target ($targetVersion). Downgrade is not supported."
            logger?.error(msg)
            throw SqlException(msg)
        }

        callback?.onOpen(driver)
    }

    private fun getUserVersion(driver: SqlDriver): Int {
        val stmt = driver.prepare("PRAGMA user_version")
        try {
            return if (stmt.step()) {
                stmt.getColumnLong(0).toInt()
            } else {
                0
            }
        } finally {
            stmt.close()
        }
    }

    private fun setUserVersion(driver: SqlDriver, version: Int) {
        driver.execute("PRAGMA user_version = $version")
    }
}
