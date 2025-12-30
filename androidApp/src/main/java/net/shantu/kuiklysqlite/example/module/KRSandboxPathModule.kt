package net.shantu.kuiklysqlite.example.module

import android.content.Context
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import java.io.File

class KRSandboxPathModule(val baseContext: Context) : KuiklyRenderBaseModule() {

    companion object Companion {
        const val MODULE_NAME = "SandboxPathModule"
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "getDatabasesDirectoryPath" -> getDatabasesDirectoryPath()
            else -> {
                super.call(method, params, callback)
            }
        }
    }
    private fun getDatabasesDirectoryPath(): String {
        File(baseContext.filesDir.parentFile, "databases").apply {
            // 确保目录存在（系统可能未自动创建）
            if (!exists()) mkdirs()
        }
        return File(baseContext.filesDir.parentFile, "databases").path
    }
}