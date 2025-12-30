package net.shantu.kuiklysqlite.dsl

sealed class Op(val symbol: String) {
    object Eq : Op("=")
    object Neq : Op("<>")
    object Gt : Op(">")
    object Lt : Op("<")
    object Gte : Op(">=")
    object Lte : Op("<=")
    object Like : Op("LIKE")
}

// Sealed interface for condition tree
sealed interface Condition

// Leaf node: Simple condition (column op value)
data class SimpleCondition(val column: Column<*>, val op: Op, val value: Any?) : Condition

// Composite node: AND/OR logic
data class CompositeCondition(
    val left: Condition,
    val right: Condition,
    val logic: Logic
) : Condition {
    enum class Logic(val symbol: String) {
        AND("AND"),
        OR("OR")
    }
}

// Infix functions for logic combination
infix fun Condition.and(other: Condition): Condition = CompositeCondition(this, other, CompositeCondition.Logic.AND)
infix fun Condition.or(other: Condition): Condition = CompositeCondition(this, other, CompositeCondition.Logic.OR)

// Column Extensions (Leaf builders)
infix fun <T> Column<T>.eq(value: T): Condition = SimpleCondition(this, Op.Eq, value)
infix fun <T> Column<T>.neq(value: T): Condition = SimpleCondition(this, Op.Neq, value)
infix fun <T> Column<T>.gt(value: T): Condition = SimpleCondition(this, Op.Gt, value)
infix fun <T> Column<T>.lt(value: T): Condition = SimpleCondition(this, Op.Lt, value)
infix fun <T> Column<T>.gte(value: T): Condition = SimpleCondition(this, Op.Gte, value)
infix fun <T> Column<T>.lte(value: T): Condition = SimpleCondition(this, Op.Lte, value)
infix fun Column<String>.like(value: String): Condition = SimpleCondition(this, Op.Like, value)
