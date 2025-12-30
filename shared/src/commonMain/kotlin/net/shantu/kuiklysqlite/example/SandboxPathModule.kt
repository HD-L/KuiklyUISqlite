package net.shantu.kuiklysqlite.example

import com.tencent.kuikly.core.module.Module

class SandboxPathModule : Module() {

    override fun moduleName(): String = MODULE_NAME

    companion object Companion {
        const val MODULE_NAME = "SandboxPathModule"
    }

    /**
     * 获取沙盒标准db路径
     * @param
     */
    fun getDatabasesDirectoryPath(): String {
        return toNative(
            false, "getDatabasesDirectoryPath", "", null, true
        ).toString()
    }

}