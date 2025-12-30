package net.shantu.kuiklysqlite.example

import net.shantu.kuiklysqlite.ColumnType
import net.shantu.kuiklysqlite.annotations.PrimaryKey
import net.shantu.kuiklysqlite.annotations.SqlColumn
import net.shantu.kuiklysqlite.annotations.SqlCompositeIndex
import net.shantu.kuiklysqlite.annotations.SqlCompositeIndexes
import net.shantu.kuiklysqlite.annotations.SqlEntity
import net.shantu.kuiklysqlite.annotations.SqlIgnore
import net.shantu.kuiklysqlite.annotations.SqlIndex


@SqlEntity(tableName = "t_user")
@SqlCompositeIndex(
    name = "idx_name_age",
    columns = ["t_name", "t_age"],
    unique = false
)
@SqlCompositeIndex(
    name = "idx_age_create_time",
    columns = ["t_age", "t_create_time"],
    unique = false
)
data class User (
    @PrimaryKey(autoGenerate = true)
    @SqlColumn(
        name = "t_id",
        notNull = true,
        type = ColumnType.INTEGER
    )
    val id: Long = 0,

    @SqlIndex(unique = true)
    @SqlColumn(
        name = "t_phone",
        notNull = true,
        type = ColumnType.TEXT
    )
    val phone: String,

    @SqlColumn(
        name = "t_name",
        notNull = true,
        defaultValue = "''",
        type = ColumnType.TEXT
    )
    val name: String,

    @SqlColumn(
        name = "t_age",
        notNull = true,
        defaultValue = "0",
        type = ColumnType.INTEGER
    )
    val age: Int,

    @SqlColumn(
        name = "t_email",
        notNull = false,
        type = ColumnType.TEXT
    )
    val email: String? = null,

    @SqlColumn(
        name = "t_create_time",
        notNull = true,
        defaultValue = "CURRENT_TIMESTAMP",
        type = ColumnType.INTEGER
    )
    val createTime: Long = 0,

    @SqlIgnore
    val avatar: ByteArray? = null
)