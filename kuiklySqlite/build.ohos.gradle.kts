plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("maven-publish")
    signing
}

// 定义生成的库名称（不带 lib 前缀和 .so 后缀）
val nativeLibName = "sqlite3"

kotlin {
    /* ---------------- Android JVM ---------------- */
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }/* ---------------- iOS (CocoaPods) ---------------- */
    cocoapods {
        homepage = "Link to sqlite3 module homepage"
        version = "1.0"
        ios.deploymentTarget = "14.1"

        framework {
            baseName = nativeLibName
            isStatic = false
        }
    }
//    ohos编译需要解开注释
    ohosArm64 {
        binaries.sharedLib {
        }
    }
    // 1. 保留原有iOS目标
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    /* ---------------- Native targets ---------------- */
    val nativeTargets = listOf(
        iosArm64(),
        iosX64(),
        iosSimulatorArm64(),
        //    ohos编译需要解开注释
        ohosArm64()
    )
    sourceSets {
        val commonMain by getting
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        nativeTargets.forEach { target ->
            target.compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        }
        val androidMain by getting {
            dependencies {
                implementation("net.java.dev.jna:jna:5.18.1@aar")
            }
        }
    }

    /* ---------------- cinterop ---------------- */
    nativeTargets.forEach { target ->
        target.compilations["main"].cinterops {
            create("sqlite3") {
                defFile = file("src/nativeMain/cinterop/sqlite3.def")
                packageName = "net.shantu.kuiklysqlite"
                // 只保留「真正必要」的 include
                includeDirs("src/nativeMain/c")
                // 【关键修改】针对 Android Native 目标，强制导出所有符号
                if (target.name.startsWith("androidNative")) {
                    // -fvisibility=default : 让所有函数符号在动态库中可见
                    // 这样 JNA 就能找到 h的方法，而不需要在 C 代码里加 JNA_EXPORT
                    extraOpts("-compiler-option", "-fvisibility=default")
                }
            }
        }
    }
}

group = "net.shantu.kuiklysqlite"
version = System.getenv("kuiklyBizVersion") ?: "1.0.0"

publishing {
    repositories {
        maven {
            url = uri("../maven-repo") // 本地输出目录
        }
    }
}

android {
    namespace = "net.shantu.kuiklysqlite"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 33
        // 【新增】配置 CMake 参数
        externalNativeBuild {
            cmake {
                // 这一行至关重要！代替了在 C 代码里写 __attribute__((visibility("default")))
                // 它告诉编译器：把所有符号都公开，这样 JNA 就能找到了。
                cFlags("-fvisibility=default")
            }
        }
    }
    // 【新增】指定 CMake 构建脚本路径
    externalNativeBuild {
        cmake {
            // 我们将在模块根目录创建一个 CMakeLists.txt
            path = file("src/nativeMain/cinterop/CMakeLists.txt")
        }
    }
    // 关联NDK（与KMP共用）
    ndkVersion = "25.1.8937393"  // 匹配你的NDK版本
}

fun getCommonCompilerArgs(): List<String> {
    return listOf(
        "-Xallocator=std"
    )
}