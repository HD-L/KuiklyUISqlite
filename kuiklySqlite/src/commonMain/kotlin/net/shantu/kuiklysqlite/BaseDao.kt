package net.shantu.kuiklysqlite

import net.shantu.kuiklysqlite.dsl.QueryBuilder

interface BaseDao<T> {
    fun initTable() // Added initTable to interface

    // CRUD
    fun insert(entity: T): Long
    fun update(entity: T): Boolean
    fun delete(entity: T): Boolean
    fun deleteById(id: Any): Boolean
    fun selectAll(): List<T>
    fun selectById(id: Any): T?
    
    // Batch Operations
    fun batchInsert(entities: List<T>): Boolean
    fun batchUpdate(entities: List<T>): Boolean
    fun batchDelete(entities: List<T>): Boolean

    // Lightweight & Ops
    fun count(): Long
    fun count(whereClause: String, args: List<Any?>): Long // Added count with filter
    fun exists(id: Any): Boolean
    fun clearTable()
    fun dropTable()

    // Advanced Query (DSL Support)
    // Deprecated direct usage, use QueryBuilder instead
    fun executeSelect(whereClause: String, args: List<Any?>): List<T>
    
    // Page & Sort (Used by DSL)
    fun selectByPage(
        whereClause: String, 
        args: List<Any?>, 
        orderBy: String? = null, 
        limit: Int? = null, 
        offset: Int? = null
    ): List<T>

    fun select(): QueryBuilder<T> {
        return QueryBuilder(this)
    }

}
