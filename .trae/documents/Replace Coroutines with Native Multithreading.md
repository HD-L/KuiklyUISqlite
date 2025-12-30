I will replace the `kotlinx-coroutines-core` dependency with a native multithreading implementation using `Worker` (Native) and `ExecutorService` (Android/JVM), and update the KSP compiler to remove coroutine-based code generation.

### 1. Build Configuration
*   **Remove Dependency**: In `kuiklySqlite/build.gradle.kts`, remove `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")`.

### 2. Remove Existing Dispatchers
*   Delete `SqlDispatchers.kt` and its platform implementations:
    *   `kuiklySqlite/src/commonMain/kotlin/net/shantu/kuiklysqlite/SqlDispatchers.kt`
    *   `kuiklySqlite/src/androidMain/kotlin/net/shantu/kuiklysqlite/SqlDispatchers.android.kt`
    *   `kuiklySqlite/src/nativeMain/kotlin/net/shantu/kuiklysqlite/SqlDispatchers.native.kt`

### 3. Implement New Threading Mechanism (`DbExecutor`)
*   **Common Main**: Create `net/shantu/kuiklysqlite/DbExecutor.kt` with `expect object DbExecutor`.
*   **Android Main**: Create `net/shantu/kuiklysqlite/DbExecutor.android.kt` using `java.util.concurrent.Executors`.
*   **Native Main**: Create `net/shantu/kuiklysqlite/DbExecutor.native.kt` using `kotlin.native.concurrent.Worker` and `TransferMode` as requested.

### 4. Update KSP Compiler (`DaoGenerator.kt`)
*   **Modify `DaoGenerator.kt`**:
    *   Remove generation of `suspend` functions (`insertSuspend`, `updateSuspend`, etc.).
    *   Remove generation of `Flow` functions (`selectAllFlow`, etc.).
    *   Remove `kotlinx.coroutines` imports from the generated files.
    *   This ensures the generated code no longer relies on the removed library.
