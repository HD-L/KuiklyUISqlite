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

    // --- 异步 API ---
    // 在 SqlWorker 后台线程执行，回调经 driver.callbackDispatcher（或传入的 dispatcher）投递；
    // 失败时调用 onError，未提供则交给 driver.logger。其他查询可用 driver.async { ... } 组合。

    /** 生成的 DAO 所使用的连接 */
    val driver: SqlDriver

    fun insertAsync(
        entity: T,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Long) -> Unit,
    ) = driver.async({ insert(entity) }, onError, dispatcher, callback)

    fun updateAsync(
        entity: T,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ update(entity) }, onError, dispatcher, callback)

    fun deleteAsync(
        entity: T,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ delete(entity) }, onError, dispatcher, callback)

    fun deleteByIdAsync(
        id: Any,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ deleteById(id) }, onError, dispatcher, callback)

    fun batchInsertAsync(
        entities: List<T>,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ batchInsert(entities) }, onError, dispatcher, callback)

    fun batchUpdateAsync(
        entities: List<T>,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ batchUpdate(entities) }, onError, dispatcher, callback)

    fun batchDeleteAsync(
        entities: List<T>,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ batchDelete(entities) }, onError, dispatcher, callback)

    fun selectAllAsync(
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (List<T>) -> Unit,
    ) = driver.async({ selectAll() }, onError, dispatcher, callback)

    fun selectByIdAsync(
        id: Any,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (T?) -> Unit,
    ) = driver.async({ selectById(id) }, onError, dispatcher, callback)

    fun countAsync(
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Long) -> Unit,
    ) = driver.async({ count() }, onError, dispatcher, callback)

    fun existsAsync(
        id: Any,
        onError: ((Throwable) -> Unit)? = null,
        dispatcher: SqlCallbackDispatcher = driver.callbackDispatcher,
        callback: (Boolean) -> Unit,
    ) = driver.async({ exists(id) }, onError, dispatcher, callback)
}
