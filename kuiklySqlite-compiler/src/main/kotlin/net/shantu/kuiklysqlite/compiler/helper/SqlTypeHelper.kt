package net.shantu.kuiklysqlite.compiler.helper

import com.google.devtools.ksp.symbol.KSPropertyDeclaration

object SqlTypeHelper {

    fun getSqlType(typeName: String?): String {
        return when (typeName) {
            "kotlin.Int", "kotlin.Long", "kotlin.Boolean" -> "INTEGER"
            "kotlin.String" -> "TEXT"
            "kotlin.Double", "kotlin.Float" -> "REAL"
            "kotlin.ByteArray" -> "BLOB"
            else -> "TEXT"
        }
    }

    fun getColumnMethod(prop: KSPropertyDeclaration, index: Int): String {
        val typeName = prop.type.resolve().declaration.qualifiedName?.asString()
        return when (typeName) {
            "kotlin.Int" -> "stmt.getColumnLong($index).toInt()"
            "kotlin.Long" -> "stmt.getColumnLong($index)"
            "kotlin.String" -> "stmt.getColumnString($index)"
            "kotlin.Double" -> "stmt.getColumnDouble($index)"
            "kotlin.Float" -> "stmt.getColumnDouble($index).toFloat()"
            "kotlin.Boolean" -> "stmt.getColumnLong($index) == 1L"
            "kotlin.ByteArray" -> "stmt.getColumnBlob($index)"
            else -> "stmt.getColumnString($index)"
        }
    }

    fun getBindMethod(
        prop: KSPropertyDeclaration, valueAccess: String
    ): Pair<String, String> {
        val typeName = prop.type.resolve().declaration.qualifiedName?.asString()
        return when (typeName) {
            "kotlin.Int" -> "bindLong" to "$valueAccess.toLong()"
            "kotlin.Long" -> "bindLong" to valueAccess
            "kotlin.String" -> "bindString" to valueAccess
            "kotlin.Double" -> "bindDouble" to valueAccess
            "kotlin.Float" -> "bindDouble" to "$valueAccess.toDouble()"
            "kotlin.Boolean" -> "bindLong" to "if ($valueAccess) 1L else 0L"
            "kotlin.ByteArray" -> "bindBlob" to valueAccess
            else -> "bindString" to "$valueAccess.toString()"
        }
    }
}
