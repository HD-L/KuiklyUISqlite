plugins {
    kotlin("jvm") version "1.9.20"
    `java-gradle-plugin` // 【新增】开启 Gradle 插件开发支持
    `maven-publish`      // 【新增】我们需要发布坐标，以便插件能引用自己
}

group = "net.shantu.kuiklySqlite"           // 【关键】必须定义 Group
version = "1.0.0"              // 【关键】必须定义 Version

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    // --- 原有的 KSP 依赖 ---
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.squareup:kotlinpoet-ksp:1.14.2")

    // --- 【新增】Gradle 插件开发依赖 ---
    // 引入 Kotlin Gradle 插件 API (compileOnly, 因为运行时宿主环境会有)
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    // 引入 KSP Gradle 插件 API (compileOnly)
    compileOnly("com.google.devtools.ksp:symbol-processing-gradle-plugin:1.9.20-1.0.14")
    // 引入 Gradle API
    compileOnly(gradleApi())
}

// 【新增】定义插件 ID
gradlePlugin {
    plugins {
        create("kuiklySqlitePlugin") {
            id = "net.shantu.kuiklysqlite.plugin" // 你的插件 ID
            implementationClass = "net.shantu.kuiklysqlite.plugin.KuiklySqlitePlugin" // 下一步要写的类名
        }
    }
}
// 1. 定义生成的源码路径
val generatedDir = layout.buildDirectory.dir("generated/source/buildConfig")

// 2. 注册一个生成 BuildConfig 的任务
val generateBuildConfig by tasks.registering {
    // 声明输入属性：只要这些变了，任务就会重新执行
    inputs.property("group", project.group)
    inputs.property("version", project.version)
    inputs.property("name", project.name) // 模块名，通常是 kuiklySqlite-compiler

    // 声明输出目录
    outputs.dir(generatedDir)

    doLast {
        val group = inputs.properties["group"]
        val version = inputs.properties["version"]
        val name = inputs.properties["name"]

        // 自动拼接 Maven 坐标
        val coordinate = "$group:$name:$version"

        // 目标文件：必须和你的 Plugin 类在同一个包下，或者你指定的任何包
        // 这里假设你的 Plugin 在 net.shantu.kuiklysqlite.compiler.plugin 包下
        val file = generatedDir.get().asFile.resolve("net/shantu/kuiklysqlite/compiler/plugin/BuildConfig.kt")

        file.parentFile.mkdirs()
        file.writeText("""
            package net.shantu.kuiklysqlite.compiler.plugin

            // 由 Gradle 自动生成，请勿修改
            internal object BuildConfig {
                const val COMPILER_COORDINATE = "$coordinate"
                const val VERSION = "$version"
            }
        """.trimIndent())
    }
}

// 3. 将生成的目录注册为 Kotlin 源码目录
kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildConfig)
    }
}