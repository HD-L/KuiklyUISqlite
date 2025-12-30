package net.shantu.kuiklysqlite.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.layout.size
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.foundation.lazy.LazyColumn
import com.tencent.kuikly.compose.foundation.lazy.items
import com.tencent.kuikly.compose.foundation.text.KeyboardOptions
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Checkbox
import com.tencent.kuikly.compose.material3.Divider
import com.tencent.kuikly.compose.material3.MaterialTheme
import com.tencent.kuikly.compose.material3.TextField
import com.tencent.kuikly.compose.ui.text.input.KeyboardType
import com.tencent.kuikly.compose.ui.window.Dialog
import com.tencent.kuikly.compose.ui.window.DialogProperties
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.module.Module

import net.shantu.kuiklysqlite.ConsoleSqlLogger
import net.shantu.kuiklysqlite.DatabaseManager
import net.shantu.kuiklysqlite.dsl.like
import net.shantu.kuiklysqlite.example.dao.UserDao
import net.shantu.kuiklysqlite.example.dao.UserTable

@Page("router", supportInLocal = true)
internal class ComposeRoutePager : ComposeContainer() {
    override fun createExternalModules(): Map<String, Module> {
        return mapOf(
            SandboxPathModule.MODULE_NAME to SandboxPathModule(),
        )
    }

    override fun created() {
        super.created()
        // 初始化数据
        val dbPath = this.acquireModule<SandboxPathModule>(SandboxPathModule.MODULE_NAME)
            .getDatabasesDirectoryPath() + "/app.db"
        KLog.e("shantu", "dbPath:$dbPath")
        // 2. 创建管理器
        val dbManager = DatabaseManager(
            path = dbPath, schema = AppSchema, logger = ConsoleSqlLogger()
        )
        // 3. 获取 Driver (会自动触发 create/migrate)
        val driver = dbManager.driver
        val userDao = UserDao(driver)
        setContent {
            UserManagementScreen(userDao)
        }
    }
}

@Composable
fun UserManagementScreen(userDao: UserDao) {
    var users by remember { mutableStateOf(userDao.selectAll()) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var userToUpdate by remember { mutableStateOf<User?>(null) }

    // Search State
    var searchQuery by remember { mutableStateOf("") }

    // Count State
    var totalCount by remember { mutableStateOf(0L) }
    var filteredCount by remember { mutableStateOf(0L) }

    fun refresh() {
        // Update counts
        totalCount = userDao.count()

        if (searchQuery.isEmpty()) {
            users = userDao.selectAll()
            filteredCount = totalCount
        } else {
            // Use DSL for count and query
            val query = userDao.select().where(UserTable.name like "%$searchQuery%")

            filteredCount = query.count()
            users = query.find()
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        refresh()
    }

    // Auto-refresh when search query changes
    LaunchedEffect(searchQuery) {
        refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 50.dp)) {

        // Title Bar with Counts
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "用户管理",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "总数: $totalCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "当前展示: $filteredCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (filteredCount < totalCount) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
        }

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("搜索用户 (Name)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("输入关键词...") })
            if (searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { searchQuery = "" }) {
                    Text("清除")
                }
            }
        }

        // User List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(users) { user ->
                UserRow(
                    user = user, isSelected = selectedIds.contains(user.id), onToggleSelection = {
                        if (selectedIds.contains(user.id)) {
                            selectedIds.remove(user.id)
                        } else {
                            selectedIds.add(user.id)
                        }
                    })
                Divider()
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { showAddDialog = true }) {
                Text("添加")
            }
            Button(onClick = {
                selectedIds.forEach { id ->
                    val user = users.find { it.id == id }
                    if (user != null) {
                        userDao.delete(user)
                    }
                }
                selectedIds.clear()
                refresh()
            }) {
                Text("删除")
            }
            Button(onClick = {
                if (selectedIds.size == 1) {
                    userToUpdate = users.find { it.id == selectedIds.first() }
                    if (userToUpdate != null) {
                        showUpdateDialog = true
                    }
                } else {
                    // TODO: Show toast or indicator that only one item can be updated at a time
                }
            }) {
                Text("更新")
            }
        }
    }

    if (showAddDialog) {
        UserDialog(title = "添加用户", onConfirm = { name, age, phone, email ->
            userDao.insert(User(name = name, age = age, phone = phone, email = email))
            refresh()
            showAddDialog = false
        }, onDismiss = { showAddDialog = false })
    }

    if (showUpdateDialog && userToUpdate != null) {
        UserDialog(
            title = "更新用户",
            initialName = userToUpdate!!.name,
            initialAge = userToUpdate!!.age,
            initialEmail = if (userToUpdate!!.email == null) "" else userToUpdate!!.email!!,
            onConfirm = { name, age, phone, email ->
                userDao.update(
                    userToUpdate!!.copy(
                        name = name, age = age, phone = phone, email = email
                    )
                )
                refresh()
                showUpdateDialog = false
                userToUpdate = null
                selectedIds.clear()
            },
            onDismiss = {
                showUpdateDialog = false
                userToUpdate = null
            })
    }
}

@Composable
fun UserRow(user: User, isSelected: Boolean, onToggleSelection: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggleSelection() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected, onCheckedChange = { onToggleSelection() })
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "ID: ${user.id}, Name: ${user.name}, Age: ${user.age}, Email: ${user.email}, Phone: ${user.phone}")
    }
}

/**
 * 自定义用户信息弹窗（重构版，贴合基础Dialog写法风格）
 * @param title 弹窗标题
 * @param initialName 姓名初始值
 * @param initialAge 年龄初始值
 * @param onConfirm 确认回调（返回姓名、年龄）
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun UserDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialEmail: String = "",
    initialAge: Int = 0,
    onConfirm: (String, Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    // 状态管理：姓名（字符串）、年龄（字符串，方便输入校验）
    var name by remember { mutableStateOf(initialName) }
    var phoneStr by remember { mutableStateOf(initialPhone) }
    var emailStr by remember { mutableStateOf(initialEmail) }
    var ageStr by remember { mutableStateOf(if (initialAge > 0) initialAge.toString() else "") }

    // 基础Dialog容器（替代AlertDialog，完全自定义样式）
    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            // 可选：配置弹窗特性（如是否可点击外部关闭、是否全屏等）
            dismissOnBackPress = true, dismissOnClickOutside = true
        )
    ) {
        // 弹窗主体容器（固定宽度、圆角、白色背景、内边距）
        Box(
            modifier = Modifier.width(300.dp) // 固定宽度，和示例一致
                .background(Color.White, MaterialTheme.shapes.large) // 圆角（16dp）+ 白色背景
                .padding(20.dp), // 内边距
            contentAlignment = Alignment.Center
        ) {
            // 弹窗内容列布局
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp), // 元素间距16dp
                modifier = Modifier.fillMaxWidth()
            ) {
                // 弹窗标题
                Text(
                    text = title,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )

                // 姓名输入框
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, // 单行输入
                    placeholder = { Text("请输入姓名") })
                // 手机号输入框（仅允许数字输入）
                TextField(
                    value = phoneStr,
                    onValueChange = { input ->
                        // 仅允许输入数字，过滤非数字字符
                        if (input.all { it.isDigit() }) {
                            phoneStr = input
                        }
                    },
                    label = { Text("phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // 数字键盘
                    placeholder = { Text("请输入Phone") })
                // 年龄输入框（仅允许数字输入）
                TextField(
                    value = ageStr,
                    onValueChange = { input ->
                        // 仅允许输入数字，过滤非数字字符
                        if (input.all { it.isDigit() }) {
                            ageStr = input
                        }
                    },
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // 数字键盘
                    placeholder = { Text("请输入年龄") })
                // email输入框
                TextField(
                    value = emailStr,
                    onValueChange = { input ->
                        emailStr = input
                    },
                    label = { Text("email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("请输入Email") })

                // 按钮行（取消 + 确认）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp), // 按钮间距
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 取消按钮
                    Button(
                        onClick = onDismiss, modifier = Modifier.weight(1f) // 平分宽度
                    ) {
                        Text("取消")
                    }

                    // 确认按钮（带输入校验）
                    Button(
                        onClick = {
                            val age = ageStr.toIntOrNull() ?: 0
                            // 姓名非空才触发回调
                            if (name.isNotBlank()) {
                                onConfirm(name, age, phoneStr, emailStr)
                                onDismiss() // 确认后自动关闭弹窗
                            }
                        }, modifier = Modifier.weight(1f),
                        // 姓名为空时禁用按钮（优化体验）
                        enabled = name.isNotBlank()
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}


