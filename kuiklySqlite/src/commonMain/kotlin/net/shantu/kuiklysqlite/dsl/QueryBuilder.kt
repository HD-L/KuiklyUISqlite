package net.shantu.kuiklysqlite.dsl

import net.shantu.kuiklysqlite.BaseDao

class QueryBuilder<T>(private val dao: BaseDao<T>) {
    private var rootCondition: Condition? = null
    private val orderByList = mutableListOf<String>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null

    /**
     * Add a condition to the query.
     * By default, multiple calls to where() are combined with AND.
     */
    fun where(condition: Condition): QueryBuilder<T> {
        rootCondition = if (rootCondition == null) {
            condition
        } else {
            // Default to AND when chaining where calls
            rootCondition!! and condition
        }
        return this
    }
    
    // Support varargs for convenience (Implicitly AND)
    fun where(vararg conditions: Condition): QueryBuilder<T> {
        conditions.forEach { where(it) }
        return this
    }
    
    // Explicit OR chaining
    fun orWhere(condition: Condition): QueryBuilder<T> {
        rootCondition = if (rootCondition == null) {
            condition
        } else {
            rootCondition!! or condition
        }
        return this
    }
    
    fun orderBy(column: Column<*>, ascending: Boolean = true): QueryBuilder<T> {
        // Validate column name to prevent injection (simple check)
        if (!column.name.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            throw IllegalArgumentException("Invalid column name: ${column.name}")
        }
        val direction = if (ascending) "ASC" else "DESC"
        orderByList.add("${column.name} $direction")
        return this
    }
    
    fun limit(limit: Int): QueryBuilder<T> {
        if (limit < 0) throw IllegalArgumentException("Limit must be >= 0")
        this.limitValue = limit
        return this
    }
    
    fun offset(offset: Int): QueryBuilder<T> {
        if (offset < 0) throw IllegalArgumentException("Offset must be >= 0")
        this.offsetValue = offset
        return this
    }
    
    fun count(): Long {
        // Build SQL but only use the WHERE clause
        val (whereClause, args) = buildWhereClause()
        return dao.count(whereClause, args)
    }

    fun find(): List<T> {
        val (whereClause, args) = buildWhereClause()
        val orderByClause = if (orderByList.isNotEmpty()) orderByList.joinToString(", ") else null

        return dao.selectByPage(
            whereClause = whereClause,
            args = args,
            orderBy = orderByClause,
            limit = limitValue,
            offset = offsetValue
        )
    }

    private fun buildWhereClause(): Pair<String, List<Any?>> {
        if (rootCondition == null) {
            return "" to emptyList()
        }
        
        val args = mutableListOf<Any?>()
        val sb = StringBuilder()
        
        recursiveBuild(rootCondition!!, sb, args)
        
        return sb.toString() to args
    }
    
    private fun recursiveBuild(condition: Condition, sb: StringBuilder, args: MutableList<Any?>) {
        when (condition) {
            is SimpleCondition -> {
                sb.append("${condition.column.name} ${condition.op.symbol} ?")
                args.add(condition.value)
            }
            is CompositeCondition -> {
                sb.append("(")
                recursiveBuild(condition.left, sb, args)
                sb.append(" ${condition.logic.symbol} ")
                recursiveBuild(condition.right, sb, args)
                sb.append(")")
            }
        }
    }
}
