package com.juge.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textPrimary
import com.juge.app.ui.theme.textSecondary
import com.juge.app.ui.theme.textTertiary

/** 金句列表：逐条卡片（进场动画、分类角标、编辑/删除）+ 空态 + 页脚。 */
@Composable
internal fun ReminderList(
    reminders: List<Reminder>,
    categories: List<Category>,
    isLoaded: Boolean,
    onReminderClick: (Reminder) -> Unit,
    onEditRequest: (Reminder) -> Unit,
    onDeleteRequest: (Reminder) -> Unit,
    onOpenDoc: (String) -> Unit
) {
    // 只给初次出现的前几条或后来新增到顶部的条目播放一次进场动画；普通滚动不反复动画。
    val animatedReminderIds = remember { mutableSetOf<Long>() }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.itemGap)
    ) {
        if (isLoaded && reminders.isEmpty()) {
            item(key = "empty_state") {
                EmptyReminderState(hasCategories = categories.isNotEmpty())
            }
        } else {
            itemsIndexed(reminders, key = { _, reminder -> reminder.id }) { index, reminder ->
                val catName = categories.find { it.id == reminder.categoryId }?.name ?: "普通"

                val shouldAnimate = index < 4 && reminder.id !in animatedReminderIds
                if (shouldAnimate) SideEffect { animatedReminderIds.add(reminder.id) }
                val visibleState = remember(reminder.id) {
                    MutableTransitionState(initialState = !shouldAnimate)
                }.apply { targetState = true }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = slideInVertically(
                        animationSpec = AppMotion.entrance()
                    ) { it / 2 } + fadeIn(
                        animationSpec = AppMotion.entrance()
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReminderClick(reminder)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, borderBlue),
                        shape = AppShape.card,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpacing.cardPadding)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = AppShape.badge,
                                    color = selectBlue
                                ) {
                                    Text(
                                        text = catName,
                                        style = AppType.badge,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 图标视觉仍是 18dp，IconButton 自带 48dp 触控热区
                                    IconButton(onClick = { onEditRequest(reminder) }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "编辑金句",
                                            tint = textTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { onDeleteRequest(reminder) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除金句",
                                            tint = dangerRed.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(AppSpacing.itemGap))
                            Text(
                                text = reminder.content,
                                style = AppType.bodyLarge,
                                color = textPrimary
                            )
                        }
                    }
                }
            }
        }
        item {
            // 页脚与最后一张卡片之间留出空间：首屏不露出页脚，滚到底时再完整出现
            Spacer(modifier = Modifier.height(80.dp))
        }
        item {
            // 页脚放在按钮上方：滚到底时《开源许可》不会被浮动按钮遮住
            LegalFooter(onOpenDoc)
        }
        item {
            // 底部留白让页脚滚到「录入新句」按钮上方
            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

/** 空文库：给下一步动作，而不是留一片空白。 */
@Composable
private fun EmptyReminderState(hasCategories: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = textTertiary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(AppSpacing.itemGap))
        Text(
            text = if (hasCategories) "这里还没有金句" else "还没有分类目录",
            style = AppType.bodyStrong,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(AppSpacing.tight))
        Text(
            text = if (hasCategories) {
                "点击右下角「录入新句」，把喜欢的句子收进来。"
            } else {
                "先到「分类管理」建一个目录，再录入第一句。"
            },
            style = AppType.body,
            color = textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
