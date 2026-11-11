# KuiklyUISqlite — 工作区说明

面向后续 ZCode agent 的项目备忘。先读本文件，再动手。

## 项目用途

Kotlin Multiplatform 的轻量 ORM 库 **KuiklySqlite**（为 Kuikly UI 设计），支持 Android / iOS / HarmonyOS(ohos)。
核心机制：`@SqlEntity`/`@SqlColumn` 等注解 + **KSP 编译时生成 DAO/Table**（无反射）；`SqlDriver` 在各端有独立实现；
`DatabaseManager` + `SqlSchema` 负责建表与迁移。npm 包 `package.json` 只是一个静态资源服务器（Kuikly 调试用），不是构建工具。

## 主要目录

- `kuiklySqlite/` — 库主体（发布产物）。`src/commonMain`（注解、DSL、Driver 接口、DatabaseManager）、`src/androidMain`（JNA 实现）、`src/nativeMain`（cinterop + `c/sqlite3.c`）。
- `kuiklySqlite-compiler/` — KSP 处理器 + Gradle 插件（`net.shantu.kuiklysqlite.plugin`），用 kotlinpoet 生成 DAO。独立 `includeBuild`，用 **Kotlin 1.9.20**，与主工程的 2.1.21 不同版本，属正常。
- `shared/` — Kuikly 业务示例代码（Entity、AppSchema、页面），同时是 KSP 生成目录的宿主。
- `androidApp/`、`iosApp/`、`ohosApp/` — 三端壳工程。
- `maven-repo/` — 本地发布输出，通过 `git subtree push --prefix maven-repo origin gh-pages` 推到 GitHub Pages 作为远端 Maven 源。
- `buildSrc/src/main/java/KotlinBuildVar.kt` — 版本号唯一来源（`Version` / `BuildPlugin`）。

## 常用命令

```bash
./gradlew clean :shared:kspCommonMainKotlinMetadata   # 跑 KSP 生成 DAO（IDE 同步也会触发）
./gradlew :kuiklySqlite:build                          # 构建库（JVM/iOS 通道）
./gradlew -c settings.ohos.gradle.kts :shared:build    # ohos 通道，必须带 -c 指定 settings 文件
bash publishing-github.sh                              # 发布（需按当前版本号核对脚本内容）
./gradlew :kuiklySqlite:connectedDebugAndroidTest      # commonTest 作为 Android instrumented test 跑（需连设备）
./gradlew :kuiklySqlite:linkDebugTestIosSimulatorArm64 && xcrun simctl spawn booted kuiklySqlite/build/bin/iosSimulatorArm64/debugTest/test.kexe
```

KGP 会把 `iosSimulatorArm64Test` 任务置为 disabled（新版 Xcode 下），因此 iOS 测试直接用 simctl 运行链接产物；鸿蒙同理用 hdc 推送 `ohosArm64/debugTest/test.kexe` 运行。

## 架构与分层约束

- 线程模型（1.1.0 起）：`BaseSqlDriver` 持有可重入连接锁，生成的 DAO 方法都包在 `driver.withConnection { }` 中，事务从 BEGIN 到 COMMIT 持锁；
  异步统一走 `SqlWorker` 单后台线程 + `SqlCallbackDispatcher` 回调。不要再引入线程池或绕过连接锁直接多步操作连接。
- 运行库与 compiler 版本需同步升级：生成代码依赖运行库 API（`withConnection`、`BaseDao.driver`）。
- 修改公共 API 时注意四套 source set 都要落地：`commonMain` 定义接口，`androidMain` / `nativeMain` 各写 `expect` 实现（`DriverFactory`、`SqlWorker` 成对出现）。
- KSP 生成规则：`shared/build.gradle.kts` 的 `kuiklysqlite { packageName / srcDirs }` 决定生成位置；生成结果**已提交**在 `shared/src/commonMain/kuiklysqlite/`，改实体后需重新跑 KSP 并保持二者一致。
- `kuiklySqlite-compiler` 里的 `BuildConfig.kt` 由 Gradle 任务自动生成，不要手改。

## 双通道构建（最容易踩的点）

- **JVM/iOS 通道**：`settings.gradle.kts` + `build.gradle.kts`，Kotlin `2.1.21`，Kuikly 版本 `2.7.0-2.1.21`。
- **ohos 通道**：`settings.ohos.gradle.kts` + `build.ohos.gradle.kts`，Kotlin `2.0.21-KBA-010`，Kuikly 版本 `2.7.0-2.0.21-ohos`，额外声明 `ohosArm64()` target 与 `kspOhosArm64`。
- 两套文件是**平行副本**：任何一处新增依赖/target/插件，另一处必须同步；`Version.getKuiklyVersion()` 与 `getKuiklyOhosVersion()` 不可混用。
- `kuiklySqlite/build.gradle.kts` 中 `ohosArm64()` 默认被注释，走 ohos 通道时需按文件内注释解开。

## 编码约定

- 注释用中文，跟随所在文件既有风格；标识符、坐标、路径保持原样。
- 版本号集中管理：改版本改 `buildSrc/.../KotlinBuildVar.kt`，不要在模块脚本里散落字面量（compiler 模块因 Kotlin 版本不同，是自身独立的一套）。
- `gradle.properties` 中 `ksp.incremental=false`，KSP 输出全量重建，属预期。
- 平台最低版本：Android minSdk 21 / compileSdk 34，iOS deploymentTarget 14.1，Native 使用 `-Xallocator=std`。

## 变更前后必读

- `README.md` — 注解参考表与配置示例，改公共 API 后需同步更新。
- 发布相关：`maven-repo/` 结构 + `publishing-github.sh`，路径与 artifactId 大小写敏感（历史上出过 maven 路径敏感问题）。
- Git 提交由用户在对话中明确指示后才执行。
