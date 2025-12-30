package net.shantu.kuiklysqlite.plugin

import com.google.devtools.ksp.gradle.KspExtension
import net.shantu.kuiklysqlite.compiler.plugin.BuildConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

open class KuiklySqliteExtension {
    var packageName: String = ""
    var srcDirs: List<String> = emptyList()
}

class KuiklySqlitePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 0. 注册 Extension
        val extension = project.extensions.create("kuiklysqlite", KuiklySqliteExtension::class.java)

        // 1. 不再主动 apply KSP，假设用户已经应用
        // project.pluginManager.apply("com.google.devtools.ksp")

        // 2. 配置 SourceSets (懒加载，只注册自定义目录)
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { ext ->
            ext.sourceSets.getByName("commonMain") { sourceSet ->
                sourceSet.kotlin.srcDir(project.provider {
                    if (extension.srcDirs.isNotEmpty()) {
                        println(">>> 📂 [KuiklySqlitePlugin] Registering custom source dirs: ${extension.srcDirs}")
                        extension.srcDirs
                    } else {
                        emptyList()
                    }
                })
            }
        }

        // 3. 添加依赖
        val compilerCoordinate = BuildConfig.COMPILER_COORDINATE
        project.dependencies.add("kspCommonMainMetadata", compilerCoordinate)

        // 5. 传递配置 & Clean 联动
        project.afterEvaluate {
            // 传递参数给 KSP
            val packageName = extension.packageName
            val srcDir = extension.srcDirs.firstOrNull()?.let { project.file(it).absolutePath }

            project.extensions.configure(KspExtension::class.java) { ksp ->
                if (packageName.isNotEmpty()) {
                    ksp.arg("kuikly.packageName", packageName)
                    println(">>> 🔧 [KuiklySqlitePlugin] Configured packageName: $packageName")
                }
                if (srcDir != null) {
                    ksp.arg("kuikly.srcDir", srcDir)
                    println(">>> 📂 [KuiklySqlitePlugin] Configured srcDir: $srcDir")
                }
            }
            
            // 【新增】联动 clean 任务
            if (extension.srcDirs.isNotEmpty()) {
                val cleanTask = project.tasks.findByName("clean")
                cleanTask?.doLast {
                    extension.srcDirs.forEach { dirPath ->
                        val dir = project.file(dirPath)
                        if (dir.exists()) {
                            println(">>> 🧹 [KuiklySqlitePlugin] Cleaning generated directory: ${dir.absolutePath}")
                            // 删除目录
                            dir.deleteRecursively()
                        }
                    }
                }
            }

            // 移除所有手动的任务依赖修复 (dependsOn)，避免干扰其他框架的构建顺序
            // 相信 KSP 插件自身的依赖管理能力
        }

        println(">>> ✅ [KuiklySqlitePlugin] 混合插件已加载，Compiler 依赖已注入: $compilerCoordinate")
    }
}
