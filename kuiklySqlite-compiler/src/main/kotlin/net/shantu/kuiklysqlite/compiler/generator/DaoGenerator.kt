package net.shantu.kuiklysqlite.compiler.generator

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import net.shantu.kuiklysqlite.compiler.helper.SqlTypeHelper
import net.shantu.kuiklysqlite.compiler.model.TableInfo

class DaoGenerator(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) {

    fun generate(tableInfo: TableInfo) {
        val daoClassName = "${tableInfo.className}Dao"
        val packageName = tableInfo.packageName

        val driverClass = ClassName("net.shantu.kuiklysqlite", "SqlDriver")
        val baseDaoClass = ClassName("net.shantu.kuiklysqlite", "BaseDao")
            .parameterizedBy(tableInfo.entityType)

        val typeSpec = TypeSpec.classBuilder(daoClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder().addParameter("driver", driverClass).build()
            )
            .addProperty(
                PropertySpec.builder("driver", driverClass)
                    .initializer("driver")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addSuperinterface(baseDaoClass)

        // Helper private method for row parsing
        generateParseRowMethod(typeSpec, tableInfo)

        generateInitTableMethod(typeSpec, tableInfo)
        generateInsertMethod(typeSpec, tableInfo)
        generateUpdateMethod(typeSpec, tableInfo)
        generateDeleteMethod(typeSpec, tableInfo)
        generateDeleteByIdMethod(typeSpec, tableInfo)
        generateSelectAllMethod(typeSpec, tableInfo)
        generateSelectByIdMethod(typeSpec, tableInfo)
        
        // New methods implementation
        generateBatchInsertMethod(typeSpec, tableInfo)
        generateBatchUpdateMethod(typeSpec, tableInfo)
        generateBatchDeleteMethod(typeSpec, tableInfo)
        generateCountMethod(typeSpec, tableInfo)
        generateCountWithFilterMethod(typeSpec, tableInfo)
        generateExistsMethod(typeSpec, tableInfo)
        generateClearTableMethod(typeSpec, tableInfo)
        generateDropTableMethod(typeSpec, tableInfo)
        generateSelectByPageMethod(typeSpec, tableInfo)

        // Deprecated method implementation
        generateExecuteSelectMethod(typeSpec, tableInfo)
        
        // Async & Reactive methods
        generateInsertAsyncMethod(typeSpec, tableInfo)
        generateUpdateAsyncMethod(typeSpec, tableInfo)
        generateDeleteAsyncMethod(typeSpec, tableInfo)

        // Generate Table Object for DSL
        val tableObjectSpec = generateTableObject(tableInfo)

        // File generation
        val fileSpec = FileSpec.builder(packageName, daoClassName)
            .addType(typeSpec.build())
            .addType(tableObjectSpec) // Add the Table object to the same file
            .build()
            
        // Custom Output Logic
        val srcDir = options["kuikly.srcDir"]
        if (!srcDir.isNullOrEmpty()) {
             val file = java.io.File(srcDir)
             // Use FileSpec.writeTo(File) which handles package structure
             fileSpec.writeTo(file)
             logger.info(">>> 💾 [DaoGenerator] Generated DAO to custom path: ${file.absolutePath}")
        } else {
             fileSpec.writeTo(codeGenerator, Dependencies(false, tableInfo.containingFile!!))
             logger.info(">>> ✅ Generated DAO: $packageName.$daoClassName")
        }
    }
    
    // ... existing helpers ...
    private fun getColumnName(tableInfo: TableInfo, prop: com.google.devtools.ksp.symbol.KSPropertyDeclaration): String {
        return tableInfo.columnInfoMap[prop]?.columnName ?: prop.simpleName.asString()
    }
    
    // --- Async Methods ---
    private fun generateInsertAsyncMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("insertAsync")
            .addParameter("entity", tableInfo.entityType)
            .addParameter(
                ParameterSpec.builder(
                    "callback",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.builder("result", Long::class.asTypeName()).build()),
                        returnType = Unit::class.asTypeName()
                    )
                ).build()
            )
            .addCode("""
                net.shantu.kuiklysqlite.SqlWorker.execute(
                    block = { insert(entity) },
                    callback = callback
                )
            """.trimIndent())
        typeBuilder.addFunction(func.build())
    }

    private fun generateUpdateAsyncMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("updateAsync")
            .addParameter("entity", tableInfo.entityType)
            .addParameter(
                ParameterSpec.builder(
                    "callback",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.builder("result", Boolean::class.asTypeName()).build()),
                        returnType = Unit::class.asTypeName()
                    )
                ).build()
            )
            .addCode("""
                net.shantu.kuiklysqlite.SqlWorker.execute(
                    block = { update(entity) },
                    callback = callback
                )
            """.trimIndent())
        typeBuilder.addFunction(func.build())
    }

    private fun generateDeleteAsyncMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("deleteAsync")
            .addParameter("entity", tableInfo.entityType)
            .addParameter(
                ParameterSpec.builder(
                    "callback",
                    LambdaTypeName.get(
                        parameters = listOf(ParameterSpec.builder("result", Boolean::class.asTypeName()).build()),
                        returnType = Unit::class.asTypeName()
                    )
                ).build()
            )
            .addCode("""
                net.shantu.kuiklysqlite.SqlWorker.execute(
                    block = { delete(entity) },
                    callback = callback
                )
            """.trimIndent())
        typeBuilder.addFunction(func.build())
    }
    
    // --- Private Helper: Parse Row ---
    private fun generateParseRowMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val stmtClass = ClassName("net.shantu.kuiklysqlite", "SqlStatement")
        val func = FunSpec.builder("parseRow")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("stmt", stmtClass)
            .returns(tableInfo.entityType)

        val constructorCall = CodeBlock.builder().add("return %T(\n", tableInfo.entityType).indent()
        tableInfo.properties.forEachIndexed { index, prop ->
            // Use safe column retrieval (TODO: enhance SqlTypeHelper to handle nulls more gracefully if needed)
            val getCode = SqlTypeHelper.getColumnMethod(prop, index)
            constructorCall.add("%L = %L,\n", prop.simpleName.asString(), getCode)
        }
        constructorCall.unindent().add(")\n")
        
        func.addCode(constructorCall.build())
        typeBuilder.addFunction(func.build())
    }

    private fun generateInitTableMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val columnDefs = tableInfo.properties.joinToString(", ") { prop ->
            val colName = getColumnName(tableInfo, prop)
            val typeName = prop.type.resolve().declaration.qualifiedName?.asString()
            val colMeta = tableInfo.columnInfoMap[prop]
            
            // Use explicit type if provided in annotation, otherwise infer from Kotlin type
            val sqlType = colMeta?.explicitType ?: SqlTypeHelper.getSqlType(typeName)

            val sb = StringBuilder("$colName $sqlType")
            
            if (prop == tableInfo.primaryKey) {
                if (tableInfo.isAutoGenerate) {
                    sb.append(" PRIMARY KEY AUTOINCREMENT")
                } else {
                    sb.append(" PRIMARY KEY")
                }
            } else {
                if (colMeta?.notNull == true) {
                    sb.append(" NOT NULL")
                }
                if (!colMeta?.defaultValue.isNullOrEmpty()) {
                    val defaultVal = colMeta?.defaultValue
                    // Quote string default values if explicit type is TEXT or inferred type is TEXT (and defaultVal isn't already quoted/expression)
                    // Simple heuristic: if type is TEXT and value doesn't start with ', quote it.
                    // Also check for keywords like CURRENT_TIMESTAMP which shouldn't be quoted.
                    val isText = sqlType.equals("TEXT", ignoreCase = true)
                    val isKeyword = defaultVal.equals("CURRENT_TIMESTAMP", ignoreCase = true) || 
                                    defaultVal.equals("NULL", ignoreCase = true)
                                    
                    if (isText && !isKeyword && !defaultVal!!.startsWith("'")) {
                        sb.append(" DEFAULT '$defaultVal'")
                    } else {
                        sb.append(" DEFAULT $defaultVal")
                    }
                }
            }
            sb.toString()
        }

        val createTableSql = "CREATE TABLE IF NOT EXISTS ${tableInfo.tableName} ($columnDefs)"
        
        // Generate Index SQLs
        val indexSqls = tableInfo.indices.map { index ->
            val uniqueStr = if (index.unique) "UNIQUE" else ""
            val colsStr = index.columns.joinToString(", ")
            "CREATE $uniqueStr INDEX IF NOT EXISTS ${index.name} ON ${tableInfo.tableName} ($colsStr)"
        }

        val func = FunSpec.builder("initTable")
            .addModifiers(KModifier.OVERRIDE) // Now override from BaseDao
            .addStatement("driver.execute(%S)", createTableSql)
            
        indexSqls.forEach { sql ->
            func.addStatement("driver.execute(%S)", sql)
        }
        
        typeBuilder.addFunction(func.build())
    }

    private fun generateInsertMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val insertProps = tableInfo.properties.filter { it != tableInfo.primaryKey || !tableInfo.isAutoGenerate }
        val columnNames = insertProps.joinToString(", ") { getColumnName(tableInfo, it) }
        val placeholders = insertProps.joinToString(", ") { "?" }
        val sql = "INSERT INTO ${tableInfo.tableName} ($columnNames) VALUES ($placeholders)"

        val func = FunSpec.builder("insert")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entity", tableInfo.entityType)
            .returns(Long::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")

        insertProps.forEachIndexed { index, prop ->
            val bindCode = SqlTypeHelper.getBindMethod(prop, "entity.${prop.simpleName.asString()}")
            func.addStatement("stmt.%L(index = %L, value = %L)", bindCode.first, index + 1, bindCode.second)
        }

        func.addStatement("stmt.step()")
        func.addStatement("val insertId = driver.getLastInsertId()")
        func.addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return insertId")

        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    private fun generateUpdateMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val updateProps = tableInfo.properties.filter { it != tableInfo.primaryKey }
        val setClause = updateProps.joinToString(", ") { "${getColumnName(tableInfo, it)} = ?" }
        val whereClause = "${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"
        val sql = "UPDATE ${tableInfo.tableName} SET $setClause WHERE $whereClause"

        val func = FunSpec.builder("update")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entity", tableInfo.entityType)
            .returns(Boolean::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")

        var bindIndex = 1
        updateProps.forEach { prop ->
            val bindCode = SqlTypeHelper.getBindMethod(prop, "entity.${prop.simpleName.asString()}")
            func.addStatement("stmt.%L(index = %L, value = %L)", bindCode.first, bindIndex++, bindCode.second)
        }
        val pkBind = SqlTypeHelper.getBindMethod(tableInfo.primaryKey, "entity.${tableInfo.primaryKey.simpleName.asString()}")
        func.addStatement("stmt.%L(index = %L, value = %L)", pkBind.first, bindIndex, pkBind.second)

        func.addStatement("stmt.step()")
        func.addStatement("val changes = driver.getChanges() > 0")
        func.addStatement("if (changes) driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return changes")

        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    private fun generateDeleteMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val sql = "DELETE FROM ${tableInfo.tableName} WHERE ${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"
        val func = FunSpec.builder("delete")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entity", tableInfo.entityType)
            .returns(Boolean::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")

        val pkBind = SqlTypeHelper.getBindMethod(tableInfo.primaryKey, "entity.${tableInfo.primaryKey.simpleName.asString()}")
        func.addStatement("stmt.%L(index = 1, value = %L)", pkBind.first, pkBind.second)

        func.addStatement("stmt.step()")
        func.addStatement("val changes = driver.getChanges() > 0")
        func.addStatement("if (changes) driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return changes")

        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    private fun generateDeleteByIdMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val sql = "DELETE FROM ${tableInfo.tableName} WHERE ${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"
        val func = FunSpec.builder("deleteById")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("id", Any::class)
            .returns(Boolean::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")

        val pkType = tableInfo.primaryKey.type.resolve().declaration.qualifiedName?.asString()
        val castId = when (pkType) {
            "kotlin.Long" -> "id as Long"
            "kotlin.Int" -> "(id as Number).toLong()"
            "kotlin.String" -> "id as String"
            else -> "id.toString()"
        }
        val bindMethod = if (pkType == "kotlin.String") "bindString" else "bindLong"
        func.addStatement("stmt.$bindMethod(index = 1, value = %L)", castId)

        func.addStatement("stmt.step()")
        func.addStatement("val changes = driver.getChanges() > 0")
        func.addStatement("if (changes) driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return changes")

        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    private fun generateSelectAllMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val columnNames = tableInfo.properties.joinToString(", ") { getColumnName(tableInfo, it) }
        val sql = "SELECT $columnNames FROM ${tableInfo.tableName}"

        val func = FunSpec.builder("selectAll")
            .addModifiers(KModifier.OVERRIDE)
            .returns(ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
            .addStatement("val list = mutableListOf<%T>()", tableInfo.entityType)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            .beginControlFlow("while (stmt.step())")
            .addStatement("list.add(parseRow(stmt))") // Use helper
            .endControlFlow()
            .endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()
            .addStatement("return list")

        typeBuilder.addFunction(func.build())
    }

    private fun generateSelectByIdMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val columnNames = tableInfo.properties.joinToString(", ") { getColumnName(tableInfo, it) }
        val sql = "SELECT $columnNames FROM ${tableInfo.tableName} WHERE ${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"

        val func = FunSpec.builder("selectById")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("id", Any::class)
            .returns(tableInfo.entityType.copy(nullable = true))
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")

        val pkType = tableInfo.primaryKey.type.resolve().declaration.qualifiedName?.asString()
        val castId = when (pkType) {
            "kotlin.Long" -> "id as Long"
            "kotlin.Int" -> "(id as Number).toLong()"
            "kotlin.String" -> "id as String"
            else -> "id.toString()"
        }
        val bindMethod = if (pkType == "kotlin.String") "bindString" else "bindLong"
        func.addStatement("stmt.$bindMethod(index = 1, value = %L)", castId)

        func.beginControlFlow("if (stmt.step())")
            .addStatement("return parseRow(stmt)") // Use helper
            .nextControlFlow("else")
            .addStatement("return null")
            .endControlFlow()

        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    // --- New Batch Methods ---
    private fun generateBatchInsertMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val insertProps = tableInfo.properties.filter { it != tableInfo.primaryKey || !tableInfo.isAutoGenerate }
        val columnNames = insertProps.joinToString(", ") { getColumnName(tableInfo, it) }
        val placeholders = insertProps.joinToString(", ") { "?" }
        val sql = "INSERT INTO ${tableInfo.tableName} ($columnNames) VALUES ($placeholders)"

        val func = FunSpec.builder("batchInsert")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entities", ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
            .returns(Boolean::class)
            .addStatement("if (entities.isEmpty()) return true")
            .addStatement("driver.beginTransaction()")
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            
        func.beginControlFlow("for (entity in entities)")
        insertProps.forEachIndexed { index, prop ->
            val bindCode = SqlTypeHelper.getBindMethod(prop, "entity.${prop.simpleName.asString()}")
            func.addStatement("stmt.%L(index = %L, value = %L)", bindCode.first, index + 1, bindCode.second)
        }
        func.addStatement("stmt.step()")
        func.addStatement("stmt.reset()") // Important for reuse
        func.endControlFlow() // for
        
        func.addStatement("driver.endTransaction()")
        func.addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return true")
        
        func.nextControlFlow("catch (e: Exception)")
        func.addStatement("driver.rollbackTransaction()")
        func.addStatement("throw e")
        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }
    
    private fun generateBatchUpdateMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val updateProps = tableInfo.properties.filter { it != tableInfo.primaryKey }
        val setClause = updateProps.joinToString(", ") { "${getColumnName(tableInfo, it)} = ?" }
        val whereClause = "${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"
        val sql = "UPDATE ${tableInfo.tableName} SET $setClause WHERE $whereClause"

        val func = FunSpec.builder("batchUpdate")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entities", ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
            .returns(Boolean::class)
            .addStatement("if (entities.isEmpty()) return true")
            .addStatement("driver.beginTransaction()")
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            
        func.beginControlFlow("for (entity in entities)")
        var bindIndex = 1
        updateProps.forEach { prop ->
            val bindCode = SqlTypeHelper.getBindMethod(prop, "entity.${prop.simpleName.asString()}")
            func.addStatement("stmt.%L(index = %L, value = %L)", bindCode.first, bindIndex++, bindCode.second)
        }
        val pkBind = SqlTypeHelper.getBindMethod(tableInfo.primaryKey, "entity.${tableInfo.primaryKey.simpleName.asString()}")
        func.addStatement("stmt.%L(index = %L, value = %L)", pkBind.first, bindIndex, pkBind.second)
        
        func.addStatement("stmt.step()")
        func.addStatement("stmt.reset()")
        func.endControlFlow() // for
        
        func.addStatement("driver.endTransaction()")
        func.addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return true")
        
        func.nextControlFlow("catch (e: Exception)")
        func.addStatement("driver.rollbackTransaction()")
        func.addStatement("throw e")
        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    private fun generateBatchDeleteMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val sql = "DELETE FROM ${tableInfo.tableName} WHERE ${getColumnName(tableInfo, tableInfo.primaryKey)} = ?"
        val func = FunSpec.builder("batchDelete")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("entities", ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
            .returns(Boolean::class)
            .addStatement("if (entities.isEmpty()) return true")
            .addStatement("driver.beginTransaction()")
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            
        func.beginControlFlow("for (entity in entities)")
        val pkBind = SqlTypeHelper.getBindMethod(tableInfo.primaryKey, "entity.${tableInfo.primaryKey.simpleName.asString()}")
        func.addStatement("stmt.%L(index = 1, value = %L)", pkBind.first, pkBind.second)
        func.addStatement("stmt.step()")
        func.addStatement("stmt.reset()")
        func.endControlFlow() // for
        
        func.addStatement("driver.endTransaction()")
        func.addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        func.addStatement("return true")
        
        func.nextControlFlow("catch (e: Exception)")
        func.addStatement("driver.rollbackTransaction()")
        func.addStatement("throw e")
        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()

        typeBuilder.addFunction(func.build())
    }

    // --- Ops Methods ---
    private fun generateCountMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val sql = "SELECT COUNT(*) FROM ${tableInfo.tableName}"
        val func = FunSpec.builder("count")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Long::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            .beginControlFlow("if (stmt.step())")
            .addStatement("return stmt.getColumnLong(0)")
            .endControlFlow()
            .addStatement("return 0L")
            .endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()
        typeBuilder.addFunction(func.build())
    }

    private fun generateCountWithFilterMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("count")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("whereClause", String::class)
            .addParameter("args", ClassName("kotlin.collections", "List").parameterizedBy(Any::class.asTypeName().copy(nullable = true)))
            .returns(Long::class)
        
        val baseSql = "SELECT COUNT(*) FROM ${tableInfo.tableName}"
        
        func.addCode("""
            val sb = StringBuilder(%S)
            if (whereClause.isNotBlank()) {
                sb.append(" WHERE ").append(whereClause)
            }
            val stmt = driver.prepare(sb.toString())
            try {
                // Bind args
                args.forEachIndexed { index, arg ->
                    val i = index + 1
                    if (arg == null) {
                        stmt.bindNull(i)
                    } else if (arg is Long) {
                        stmt.bindLong(i, arg)
                    } else if (arg is Int) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is Short) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is Byte) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is String) {
                        stmt.bindString(i, arg)
                    } else if (arg is Boolean) {
                        stmt.bindLong(i, if (arg) 1L else 0L)
                    } else if (arg is Double) {
                        stmt.bindDouble(i, arg)
                    } else if (arg is Float) {
                        stmt.bindDouble(i, arg.toDouble())
                    } else if (arg is ByteArray) {
                        stmt.bindBlob(i, arg)
                    } else {
                        stmt.bindString(i, arg.toString())
                    }
                }
                
                if (stmt.step()) {
                    return stmt.getColumnLong(0)
                }
                return 0L
            } finally {
                stmt.close()
            }
        """.trimIndent(), baseSql)
        
        typeBuilder.addFunction(func.build())
    }

    private fun generateExistsMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val sql = "SELECT 1 FROM ${tableInfo.tableName} WHERE ${getColumnName(tableInfo, tableInfo.primaryKey)} = ? LIMIT 1"
        val func = FunSpec.builder("exists")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("id", Any::class)
            .returns(Boolean::class)
            .addStatement("val stmt = driver.prepare(%S)", sql)
            .beginControlFlow("try")
            
        // Bind ID (reuse logic if possible or copy)
        val pkType = tableInfo.primaryKey.type.resolve().declaration.qualifiedName?.asString()
        val castId = when (pkType) {
            "kotlin.Long" -> "id as Long"
            "kotlin.Int" -> "(id as Number).toLong()"
            "kotlin.String" -> "id as String"
            else -> "id.toString()"
        }
        val bindMethod = if (pkType == "kotlin.String") "bindString" else "bindLong"
        func.addStatement("stmt.$bindMethod(index = 1, value = %L)", castId)
        
        func.addStatement("return stmt.step()")
        func.endControlFlow()
            .beginControlFlow("finally")
            .addStatement("stmt.close()")
            .endControlFlow()
        typeBuilder.addFunction(func.build())
    }

    private fun generateClearTableMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("clearTable")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("driver.execute(%S)", "DELETE FROM ${tableInfo.tableName}")
            .addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        typeBuilder.addFunction(func.build())
    }

    private fun generateDropTableMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("dropTable")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("driver.execute(%S)", "DROP TABLE IF EXISTS ${tableInfo.tableName}")
            .addStatement("driver.notifyListeners(%S)", tableInfo.tableName)
        typeBuilder.addFunction(func.build())
    }

    // --- Page & DSL Support ---
    private fun generateSelectByPageMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val columnNames = tableInfo.properties.joinToString(", ") { getColumnName(tableInfo, it) }
        
        val func = FunSpec.builder("selectByPage")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("whereClause", String::class)
            .addParameter("args", ClassName("kotlin.collections", "List").parameterizedBy(Any::class.asTypeName().copy(nullable = true)))
            .addParameter(ParameterSpec.builder("orderBy", String::class.asTypeName().copy(nullable = true)).build())
            .addParameter(ParameterSpec.builder("limit", Int::class.asTypeName().copy(nullable = true)).build())
            .addParameter(ParameterSpec.builder("offset", Int::class.asTypeName().copy(nullable = true)).build())
            .returns(ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
        
        val baseSql = "SELECT $columnNames FROM ${tableInfo.tableName}"
        
        func.addCode("""
            val sb = StringBuilder(%S)
            if (whereClause.isNotEmpty()) {
                sb.append(" WHERE ").append(whereClause)
            }
            if (orderBy != null) {
                sb.append(" ORDER BY ").append(orderBy)
            }
            if (limit != null) {
                sb.append(" LIMIT ").append(limit)
            }
            if (offset != null) {
                sb.append(" OFFSET ").append(offset)
            }
            
            val stmt = driver.prepare(sb.toString())
            val list = mutableListOf<%T>()
            
            try {
                // Bind args
                args.forEachIndexed { index, arg ->
                    val i = index + 1
                    if (arg == null) {
                        stmt.bindNull(i)
                    } else if (arg is Long) {
                        stmt.bindLong(i, arg)
                    } else if (arg is Int) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is Short) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is Byte) {
                        stmt.bindLong(i, arg.toLong())
                    } else if (arg is String) {
                        stmt.bindString(i, arg)
                    } else if (arg is Boolean) {
                        stmt.bindLong(i, if (arg) 1L else 0L)
                    } else if (arg is Double) {
                        stmt.bindDouble(i, arg)
                    } else if (arg is Float) {
                        stmt.bindDouble(i, arg.toDouble())
                    } else if (arg is ByteArray) {
                        stmt.bindBlob(i, arg)
                    } else {
                        stmt.bindString(i, arg.toString())
                    }
                }
                
                while (stmt.step()) {
                    list.add(parseRow(stmt))
                }
            } finally {
                stmt.close()
            }
            return list
        """.trimIndent(), baseSql, tableInfo.entityType)
        
        typeBuilder.addFunction(func.build())
    }

    private fun generateExecuteSelectMethod(typeBuilder: TypeSpec.Builder, tableInfo: TableInfo) {
        val func = FunSpec.builder("executeSelect")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(AnnotationSpec.builder(Deprecated::class).addMember("%S", "Use select() DSL or selectByPage instead").build())
            .addParameter("whereClause", String::class)
            .addParameter("args", ClassName("kotlin.collections", "List").parameterizedBy(Any::class.asTypeName().copy(nullable = true)))
            .returns(ClassName("kotlin.collections", "List").parameterizedBy(tableInfo.entityType))
            .addStatement("return selectByPage(whereClause, args, null, null, null)")
            
        typeBuilder.addFunction(func.build())
    }

    private fun generateTableObject(tableInfo: TableInfo): TypeSpec {
        val tableClassName = "${tableInfo.className}Table"
        val tableSuperClass = ClassName("net.shantu.kuiklysqlite.dsl", "Table")
        val columnClass = ClassName("net.shantu.kuiklysqlite.dsl", "Column")

        val typeSpec = TypeSpec.objectBuilder(tableClassName)
            .superclass(tableSuperClass)
            .addSuperclassConstructorParameter("%S", tableInfo.tableName)

        tableInfo.properties.forEach { prop ->
            val propName = prop.simpleName.asString()
            // Use property type directly
            val typeName = prop.type.resolve().toClassName()
            
            val columnType = columnClass.parameterizedBy(typeName)

            // Use the database column name for the Column object
            val dbColumnName = getColumnName(tableInfo, prop)

            typeSpec.addProperty(
                PropertySpec.builder(propName, columnType)
                    .initializer("%T(%S)", columnClass, dbColumnName) // Map property to DB column name
                    .build()
            )
        }
        return typeSpec.build()
    }
}
