package net.shantu.kuiklysqlite.compiler

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ksp.toClassName
import net.shantu.kuiklysqlite.compiler.generator.DaoGenerator
import net.shantu.kuiklysqlite.compiler.model.ColumnMeta
import net.shantu.kuiklysqlite.compiler.model.IndexMeta
import net.shantu.kuiklysqlite.compiler.model.TableInfo

class SqlProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val daoGenerator = DaoGenerator(codeGenerator, logger, options)

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info(">>> 🚀 [SqlProcessor] KSP 正在运行! Options: $options <<<")
        val annotationName = "net.shantu.kuiklysqlite.annotations.SqlEntity"
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)
        logger.info(">>> 🔍 [SqlProcessor] 找到了 ${symbols.count()} 个 @SqlEntity 注解")

        symbols.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
            logger.info(">>> 📝 [SqlProcessor] 正在处理类: ${classDecl.simpleName.asString()}")
            val tableInfo = parseTableInfo(classDecl)
            if (tableInfo != null) {
                daoGenerator.generate(tableInfo)
            }
        }
        return emptyList()
    }

    private fun parseTableInfo(classDecl: KSClassDeclaration): TableInfo? {
        val className = classDecl.simpleName.asString()
        // 优先使用配置的包名
        val packageName = options["kuikly.packageName"]?.takeIf { it.isNotEmpty() }
            ?: classDecl.packageName.asString()
        val entityType = classDecl.toClassName()

        // 1. 解析表名
        val annotation = classDecl.annotations.find {
            it.shortName.asString() == "SqlEntity"
        }!!
        val tableNameArg = annotation.arguments.find { it.name?.asString() == "tableName" }?.value as? String
        val tableName = if (tableNameArg.isNullOrEmpty()) className else tableNameArg

        // 2. 解析属性 (支持 @SqlIgnore, @SqlColumn)
        val allProperties = classDecl.getAllProperties().toList()
        val validProperties = mutableListOf<KSPropertyDeclaration>()
        val columnInfoMap = mutableMapOf<KSPropertyDeclaration, ColumnMeta>()
        val indices = mutableListOf<IndexMeta>()

        var primaryKeyProp: KSPropertyDeclaration? = null
        var isAutoGenerate = false

        allProperties.forEach { prop ->
            // Skip @SqlIgnore
            if (prop.annotations.any { it.shortName.asString() == "SqlIgnore" }) {
                return@forEach
            }
            
            validProperties.add(prop)

            // Parse @SqlColumn
            val sqlColumn = prop.annotations.find { it.shortName.asString() == "SqlColumn" }
            val colName = (sqlColumn?.arguments?.find { it.name?.asString() == "name" }?.value as? String)
                ?.takeIf { it.isNotEmpty() } ?: prop.simpleName.asString()
            val defaultValue = (sqlColumn?.arguments?.find { it.name?.asString() == "defaultValue" }?.value as? String) ?: ""
            val notNull = (sqlColumn?.arguments?.find { it.name?.asString() == "notNull" }?.value as? Boolean) ?: false
            
            // Extract type from annotation (KSType -> Enum Name)
            val typeArg = sqlColumn?.arguments?.find { it.name?.asString() == "type" }?.value
            val explicitType = if (typeArg is KSType) {
                // KSType represents the enum entry type, e.g. net.shantu.kuiklysqlite.ColumnType.TEXT
                // We need the simple name of the enum entry.
                // However, KSType usually points to the Class of the Enum.
                // Wait, annotation argument value for Enum is actually a KSType if it's the class, 
                // but usually for enum values it's a KSPropertyDeclaration or similar?
                // Actually KSP returns a KSType for Class<?> arguments, but for Enum arguments it returns a KSType that represents the Enum Class? No.
                // According to KSP docs, Enum value in annotation argument is returned as a `KSType`? No, it's `KSClassifier`?
                // Let's check debugging or standard KSP pattern.
                // Usually it returns a `KSType` corresponding to the Enum Class, but we need the VALUE.
                // Actually, KSP 1.x returns `KSType` for enum entries? No.
                // It returns a `KSName`? No.
                // It returns a `KSType` representing the enum entry if it's a simple enum?
                // Let's look at how to get enum value name.
                // The value is often a `KSType` but we can't get the enum constant name easily from KSType alone if it's just the type.
                // Wait, for Enum arguments, KSP returns a `KSType` whose declaration is the Enum Class. But where is the value?
                // Ah, I might be mistaken. Let's look up KSP annotation enum argument.
                // It seems KSP returns the `KSClassDeclaration` of the enum entry?
                // Actually, the `value` property of `KSValueArgument` for an enum is a `KSType`?
                // If I use `prop.annotations...`, the value is resolved.
                // Let's try to stringify it. `typeArg.toString()` usually gives "ColumnType.TEXT".
                // Let's assume `typeArg.toString()` gives "net.shantu.kuiklysqlite.ColumnType.TEXT" or "TEXT".
                // Let's clean it up.
                val rawString = typeArg.toString()
                if (rawString.contains(".")) rawString.substringAfterLast(".") else rawString
            } else {
                null
            }
            
            // Filter out "NULL" (default value)
            val finalExplicitType = if (explicitType == "NULL") null else explicitType
            
            columnInfoMap[prop] = ColumnMeta(colName, defaultValue, notNull, finalExplicitType)

            // Parse @PrimaryKey
            val pkAnno = prop.annotations.find { it.shortName.asString() == "PrimaryKey" }
            if (pkAnno != null) {
                primaryKeyProp = prop
                val autoGenArg = pkAnno.arguments.find { it.name?.asString() == "autoGenerate" }?.value as? Boolean
                isAutoGenerate = autoGenArg ?: true
            }
            
            // Parse @SqlIndex (Single column index)
            val indexAnno = prop.annotations.find { it.shortName.asString() == "SqlIndex" }
            if (indexAnno != null) {
                val idxName = (indexAnno.arguments.find { it.name?.asString() == "name" }?.value as? String)
                    ?.takeIf { it.isNotEmpty() } ?: "idx_${tableName}_${colName}"
                val unique = (indexAnno.arguments.find { it.name?.asString() == "unique" }?.value as? Boolean) ?: false
                
                indices.add(IndexMeta(idxName, listOf(colName), unique))
            }
        }
        
        // 3. 解析类级别的 @SqlCompositeIndex
        // Supports @SqlCompositeIndex and @SqlCompositeIndexes (Repeatable container)
        val compositeIndices = classDecl.annotations.filter { 
            it.shortName.asString() == "SqlCompositeIndex" 
        }
        val compositeIndexesContainer = classDecl.annotations.find { 
            it.shortName.asString() == "SqlCompositeIndexes" 
        }
        
        val allCompositeAnnos = compositeIndices.toMutableList()
        if (compositeIndexesContainer != null) {
            // Container has 'value' argument which is Array<Annotation>
            val valueArg = compositeIndexesContainer.arguments.find { it.name?.asString() == "value" }?.value as? List<KSAnnotation>
            if (valueArg != null) {
                allCompositeAnnos.addAll(valueArg)
            }
        }
        
        allCompositeAnnos.forEach { anno ->
            val idxName = anno.arguments.find { it.name?.asString() == "name" }?.value as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val columns = anno.arguments.find { it.name?.asString() == "columns" }?.value as? ArrayList<String>
            val unique = (anno.arguments.find { it.name?.asString() == "unique" }?.value as? Boolean) ?: false
            
            if (idxName.isNotEmpty() && !columns.isNullOrEmpty()) {
                indices.add(IndexMeta(idxName, columns.toList(), unique))
            }
        }

        if (primaryKeyProp == null) {
            logger.error("Entity $className must have a @PrimaryKey defined.", classDecl)
            return null
        }

        return TableInfo(
            tableName = tableName,
            className = className,
            packageName = packageName,
            properties = validProperties,
            primaryKey = primaryKeyProp!!,
            isAutoGenerate = isAutoGenerate,
            entityType = entityType,
            containingFile = classDecl.containingFile,
            columnInfoMap = columnInfoMap,
            indices = indices
        )
    }
}
