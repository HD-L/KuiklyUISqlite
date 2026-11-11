# KuiklySqlite 

**KuiklySqlite** 是一个轻量级、高性能的 Kotlin Multiplatform (KMP) ORM 框架，专为kuiklyUI框架设计。目前支持
**Android** 和 **iOS** 和 **HarmonyOS** 。

该框架提供类型安全的 API、基于 KSP 的编译时 DAO 生成，以及线程安全的同步/异步访问。

## ✨ 特性

* **Kotlin Multiplatform**: 一次编写，运行在 Android, iOS 和 HarmonyOS 上。
* **编译时代码生成**: 使用 KSP (Kotlin Symbol Processing) 生成高效的 DAO 实现。无反射开销。
* **异步与回调线程可控**:
    * 所有 DAO 都提供 `xxxAsync` 方法（增删改查、批量、计数），任意查询可用 `driver.async { }` / `driver.transactionAsync { }`。
    * 异步任务在**单个后台线程**按提交顺序执行；回调经 `SqlCallbackDispatcher` 投递，可直接送回 Kuikly 线程。
    * 失败一定会回调 `onError`（未提供则交给 `logger`），不会静默丢失。
    * **表变更监听**: `driver.addListener(table) { }`，写操作成功后触发。
* **并发控制**:
    * 默认启用 **WAL 模式** (Write-Ahead Logging)。
    * 每个 DAO 方法与事务都持有连接锁，多线程访问同一连接时不会互相卷入对方的事务或读到对方的 lastInsertId。
    * 事务支持嵌套（内层使用 SAVEPOINT，内层失败只回滚内层）。
* **开发体验**:
    * 简单的注解 (`@SqlEntity`, `@SqlColumn` 等)。
    * 自动 Schema 管理（建表、索引）。
    * 内置迁移管理器。

---

## 🚀 快速开始

### 1. 配置

添加 KSP 插件和依赖。

**根目录 `settings.gradle.kts/settings.ohos.gradle.kts`**:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://hd-l.github.io/KuiklyUISqlite") // GitHub Pages 地址
        }
    }
}
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://hd-l.github.io/KuiklyUISqlite") // GitHub Pages 地址
        }
    }
}
```

**模块 `build.gradle.kts/build.ohos.gradle.kts`**:

```kotlin
plugins {
       id("net.shantu.kuiklysqlite.plugin") version "1.1.0"
}
kuiklysqlite {
    // 1. 强制指定生成文件的包名
    packageName =  "net.shantu.kuiklysqlite.example.dao"
    // 2. 指定生成目录 (生成的文件将位于 src/commonMain/kuiklysqlite/com/xxx/db/...)
    srcDirs = listOf("src/commonMain/kuiklysqlite")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("net.shantu.kuiklysqlite:kuiklySqlite:1.1.0")
        }
    }
}
```

### 2. 定义实体

使用 `@SqlEntity` 注解标记数据类。

```kotlin

@SqlEntity(tableName = "t_user")
@SqlCompositeIndex(
    name = "idx_name_age",
    columns = ["t_name", "t_age"],
    unique = false
)
@SqlCompositeIndex(
    name = "idx_age_create_time",
    columns = ["t_age", "t_create_time"],
    unique = false
)
data class User @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey(autoGenerate = true)
    @SqlColumn(
        name = "t_id",
        notNull = true,
        type = ColumnType.INTEGER
    )
    val id: Long = 0,

    @SqlIndex(unique = true)
    @SqlColumn(
        name = "t_phone",
        notNull = true,
        type = ColumnType.TEXT
    )
    val phone: String,

    @SqlColumn(
        name = "t_name",
        notNull = true,
        defaultValue = "''",
        type = ColumnType.TEXT
    )
    val name: String,

    @SqlColumn(
        name = "t_age",
        notNull = true,
        defaultValue = "0",
        type = ColumnType.INTEGER
    )
    val age: Int,

    @SqlColumn(
        name = "t_email",
        notNull = false,
        type = ColumnType.TEXT
    )
    val email: String? = null,

    @SqlColumn(
        name = "t_create_time",
        notNull = true,
        defaultValue = "CURRENT_TIMESTAMP",
        type = ColumnType.INTEGER
    )
    val createTime: Long = Clock.System.now().epochSeconds,

    @SqlIgnore
    val avatar: ByteArray? = null
)
```

### 3. 构建项目

运行 `./gradlew clean :shared:kspCommonMainKotlinMetadata` 或同步 IDE。KSP 处理器会自动生成 `UserDao` 和 `UserTable` 类。

### 4. 使用方法

#### 初始化

使用 `DatabaseManager` 处理版本控制和迁移。

```kotlin
object AppSchema : SqlSchema {
    override val version = 1
    override fun create(driver: SqlDriver) {
        UserDao(driver).initTable()
    }
    override fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) {
        // 处理迁移逻辑
    }
}

val dbManager = DatabaseManager("data/***/my_app.db", AppSchema)
val userDao = UserDao(dbManager.driver)
```

#### CRUD 操作

**同步操作 (阻塞)**:

```kotlin
val user = User(name = "Alice", age = 20, email = "alice@example.com")
val id = userDao.insert(user)
val alice = userDao.selectById(id)
```

同步方法可在任意线程调用，但会阻塞调用线程；页面中建议使用异步 API。

**异步操作 (回调)**:

```kotlin
// 在后台线程执行，回调默认也在后台线程（见下文「在 Kuikly 中使用异步 API」）
userDao.insertAsync(user, onError = { e -> showError(e) }) { id -> /* ... */ }
userDao.selectAllAsync { users -> render(users) }

// 任意组合查询 / 事务
userDao.driver.async({ userDao.select().where(UserTable.age gt 18).find() }) { adults -> /* ... */ }
userDao.driver.transactionAsync({
    userDao.batchInsert(newUsers)
    userDao.deleteById(oldId)
}, onError = { e -> /* 已整体回滚 */ }) { }
```

#### 在 Kuikly 中使用异步 API

Kuikly 规定只能在 Kuikly 线程修改 `observable` 等 UI 状态。本库不依赖 Kuikly，回调线程由 `SqlCallbackDispatcher` 决定；
配合 [kuiklyx 协程库](https://kuikly.tds.qq.com/) 把回调送回 Kuikly 线程：

```kotlin
// 页面内（pagerId 为当前页面 ID）
val toKuikly = SqlCallbackDispatcher { task ->
    KuiklyContextScheduler.runOnKuiklyThread(pagerId) { cancel -> if (!cancel) task() }
}

userDao.selectAllAsync(dispatcher = toKuikly) { users -> this.users = users }   // 单次指定
dbManager.driver.callbackDispatcher = toKuikly                                   // 或设为默认值
```

#### 线程模型

| 调用方式 | 执行线程 | 说明 |
|:--|:--|:--|
| 同步 DAO 方法 / `transaction { }` | 调用方线程 | 持有连接锁，其他线程等待 |
| `xxxAsync` / `driver.async` / `transactionAsync` | `SqlWorker` 单个后台线程 | FIFO，先提交先执行 |
| 异步回调 | `SqlCallbackDispatcher` | 默认 `WORKER`（后台线程） |

手写多步 SQL（如 prepare → step → `getLastInsertId()`）时请包在 `driver.withConnection { }` 中。


## 📚 注解参考

| 注解           | 目标       | 描述                                                                         |
|:-------------|:---------|:---------------------------------------------------------------------------|
| `@SqlEntity` | Class    | 标记类为数据库实体。`tableName` 可选（默认为类名）。                                           |
| `@SqlColumn` | Property | 配置列属性。`name`, `isPrimaryKey`, `isAutoGenerate`, `notNull`, `defaultValue`。 |
| `@SqlIndex`  | Property | 为此列创建索引。支持 `name`, `unique`。                                               |
| `@SqlIgnore` | Property | 从数据库持久化中排除此属性。                                                             |

## 🛠 高级配置

### 数据库迁移

实现 `SqlSchema.migrate` 方法来处理 Schema 变更。

```kotlin
override fun migrate(driver: SqlDriver, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
        driver.execute("ALTER TABLE t_user ADD COLUMN phone TEXT")
    }
}
```

## 🧪 测试

`kuiklySqlite/src/commonTest` 覆盖事务回滚与嵌套、跨线程写入隔离、异步错误回调、回调投递与执行顺序、并发下的 lastInsertId。

```bash
# Android：在已连接的设备/模拟器上运行 instrumented test（JNA 需加载打包进 APK 的 libsqlite3.so）
./gradlew :kuiklySqlite:connectedDebugAndroidTest

# iOS：链接测试可执行文件后在已启动的模拟器中运行
./gradlew :kuiklySqlite:linkDebugTestIosSimulatorArm64
xcrun simctl spawn booted kuiklySqlite/build/bin/iosSimulatorArm64/debugTest/test.kexe

# 鸿蒙：链接后推送到设备运行（输出写入 hilog）
./gradlew -c settings.ohos.gradle.kts :kuiklySqlite:linkDebugTestOhosArm64
hdc file send kuiklySqlite/build/bin/ohosArm64/debugTest/test.kexe /data/local/tmp/
hdc shell "cd /data/local/tmp && ./test.kexe"
```

## 📝 更新日志

### 1.1.0

需同时升级运行库与插件（生成的 DAO 依赖新的运行时 API），升级后重新执行 KSP 生成 DAO。

* 修复：异步任务异常被吞掉、回调永不触发的问题，新增 `onError`。
* 修复：Android 端多线程线程池共用一个连接，事务期间其他线程的写入会被卷入或一起回滚；现改为连接锁 + 单后台线程。
* 修复：iOS/鸿蒙测试可执行文件链接失败（`sqlite3_version` 未定义）。
* 新增：异步查询（`selectAllAsync` / `selectByIdAsync` / `countAsync` / `existsAsync` / 批量异步）、`driver.async`、`transactionAsync`。
* 新增：`SqlCallbackDispatcher`，可把回调送回 Kuikly 线程。
* 新增：嵌套事务（SAVEPOINT）。
* 变更：生成的 DAO 的 `driver` 改为公开属性；`SqlWorker.execute` 标记为废弃。
