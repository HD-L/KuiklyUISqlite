package net.shantu.kuiklysqlite.compiler.model

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.ClassName

data class TableInfo(
    val tableName: String,
    val className: String,
    val packageName: String,
    val properties: List<KSPropertyDeclaration>,
    val primaryKey: KSPropertyDeclaration,
    val isAutoGenerate: Boolean,
    val entityType: ClassName,
    val containingFile: com.google.devtools.ksp.symbol.KSFile?,
    
    // Extended Metadata
    val columnInfoMap: Map<KSPropertyDeclaration, ColumnMeta>,
    val indices: List<IndexMeta>
)

data class ColumnMeta(
    val columnName: String,
    val defaultValue: String = "",
    val notNull: Boolean = false,
    val explicitType: String? = null // Store explicit ColumnType (as String like "TEXT", "INTEGER")
)

data class IndexMeta(
    val name: String,
    val columns: List<String>,
    val unique: Boolean
)
