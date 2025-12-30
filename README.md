# KuiklySqlite 

**KuiklySqlite** 是一个轻量级、高性能的 Kotlin Multiplatform (KMP) ORM 框架，专为kuiklyUI框架设计。目前支持
**Android** 和 **iOS** 和 **HarmonyOS** 。

该框架以性能和响应式为核心构建，提供类型安全的 API、基于 KSP 的编译时 DAO 生成以及无缝的协程集成。

## ✨ 特性

* **Kotlin Multiplatform**: 一次编写，运行在 Android, iOS 和 HarmonyOS 上。
* **编译时代码生成**: 使用 KSP (Kotlin Symbol Processing) 生成高效的 DAO 实现。无反射开销。
* **响应式与异步**:
    * 内置 **协程 (Coroutines)** 支持，提供 `suspend` 函数进行异步 I/O。
    * **响应式查询**: 通过 `Flow` 观察表变更。数据变化时 UI 自动刷新。
    * **专用线程池**: 数据库操作在专用线程池中执行，防止阻塞主线程。
* **并发控制**:
    * 默认启用 **WAL 模式** (Write-Ahead Logging)，大幅提升并发读写性能。
    * 提供 JVM 和 Native 端的线程安全实现。
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
    includeBuild("kuiklySqlite-compiler")
}
include(":kuiklySqlite")
//ohos文件解开注释
//project(":kuiklySqlite").buildFileName = buildFileName
includeBuild("kuiklySqlite-compiler")
```

**模块 `build.gradle.kts/build.ohos.gradle.kts`**:

```kotlin
plugins {
    id("net.shantu.kuiklysqlite.plugin")
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
            implementation(project(":kuiklySqlite"))
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

**异步操作 (协程)**:

```kotlin
// 自动在后台线程执行
val id = userDao.insertSuspend(user)
userDao.updateSuspend(user.copy(age = 21))
```

**响应式 (Flow)**:

```kotlin
// 在 ViewModel 或 UI 层使用
val usersFlow: Flow<List<User>> = userDao.selectAllFlow()

usersFlow.collect { users ->
    // 每当 't_user' 表发生变化时，此代码块都会被触发
    updateUI(users)
}
```

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
