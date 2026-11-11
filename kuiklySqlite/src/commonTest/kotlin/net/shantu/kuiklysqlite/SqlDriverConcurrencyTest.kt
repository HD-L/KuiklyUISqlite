package net.shantu.kuiklysqlite

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 连接锁、嵌套事务与异步 API 的回归测试。
 * 只用公共 API：「另一个线程」就是 SqlWorker 的后台线程，因此同一份测试可在 iOS 模拟器与 Android 设备上运行。
 */
class SqlDriverConcurrencyTest {

    private lateinit var driver: SqlDriver

    @BeforeTest
    fun setUp() {
        driver = DriverFactory.createDriver(":memory:")
        driver.execute("CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)")
    }

    @AfterTest
    fun tearDown() {
        // 等后台线程上残留的任务结束再关闭
        val drained = Box(false)
        SqlWorker.post { drained.value = true }
        awaitUntil { drained.value }
        driver.close()
    }

    @Test
    fun failedTransactionRollsBackAndReleasesLock() {
        assertFailsWith<SqlTransactionException> {
            driver.transaction {
                insert("a")
                error("boom")
            }
        }
        assertEquals(0, count())

        // 锁已释放：后台线程能立即写入
        val done = Box(false)
        driver.async({ insert("b") }) { done.value = true }
        awaitUntil { done.value }
        assertEquals(1, count())
    }

    @Test
    fun nestedTransactionRollsBackOnlyInnerLevel() {
        driver.transaction {
            insert("outer")
            try {
                driver.transaction {
                    insert("inner")
                    error("inner failed")
                }
            } catch (expected: SqlTransactionException) {
                // 内层失败不影响外层
            }
        }
        assertEquals(listOf("outer"), names())
    }

    @Test
    fun otherThreadWritesWaitForTransactionInsteadOfJoiningIt() {
        val workerWrote = Box(false)
        assertFailsWith<SqlTransactionException> {
            driver.transaction {
                insert("in-transaction")
                // 事务进行中，另一个线程尝试写入：修复前它会被卷进本事务，随后一起被回滚
                driver.async({ insert("from-worker") }) { workerWrote.value = true }
                spin(200.milliseconds)
                assertEquals(false, workerWrote.value, "后台写入应等待事务结束")
                error("rollback")
            }
        }
        awaitUntil { workerWrote.value }
        assertEquals(listOf("from-worker"), names())
    }

    @Test
    fun asyncErrorIsDeliveredToOnError() {
        val error = Box<Throwable?>(null)
        val succeeded = Box(false)
        driver.async(
            block = { driver.execute("INSERT INTO missing_table VALUES (1)") },
            onError = { error.value = it },
        ) { succeeded.value = true }

        awaitUntil { error.value != null }
        assertIs<SqlExecutionException>(error.value)
        assertEquals(false, succeeded.value)
    }

    @Test
    fun asyncTasksRunInOrderAndCallbacksGoThroughDispatcher() {
        // 回调先排队，由测试线程手动执行：模拟「投递回 Kuikly 线程」
        val queue = Box(listOf<() -> Unit>())
        val dispatcher = SqlCallbackDispatcher { task -> queue.value = queue.value + task }
        val seen = Box(-1L)

        driver.async({ insert("x") }, dispatcher = dispatcher) { }
        driver.async({ count() }, dispatcher = dispatcher) { seen.value = it }

        awaitUntil { queue.value.size == 2 }
        assertEquals(-1L, seen.value, "回调必须经过 dispatcher，而不是在后台线程直接执行")
        queue.value.forEach { it() }
        assertEquals(1L, seen.value, "后提交的查询应看到先提交的插入")
    }

    @Test
    fun transactionAsyncRollsBackOnFailure() {
        val error = Box<Throwable?>(null)
        driver.transactionAsync({
            insert("a")
            insert("b")
            error("fail after two inserts")
        }, onError = { error.value = it }) { fail("should not succeed") }

        awaitUntil { error.value != null }
        assertEquals(0, count())
    }

    @Test
    fun lastInsertIdStaysWithItsInsertUnderConcurrency() {
        val ids = Box(listOf<Long>())
        repeat(50) { i ->
            driver.async({ insertReturningId("w$i") }) { id -> ids.value = ids.value + id }
        }
        val mainIds = (0 until 50).map { insertReturningId("m$it") }
        awaitUntil { ids.value.size == 50 }

        val all = mainIds + ids.value
        assertEquals(100, all.toSet().size, "每次插入都应拿到自己唯一的 rowid")
        assertEquals(100, count().toInt())
    }

    // ---------- helpers ----------

    private fun insert(name: String) = driver.withConnection {
        val stmt = driver.prepare("INSERT INTO t (name) VALUES (?)")
        try {
            stmt.bindString(1, name)
            stmt.step()
        } finally {
            stmt.close()
        }
    }

    private fun insertReturningId(name: String): Long = driver.withConnection {
        insert(name)
        driver.getLastInsertId()
    }

    private fun count(): Long = driver.withConnection {
        val stmt = driver.prepare("SELECT COUNT(*) FROM t")
        try {
            stmt.step()
            stmt.getColumnLong(0)
        } finally {
            stmt.close()
        }
    }

    private fun names(): List<String> = driver.withConnection {
        val stmt = driver.prepare("SELECT name FROM t ORDER BY id")
        try {
            buildList { while (stmt.step()) add(stmt.getColumnString(0)) }
        } finally {
            stmt.close()
        }
    }
}

/** 跨线程可见的值容器（用互斥锁保证内存可见性） */
internal class Box<T>(initial: T) {
    private val lock = SqlReentrantLock()
    private var current = initial
    var value: T
        get() = lock.withLock { current }
        set(v) = lock.withLock { current = v }
}

internal fun spin(duration: Duration) {
    val mark = TimeSource.Monotonic.markNow()
    while (mark.elapsedNow() < duration) {
        // busy wait：公共代码里没有 sleep
    }
}

internal fun awaitUntil(timeout: Duration = 5.seconds, condition: () -> Boolean) {
    val mark = TimeSource.Monotonic.markNow()
    while (!condition()) {
        assertTrue(mark.elapsedNow() < timeout, "等待超时")
        spin(5.milliseconds)
    }
}
