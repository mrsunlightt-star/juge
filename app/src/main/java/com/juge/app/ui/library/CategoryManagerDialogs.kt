package com.juge.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite
/**
 * 分类管理中心：排序、重命名、删除、inline 新建。
 * 重命名/删除只上报请求，二次确认弹窗由页面持有。
 */
@Composable
internal fun CategoryManagerDialog(
    categories: List<Category>,
    onSwapOrder: (Long, Int, Long, Int) -> Unit,
    onRenameRequest: (Category) -> Unit,
    onDeleteRequest: (Category) -> Unit,
    onAddCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                        modifier = Modifier.clickable { onDismiss() }
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
                                            onSwapOrder(cat.id, cat.sortOrder, prevCat.id, prevCat.sortOrder)
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
                                            onSwapOrder(cat.id, cat.sortOrder, nextCat.id, nextCat.sortOrder)
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
                                        onRenameRequest(cat)
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
                                            onDeleteRequest(cat)
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

/** 重命名分类弹窗。 */
@Composable
internal fun RenameCategoryDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    value = name,
                    onValueChange = onNameChange,
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
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color(0xFF6B7280))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                                    if (name.isNotBlank()) {
                                        onConfirm()
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

/** 二次确认删除分类目录（会连目录下的金句一起清空，故单独一个弹窗）。 */
@Composable
internal fun DeleteCategoryDialog(
    category: Category,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                    text = "确定要删除“${category.name}”吗？这将同时清空该目录下的所有诗词金句，此操作不可恢复。",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color(0xFF6B7280))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) // 危险操作使用亮红色按钮
                    ) {
                        Text("删除并清空", color = Color.White)
                    }
                }
            }
        }
    }
}
