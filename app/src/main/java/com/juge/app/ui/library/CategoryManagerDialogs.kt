package com.juge.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.R
import com.juge.app.data.Category
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppDivider
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.appTextFieldColors
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.disabledGray
import com.juge.app.ui.theme.surfaceSubtle
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textPrimary
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
        AppDialogSurface(contentPadding = 20.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppDialogTitle("文库分类管理中心")
                AppTextButton(text = "< 关闭", onClick = onDismiss, color = accentBlue)
            }

            AppDivider()

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
                            .background(surfaceSubtle, AppShape.chip)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = cat.name,
                            style = AppType.body,
                            color = textPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
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
                                enabled = index > 0
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "上移分类",
                                    tint = if (index > 0) textPrimary else disabledGray,
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
                                enabled = index < categories.size - 1
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "下移分类",
                                    tint = if (index < categories.size - 1) textPrimary else disabledGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 重命名/删除收进溢出菜单：4 颗 28dp 按钮并排既挤又容易误触
                            Box {
                                var menuExpanded by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "更多分类操作",
                                        tint = textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("重命名", style = AppType.body) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_symbol_edit_square),
                                                contentDescription = null,
                                                tint = accentBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onRenameRequest(cat)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除", style = AppType.body, color = dangerRed) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_symbol_delete),
                                                contentDescription = null,
                                                tint = dangerRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDeleteRequest(cat)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AppDivider()

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
                    placeholder = {
                        Text(
                            text = "新建分类名称",
                            style = AppType.body,
                            color = textGray
                        )
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    shape = AppShape.button,
                    textStyle = AppType.body,
                    colors = appTextFieldColors()
                )
                AppPrimaryButton(
                    text = "添加",
                    onClick = {
                        if (inlineNewCatName.isNotBlank()) {
                            onAddCategory(inlineNewCatName)
                            inlineNewCatName = ""
                        }
                    },
                    enabled = inlineNewCatName.isNotBlank()
                )
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
        AppDialogSurface {
            AppDialogTitle("修改分类目录名称")
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = {
                    Text(
                        text = "输入新分类名称",
                        style = AppType.body,
                        color = textGray
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                shape = AppShape.button,
                textStyle = AppType.body,
                colors = appTextFieldColors()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppPrimaryButton(
                    text = "保存",
                    onClick = { if (name.isNotBlank()) onConfirm() },
                    enabled = name.isNotBlank()
                )
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
        AppDialogSurface {
            AppDialogTitle("确定要删除分类目录吗？")
            Text(
                text = "确定要删除“${category.name}”吗？这将同时清空该目录下的所有诗词金句，此操作不可恢复。",
                style = AppType.body,
                color = textGray
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppDangerButton(text = "删除并清空", onClick = onConfirm)
            }
        }
    }
}
