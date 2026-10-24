package com.juge.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppSectionLabel
import com.juge.app.ui.AppFormGroup
import com.juge.app.ui.AppFormRow
import com.juge.app.ui.AppText
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.appTextFieldColors
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textTertiary
import com.juge.app.ui.theme.textWhite
import com.juge.app.ui.theme.rememberAppHaptics

/** 新建分类目录弹窗。分类名由调用方持有，避免弹窗自己再持有一份状态。 */
@Composable
internal fun AddCategoryDialog(
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
            AppDialogTitle("新建分类目录")
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = {
                    AppText(
                        text = "输入分类名称，如“电影”",
                        role = AppTextRole.bodyTertiary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
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
                    text = "确认",
                    onClick = { if (name.isNotBlank()) onConfirm() },
                    enabled = name.isNotBlank()
                )
            }
        }
    }
}

/** 新增金句弹窗：输入内容 + 单选归类，「+ 新建目录」把用户引到新建分类弹窗。 */
@Composable
internal fun AddReminderDialog(
    content: String,
    onContentChange: (String) -> Unit,
    categories: List<Category>,
    selectedCategory: Category?,
    onSelectCategory: (Category) -> Unit,
    onRequestNewCategory: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface(scrollable = true) {
            val haptics = rememberAppHaptics()
            AppDialogTitle("新增记录到句阁")

            // 放大文本输入框，设定固定高度以显示更多内容
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                placeholder = {
                    Text(
                        text = "输入金句或提醒文字",
                        style = AppType.body,
                        color = textTertiary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                maxLines = 6,
                shape = AppShape.button,
                textStyle = AppType.body,
                colors = appTextFieldColors()
            )

            // 分类标题行并排集成“+ 新建目录”按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppSectionLabel("归类目录")
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                        ) {
                            haptics.click()
                            onRequestNewCategory()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppText(text = "+ 新建目录", role = AppTextRole.link)
                }
            }

            // iOS 分组列表：标签左、选中行右侧打勾，整行可点
            AppFormGroup {
                categories.forEachIndexed { index, cat ->
                    AppFormRow(
                        label = cat.name,
                        selected = selectedCategory?.id == cat.id,
                        showDivider = index < categories.lastIndex,
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppPrimaryButton(
                    text = "确认",
                    onClick = {
                        if (content.isNotBlank() && selectedCategory != null) onConfirm()
                    },
                    enabled = content.isNotBlank() && selectedCategory != null
                )
            }
        }
    }
}

/** 编辑金句弹窗：改内容 + 改归类，保存后由页面决定是否关闭。 */
@Composable
internal fun EditReminderDialog(
    content: String,
    onContentChange: (String) -> Unit,
    categories: List<Category>,
    selectedCategory: Category?,
    onSelectCategory: (Category) -> Unit,
    onRequestNewCategory: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface(scrollable = true) {
            val haptics = rememberAppHaptics()
            AppDialogTitle("编辑金句内容")

            // 放大文本输入框
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                label = { Text("金句内容", style = AppType.body) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                maxLines = 6,
                shape = AppShape.button,
                textStyle = AppType.body,
                colors = appTextFieldColors()
            )

            // 归类并排集成“+ 新建目录”按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppSectionLabel("归类目录")
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(
                            interactionSource = MutableInteractionSource(),
                            indication = null,
                        ) {
                            haptics.click()
                            onRequestNewCategory()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+ 新建目录", style = AppType.link, color = accentBlue)
                }
            }

            // iOS 分组列表：标签左、选中行右侧打勾，整行可点
            AppFormGroup {
                categories.forEachIndexed { index, cat ->
                    AppFormRow(
                        label = cat.name,
                        selected = selectedCategory?.id == cat.id,
                        showDivider = index < categories.lastIndex,
                        onClick = { onSelectCategory(cat) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppPrimaryButton(
                    text = "保存并更新",
                    onClick = {
                        if (content.isNotBlank() && selectedCategory != null) onSave()
                    },
                    enabled = content.isNotBlank() && selectedCategory != null
                )
            }
        }
    }
}

/** 二次确认删除单条金句：删除不可恢复，且卡片上还有编辑按钮，必须防误触。 */
@Composable
internal fun DeleteReminderDialog(
    reminder: Reminder,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AppDialogSurface {
            AppDialogTitle("删除这条金句？")
            AppText(
                text = "「${reminder.content}」删除后无法恢复。",
                role = AppTextRole.bodySecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                AppDangerButton(text = "删除", onClick = onConfirm)
            }
        }
    }
}
