package com.juge.app.ui.library

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite

// --- 子Tab：我的文库内容 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabContent(
    categories: List<Category>,
    reminders: List<Reminder>,
    selectedCategory: Category?,
    editingReminder: Reminder?,
    onEditReminderRequest: (Reminder?) -> Unit,
    onCategorySelect: (Category?) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onRenameCategory: (Long, String) -> Unit,
    onSwapCategoryOrder: (Long, Int, Long, Int) -> Unit,
    onAddReminder: (String, Long) -> Unit,
    onEditReminderSave: (Long, String, Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onReminderClick: (Reminder) -> Unit,
    onOpenDoc: (String) -> Unit
) {
    val context = LocalContext.current
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var newReminderContent by remember { mutableStateOf("") }
    var selectedCatForReminder by remember { mutableStateOf<Category?>(categories.firstOrNull()) }

    // 纯文本及分类修改对话框的控制状态
    var editContentField by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var editCategorySelection by remember { mutableStateOf<Category?>(null) }

    // 分类管理中心对话框控制状态
    var showCategoryManagerDialog by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<Category?>(null) }
    var renameCategoryNewName by remember { mutableStateOf("") }

    LaunchedEffect(editingReminder) {
        if (editingReminder != null) {
            editContentField = editingReminder.content
            editCategorySelection = categories.find { it.id == editingReminder.categoryId }
        }
    }

    val filteredReminders = if (selectedCategory == null) {
        reminders
    } else {
        reminders.filter { it.categoryId == selectedCategory.id }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text("全部", fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color(0xFF64748B),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            selectedContainerColor = selectBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == null,
                            borderColor = Color(0xFFE2E8F0),
                            selectedBorderColor = selectBlue
                        )
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory?.id == cat.id,
                        onClick = { onCategorySelect(cat) },
                        label = { Text(cat.name, fontWeight = if (selectedCategory?.id == cat.id) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = Color(0xFF64748B),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            selectedContainerColor = selectBlue
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory?.id == cat.id,
                            borderColor = Color(0xFFE2E8F0),
                            selectedBorderColor = selectBlue
                        ),
                        modifier = Modifier.pointerInput(cat) {
                            detectTapGestures(
                                onLongPress = {
                                    deletingCategory = cat
                                }
                            )
                        }
                    )
                }
                item {
                    IconButton(
                        onClick = {
                            showCategoryManagerDialog = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Categories", tint = Color(0xFF64748B))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredReminders, key = { it.id }) { reminder ->
                    val catName = categories.find { it.id == reminder.categoryId }?.name ?: "普通"

                    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = slideInVertically(
                            animationSpec = tween(durationMillis = 400)
                        ) { it / 2 } + fadeIn(
                            animationSpec = tween(durationMillis = 400)
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReminderClick(reminder)
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = selectBlue
                                    ) {
                                        Text(
                                            text = catName,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Reminder",
                                            tint = Color(0xFF8A9A91),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    onEditReminderRequest(reminder)
                                                }
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clickable { onDeleteReminder(reminder.id) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = reminder.content,
                                    fontSize = 15.sp,
                                    color = Color(0xFF19241E),
                                    lineHeight = 23.sp
                                )
                            }
                        }
                    }
                }
                item {
                    LegalFooter(onOpenDoc)
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .height(50.dp)
                .shadow(6.dp, RoundedCornerShape(25.dp))
                .clip(RoundedCornerShape(25.dp))
                .background(selectBlue)
                .clickable {
                    if (categories.isEmpty()) {
                        Toast.makeText(context, "请先创建一个分类目录", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCatForReminder = categories.first()
                        showAddReminderDialog = true
                    }
                }
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("录入新句", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }

    // --- 全局统一：高级暗黑系 Compose Dialog 自定义弹窗 ---

    // 1. 新建分类弹窗
    if (showAddCategoryDialog) {
        Dialog(
            onDismissRequest = { showAddCategoryDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, borderBlue)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("新建分类目录", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite)
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        placeholder = { Text("输入分类名称，如“电影”", color = Color(0xFF94A3B8)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite,
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = borderBlue,
                            cursorColor = accentBlue
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddCategoryDialog = false }) {
                            Text("取消", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    onAddCategory(newCategoryName)
                                    newCategoryName = ""
                                    showAddCategoryDialog = false
                                }
                            },
                            modifier = Modifier
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .background(selectBlue, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 2. 新增句阁记录弹窗
    if (showAddReminderDialog) {
        Dialog(
            onDismissRequest = { showAddReminderDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                border = BorderStroke(1.dp, borderBlue)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     Text("新增记录到句阁", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textWhite)
                    
                    // 放大文本输入框，设定固定高度以显示更多内容
                    OutlinedTextField(
                        value = newReminderContent,
                        onValueChange = { newReminderContent = it },
                        placeholder = { Text("输入金句或提醒文字", color = textGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textWhite,
                            unfocusedTextColor = textWhite,
                            focusedBorderColor = accentBlue,
                            unfocusedBorderColor = borderBlue,
                            cursorColor = accentBlue
                        )
                    )
                    
                    // 分类标题行并排集成“+ 新建目录”按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("归类目录:", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ 新建目录",
                            fontSize = 12.sp,
                            color = Color(0xFF0F766E),
                            modifier = Modifier
                                .clickable {
                                    showAddReminderDialog = false
                                    showAddCategoryDialog = true
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    // 压缩单选行高的归类列表
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedCatForReminder = cat }
                                    .background(if (selectedCatForReminder?.id == cat.id) selectBlue.copy(alpha = 0.25f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedCatForReminder?.id == cat.id,
                                    onClick = { selectedCatForReminder = cat },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = selectBlue,
                                        unselectedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = cat.name, color = textWhite, fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddReminderDialog = false }) {
                            Text("取消", color = textGray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newReminderContent.isNotBlank() && selectedCatForReminder != null) {
                                    onAddReminder(newReminderContent, selectedCatForReminder!!.id)
                                    newReminderContent = ""
                                    showAddReminderDialog = false
                                }
                            },
                            modifier = Modifier
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .background(selectBlue, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 3. 点击金句卡片后：弹出的纯粹文字与分类编辑弹窗
    if (editingReminder != null) {
        Dialog(
            onDismissRequest = { onEditReminderRequest(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     Text("编辑金句内容", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                    
                    // 放大文本输入框
                    OutlinedTextField(
                        value = editContentField,
                        onValueChange = { editContentField = it },
                        label = { Text("金句内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                             focusedTextColor = Color(0xFF1F2937),
                             unfocusedTextColor = Color(0xFF1F2937),
                             focusedBorderColor = Color(0xFF0F766E),
                             unfocusedBorderColor = Color(0xFFE5E7EB),
                             focusedLabelColor = Color(0xFF0F766E),
                             unfocusedLabelColor = Color(0xFF6B7280),
                             cursorColor = Color(0xFF0F766E)
                         )
                    )
                    
                    // 归类并排集成“+ 新建目录”按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("归类目录:", color = Color(0xFF1F2937), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "+ 新建目录",
                            fontSize = 12.sp,
                            color = Color(0xFF0F766E),
                            modifier = Modifier
                                .clickable {
                                    onEditReminderRequest(null) // 先关掉编辑框
                                    showAddCategoryDialog = true // 开启新建分类
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    // 压缩间距的分类列表
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { editCategorySelection = cat }
                                    .background(if (editCategorySelection?.id == cat.id) Color(0xFFCBFBEF) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editCategorySelection?.id == cat.id,
                                    onClick = { editCategorySelection = cat },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = selectBlue,
                                        unselectedColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = cat.name, color = Color(0xFF1F2937), fontSize = 13.sp)
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onEditReminderRequest(null) }) {
                             Text("取消", color = Color(0xFF6B7280))
                         }
                         Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editContentField.isNotBlank() && editCategorySelection != null) {
                                    onEditReminderSave(editingReminder.id, editContentField, editCategorySelection!!.id)
                                }
                            },
                            modifier = Modifier
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .background(selectBlue, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存并更新", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 5. 分类管理中心弹窗
    if (showCategoryManagerDialog) {
        Dialog(
            onDismissRequest = { showCategoryManagerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("文库分类管理中心", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                        Text(
                            text = "< 关闭",
                            fontSize = 13.sp,
                            color = Color(0xFF0F766E),
                            modifier = Modifier.clickable { showCategoryManagerDialog = false }
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    // 分类项列表 (支持滑动，高度自适应)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories.size) { index ->
                            val cat = categories[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cat.name,
                                    color = Color(0xFF1F2937),
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 排序：上移按钮
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val prevCat = categories[index - 1]
                                                onSwapCategoryOrder(cat.id, cat.sortOrder, prevCat.id, prevCat.sortOrder)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Up",
                                            tint = if (index > 0) Color(0xFF1F2937) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 排序：下移按钮
                                    IconButton(
                                        onClick = {
                                            if (index < categories.size - 1) {
                                                val nextCat = categories[index + 1]
                                                onSwapCategoryOrder(cat.id, cat.sortOrder, nextCat.id, nextCat.sortOrder)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp),
                                        enabled = index < categories.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Down",
                                            tint = if (index < categories.size - 1) Color(0xFF1F2937) else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // 重命名按钮
                                    IconButton(
                                        onClick = {
                                            renamingCategory = cat
                                            renameCategoryNewName = cat.name
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Rename",
                                            tint = Color(0xFF0F766E),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // 删除按钮 (移入设置中心)
                                    IconButton(
                                        onClick = {
                                            deletingCategory = cat
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE5E7EB))

                    // 底部 inline 新建分类输入区
                    var inlineNewCatName by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inlineNewCatName,
                            onValueChange = { inlineNewCatName = it },
                            placeholder = { Text("新建分类名称", color = Color(0xFF6B7280)) },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1F2937),
                                unfocusedTextColor = Color(0xFF1F2937),
                                focusedBorderColor = Color(0xFF0F766E),
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedLabelColor = Color(0xFF0F766E),
                                unfocusedLabelColor = Color(0xFF6B7280),
                                cursorColor = Color(0xFF0F766E)
                            )
                        )
                        Button(
                            onClick = {
                                if (inlineNewCatName.isNotBlank()) {
                                    onAddCategory(inlineNewCatName)
                                    inlineNewCatName = ""
                                }
                            },
                            modifier = Modifier
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .background(selectBlue, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("添加", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 6. 重命名分类子 Dialog
    if (renamingCategory != null) {
        Dialog(
            onDismissRequest = { renamingCategory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("修改分类目录名称", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                    OutlinedTextField(
                        value = renameCategoryNewName,
                        onValueChange = { renameCategoryNewName = it },
                        placeholder = { Text("输入新分类名称", color = Color(0xFF6B7280)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1F2937),
                            unfocusedTextColor = Color(0xFF1F2937),
                            focusedBorderColor = Color(0xFF0F766E),
                            unfocusedBorderColor = Color(0xFFE5E7EB),
                            focusedLabelColor = Color(0xFF0F766E),
                            unfocusedLabelColor = Color(0xFF6B7280),
                            cursorColor = Color(0xFF0F766E)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { renamingCategory = null }) {
                            Text("取消", color = Color(0xFF6B7280))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (renameCategoryNewName.isNotBlank()) {
                                    onRenameCategory(renamingCategory!!.id, renameCategoryNewName)
                                    renamingCategory = null
                                }
                            },
                            modifier = Modifier
                                .shadow(2.dp, RoundedCornerShape(8.dp))
                                .background(selectBlue, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // 4. 二次确认删除分类目录弹窗 (防误触高级 Dialog)
    if (deletingCategory != null) {
        Dialog(
            onDismissRequest = { deletingCategory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("确定要删除分类目录吗？", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                    Text(
                        text = "确定要删除“${deletingCategory!!.name}”吗？这将同时清空该目录下的所有诗词金句，此操作不可恢复。",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { deletingCategory = null }) {
                            Text("取消", color = Color(0xFF6B7280))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onDeleteCategory(deletingCategory!!.id)
                                deletingCategory = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) // 危险操作使用亮红色按钮
                        ) {
                            Text("删除并清空", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
