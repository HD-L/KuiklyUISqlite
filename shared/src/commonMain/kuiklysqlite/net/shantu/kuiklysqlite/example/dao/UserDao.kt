package net.shantu.kuiklysqlite.example.dao

import kotlin.Any
import kotlin.Boolean
import kotlin.Deprecated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import net.shantu.kuiklysqlite.BaseDao
import net.shantu.kuiklysqlite.SqlDriver
import net.shantu.kuiklysqlite.SqlStatement
import net.shantu.kuiklysqlite.dsl.Column
import net.shantu.kuiklysqlite.dsl.Table
import net.shantu.kuiklysqlite.example.User

public class UserDao(
  private val driver: SqlDriver,
) : BaseDao<User> {
  private fun parseRow(stmt: SqlStatement): User = User(
    id = stmt.getColumnLong(0),
    phone = stmt.getColumnString(1),
    name = stmt.getColumnString(2),
    age = stmt.getColumnLong(3).toInt(),
    email = stmt.getColumnString(4),
    createTime = stmt.getColumnLong(5),
  )

  override fun initTable() {
    driver.execute("CREATE TABLE IF NOT EXISTS t_user (t_id INTEGER PRIMARY KEY AUTOINCREMENT, t_phone TEXT NOT NULL, t_name TEXT NOT NULL DEFAULT '', t_age INTEGER NOT NULL DEFAULT 0, t_email TEXT, t_create_time INTEGER NOT NULL DEFAULT CURRENT_TIMESTAMP)")
    driver.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_t_user_t_phone ON t_user (t_phone)")
    driver.execute("CREATE  INDEX IF NOT EXISTS idx_name_age ON t_user (t_name, t_age)")
    driver.execute("CREATE  INDEX IF NOT EXISTS idx_age_create_time ON t_user (t_age, t_create_time)")
  }

  override fun insert(entity: User): Long {
    val stmt =
        driver.prepare("INSERT INTO t_user (t_phone, t_name, t_age, t_email, t_create_time) VALUES (?, ?, ?, ?, ?)")
    try {
      stmt.bindString(index = 1, value = entity.phone)
      stmt.bindString(index = 2, value = entity.name)
      stmt.bindLong(index = 3, value = entity.age.toLong())
      stmt.bindString(index = 4, value = entity.email)
      stmt.bindLong(index = 5, value = entity.createTime)
      stmt.step()
      val insertId = driver.getLastInsertId()
      driver.notifyListeners("t_user")
      return insertId
    }
    finally {
      stmt.close()
    }
  }

  override fun update(entity: User): Boolean {
    val stmt =
        driver.prepare("UPDATE t_user SET t_phone = ?, t_name = ?, t_age = ?, t_email = ?, t_create_time = ? WHERE t_id = ?")
    try {
      stmt.bindString(index = 1, value = entity.phone)
      stmt.bindString(index = 2, value = entity.name)
      stmt.bindLong(index = 3, value = entity.age.toLong())
      stmt.bindString(index = 4, value = entity.email)
      stmt.bindLong(index = 5, value = entity.createTime)
      stmt.bindLong(index = 6, value = entity.id)
      stmt.step()
      val changes = driver.getChanges() > 0
      if (changes) driver.notifyListeners("t_user")
      return changes
    }
    finally {
      stmt.close()
    }
  }

  override fun delete(entity: User): Boolean {
    val stmt = driver.prepare("DELETE FROM t_user WHERE t_id = ?")
    try {
      stmt.bindLong(index = 1, value = entity.id)
      stmt.step()
      val changes = driver.getChanges() > 0
      if (changes) driver.notifyListeners("t_user")
      return changes
    }
    finally {
      stmt.close()
    }
  }

  override fun deleteById(id: Any): Boolean {
    val stmt = driver.prepare("DELETE FROM t_user WHERE t_id = ?")
    try {
      stmt.bindLong(index = 1, value = id as Long)
      stmt.step()
      val changes = driver.getChanges() > 0
      if (changes) driver.notifyListeners("t_user")
      return changes
    }
    finally {
      stmt.close()
    }
  }

  override fun selectAll(): List<User> {
    val list = mutableListOf<User>()
    val stmt =
        driver.prepare("SELECT t_id, t_phone, t_name, t_age, t_email, t_create_time FROM t_user")
    try {
      while (stmt.step()) {
        list.add(parseRow(stmt))
      }
    }
    finally {
      stmt.close()
    }
    return list
  }

  override fun selectById(id: Any): User? {
    val stmt =
        driver.prepare("SELECT t_id, t_phone, t_name, t_age, t_email, t_create_time FROM t_user WHERE t_id = ?")
    try {
      stmt.bindLong(index = 1, value = id as Long)
      if (stmt.step()) {
        return parseRow(stmt)
      } else {
        return null
      }
    }
    finally {
      stmt.close()
    }
  }

  override fun batchInsert(entities: List<User>): Boolean {
    if (entities.isEmpty()) return true
    driver.beginTransaction()
    val stmt =
        driver.prepare("INSERT INTO t_user (t_phone, t_name, t_age, t_email, t_create_time) VALUES (?, ?, ?, ?, ?)")
    try {
      for (entity in entities) {
        stmt.bindString(index = 1, value = entity.phone)
        stmt.bindString(index = 2, value = entity.name)
        stmt.bindLong(index = 3, value = entity.age.toLong())
        stmt.bindString(index = 4, value = entity.email)
        stmt.bindLong(index = 5, value = entity.createTime)
        stmt.step()
        stmt.reset()
      }
      driver.endTransaction()
      driver.notifyListeners("t_user")
      return true
    } catch (e: Exception) {
      driver.rollbackTransaction()
      throw e
    }
    finally {
      stmt.close()
    }
  }

  override fun batchUpdate(entities: List<User>): Boolean {
    if (entities.isEmpty()) return true
    driver.beginTransaction()
    val stmt =
        driver.prepare("UPDATE t_user SET t_phone = ?, t_name = ?, t_age = ?, t_email = ?, t_create_time = ? WHERE t_id = ?")
    try {
      for (entity in entities) {
        stmt.bindString(index = 1, value = entity.phone)
        stmt.bindString(index = 2, value = entity.name)
        stmt.bindLong(index = 3, value = entity.age.toLong())
        stmt.bindString(index = 4, value = entity.email)
        stmt.bindLong(index = 5, value = entity.createTime)
        stmt.bindLong(index = 6, value = entity.id)
        stmt.step()
        stmt.reset()
      }
      driver.endTransaction()
      driver.notifyListeners("t_user")
      return true
    } catch (e: Exception) {
      driver.rollbackTransaction()
      throw e
    }
    finally {
      stmt.close()
    }
  }

  override fun batchDelete(entities: List<User>): Boolean {
    if (entities.isEmpty()) return true
    driver.beginTransaction()
    val stmt = driver.prepare("DELETE FROM t_user WHERE t_id = ?")
    try {
      for (entity in entities) {
        stmt.bindLong(index = 1, value = entity.id)
        stmt.step()
        stmt.reset()
      }
      driver.endTransaction()
      driver.notifyListeners("t_user")
      return true
    } catch (e: Exception) {
      driver.rollbackTransaction()
      throw e
    }
    finally {
      stmt.close()
    }
  }

  override fun count(): Long {
    val stmt = driver.prepare("SELECT COUNT(*) FROM t_user")
    try {
      if (stmt.step()) {
        return stmt.getColumnLong(0)
      }
      return 0L
    }
    finally {
      stmt.close()
    }
  }

  override fun count(whereClause: String, args: List<Any?>): Long {
    val sb = StringBuilder("SELECT COUNT(*) FROM t_user")
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
  }

  override fun exists(id: Any): Boolean {
    val stmt = driver.prepare("SELECT 1 FROM t_user WHERE t_id = ? LIMIT 1")
    try {
      stmt.bindLong(index = 1, value = id as Long)
      return stmt.step()
    }
    finally {
      stmt.close()
    }
  }

  override fun clearTable() {
    driver.execute("DELETE FROM t_user")
    driver.notifyListeners("t_user")
  }

  override fun dropTable() {
    driver.execute("DROP TABLE IF EXISTS t_user")
    driver.notifyListeners("t_user")
  }

  override fun selectByPage(
    whereClause: String,
    args: List<Any?>,
    orderBy: String?,
    limit: Int?,
    offset: Int?,
  ): List<User> {
    val sb =
        StringBuilder("SELECT t_id, t_phone, t_name, t_age, t_email, t_create_time FROM t_user")
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
    val list = mutableListOf<User>()

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
  }

  @Deprecated("Use select() DSL or selectByPage instead")
  override fun executeSelect(whereClause: String, args: List<Any?>): List<User> =
      selectByPage(whereClause, args, null, null, null)

  public fun insertAsync(entity: User, callback: (result: Long) -> Unit) {
    net.shantu.kuiklysqlite.SqlWorker.execute(
        block = { insert(entity) },
        callback = callback
    )
  }

  public fun updateAsync(entity: User, callback: (result: Boolean) -> Unit) {
    net.shantu.kuiklysqlite.SqlWorker.execute(
        block = { update(entity) },
        callback = callback
    )
  }

  public fun deleteAsync(entity: User, callback: (result: Boolean) -> Unit) {
    net.shantu.kuiklysqlite.SqlWorker.execute(
        block = { delete(entity) },
        callback = callback
    )
  }
}

public object UserTable : Table("t_user") {
  public val id: Column<Long> = Column("t_id")

  public val phone: Column<String> = Column("t_phone")

  public val name: Column<String> = Column("t_name")

  public val age: Column<Int> = Column("t_age")

  public val email: Column<String> = Column("t_email")

  public val createTime: Column<Long> = Column("t_create_time")
}
