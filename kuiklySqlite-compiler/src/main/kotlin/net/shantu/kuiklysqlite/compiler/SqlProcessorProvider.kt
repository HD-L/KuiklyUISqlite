package net.shantu.kuiklysqlite.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

// 注意：必须实现 SymbolProcessorProvider 接口
class SqlProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        // 这里创建并返回你的 Processor
        return SqlProcessor(
            environment.codeGenerator,
            environment.logger,
            environment.options
        )
    }
}
