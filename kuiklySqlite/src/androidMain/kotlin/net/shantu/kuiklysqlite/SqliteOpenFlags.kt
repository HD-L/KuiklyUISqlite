package net.shantu.kuiklysqlite

// SQLite 打开标志常量（对应 C 宏定义，必须配套定义）
object SqliteOpenFlags {
    /** 只读模式，数据库必须存在（通用） */
    const val SQLITE_OPEN_READONLY = 0x00000001

    /** 读写模式，数据库不存在则创建（通用） */
    const val SQLITE_OPEN_READWRITE = 0x00000002

    /** 配合 READWRITE 使用，强制创建数据库（即使只读）（通用） */
    const val SQLITE_OPEN_CREATE = 0x00000004

    /** 打开内存数据库（filename 会被忽略）（通用） */
    const val SQLITE_OPEN_MEMORY = 0x00000008

    /** 禁用文件锁（仅用于单机测试，不保证线程安全）（通用） */
    const val SQLITE_OPEN_NOMUTEX = 0x00008000

    /** 启用多线程锁（不同线程可使用不同连接，单连接仍非线程安全）（通用） */
    const val SQLITE_OPEN_MULTITHREAD = 0x00002000

    /** 启用串行化锁（最高安全级别，所有操作串行执行，完全线程安全）（通用） */
    const val SQLITE_OPEN_SERIALIZABLE = 0x00000010

    /** 默认模式：读写+创建+串行化（通用） */
    const val SQLITE_OPEN_DEFAULT =
        SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_SERIALIZABLE

    /** 关闭数据库时自动删除文件（仅 VFS 内部使用，上层调用无需设置） */
    const val SQLITE_OPEN_DELETEONCLOSE = 0x00000008  /* VFS only */

    /** 独占模式打开文件，禁止其他进程访问（仅 VFS 内部使用） */
    const val SQLITE_OPEN_EXCLUSIVE = 0x00000010  /* VFS only */

    /** 启用自动代理模式（用于 VFS 层级代理，仅 VFS 内部使用） */
    const val SQLITE_OPEN_AUTOPROXY = 0x00000020  /* VFS only */

    /** 允许文件名作为 URI 格式（支持网络路径/自定义参数，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_URI = 0x00000040  /* Ok for sqlite3_open_v2() */

    /** 标记为主数据库文件（仅 VFS 内部用于区分文件类型） */
    const val SQLITE_OPEN_MAIN_DB = 0x00000100  /* VFS only */

    /** 标记为临时数据库文件（仅 VFS 内部用于区分文件类型） */
    const val SQLITE_OPEN_TEMP_DB = 0x00000200  /* VFS only */

    /** 标记为临时瞬态数据库（关闭后自动销毁，仅 VFS 内部使用） */
    const val SQLITE_OPEN_TRANSIENT_DB = 0x00000400  /* VFS only */

    /** 标记为主数据库的日志文件（仅 VFS 内部用于日志管理） */
    const val SQLITE_OPEN_MAIN_JOURNAL = 0x00000800  /* VFS only */

    /** 标记为临时数据库的日志文件（仅 VFS 内部用于日志管理） */
    const val SQLITE_OPEN_TEMP_JOURNAL = 0x00001000  /* VFS only */

    /** 标记为子日志文件（用于事务回滚，仅 VFS 内部使用） */
    const val SQLITE_OPEN_SUBJOURNAL = 0x00002000  /* VFS only */

    /** 标记为超级日志文件（用于多数据库事务，仅 VFS 内部使用） */
    const val SQLITE_OPEN_SUPER_JOURNAL = 0x00004000  /* VFS only */

    /** 启用全互斥锁（所有操作加全局锁，线程安全最高，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_FULLMUTEX = 0x00010000  /* Ok for sqlite3_open_v2() */

    /** 启用共享缓存模式（多个连接共享同一缓存，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_SHAREDCACHE = 0x00020000  /* Ok for sqlite3_open_v2() */

    /** 启用私有缓存模式（每个连接独占缓存，默认模式，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_PRIVATECACHE = 0x00040000  /* Ok for sqlite3_open_v2() */

    /** 启用 WAL（Write-Ahead Log）模式（仅 VFS 内部触发 WAL 机制） */
    const val SQLITE_OPEN_WAL = 0x00080000  /* VFS only */

    /** 禁止跟随符号链接（防止路径遍历攻击，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_NOFOLLOW = 0x01000000  /* Ok for sqlite3_open_v2() */

    /** 启用扩展错误码（返回更详细的错误信息，可用于 sqlite3_open_v2） */
    const val SQLITE_OPEN_EXRESCODE = 0x02000000  /* Extended result codes */

    /* Reserved:                         =0x00F00000 *//* Legacy compatibility: */
    /** 兼容旧版：标记为主日志文件（等同于 SQLITE_OPEN_SUPER_JOURNAL，仅 VFS 内部使用） */
    const val SQLITE_OPEN_MASTER_JOURNAL = 0x00004000  /* VFS only */
}