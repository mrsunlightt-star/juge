package com.juge.app.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juge.app.R
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.AppSectionCard
import com.juge.app.ui.AppText
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppMotion
import com.juge.app.ui.theme.AppShape
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.surfaceSubtle
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
    val haptics = rememberAppHaptics()
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
                    // 金句卡片与定制页区块卡片共用同一套卡面法律（AppSectionCard：
                    // 16dp 圆角 + 1dp 描边 + 弥散软阴影）。差异只有三点：
                    // 左右 16 / 上下 12 的瘦身内边距、关闭尺寸动画、整卡可点（选中金句）。
                    // 内层间距由 AppSectionCard 的 spacedBy(12dp) 提供，与旧版 Spacer(itemGap) 等宽。
                    AppSectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.tap()
                                onReminderClick(reminder)
                            },
                        contentPadding = PaddingValues(
                            horizontal = AppSpacing.cardPadding,
                            vertical = 12.dp
                        ),
                        animateSize = false,
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
                                AppText(
                                    text = catName,
                                    role = AppTextRole.badgeOnFill,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 34dp 圆形浅底把图标"托"起来：18dp 裸图标在白卡上又小又薄。
                                // 热区 40dp（卡片高度收 10% 的一环），略低于 48dp 指南但仍是舒适触点。
                                CardActionButton(
                                    iconRes = R.drawable.ic_symbol_edit_square,
                                    contentDescription = "编辑金句",
                                    tint = textSecondary,
                                    background = surfaceSubtle,
                                    onClick = { onEditRequest(reminder) }
                                )
                                CardActionButton(
                                    iconRes = R.drawable.ic_symbol_delete,
                                    contentDescription = "删除金句",
                                    tint = dangerRed,
                                    background = dangerRed.copy(alpha = 0.08f),
                                    onClick = { onDeleteRequest(reminder) }
                                )
                            }
                        }
                        AppText(
                            text = reminder.content,
                            role = AppTextRole.bodyLarge
                        )
                    }
                }
            }
        }
        item {
            // 页脚与最后一张卡片之间留出空间：首屏不露出页脚，滚到底时再完整出现
            Spacer(modifier = Modifier.height(80.dp))
        }
        item {
            // 页脚放在按钮上方：滚到底时《字体许可》不会被浮动按钮遮住
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
        AppText(
            text = if (hasCategories) "这里还没有金句" else "还没有分类目录",
            role = AppTextRole.bodyStrong
        )
        Spacer(modifier = Modifier.height(AppSpacing.tight))
        AppText(
            text = if (hasCategories) {
                "点击右下角「录入新句」，把喜欢的句子收进来。"
            } else {
                "先到「分类管理」建一个目录，再录入第一句。"
            },
            role = AppTextRole.bodySecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/**
 * 金句卡片右上角的操作按钮：34dp 圆形浅底 + 20dp 圆角图标。
 *
 * 直接用 18dp 裸图标时，在白色卡片上又小又薄；加一层同色系浅底后图标有了"实体"，
 * 视觉尺寸和可点按的暗示都够用。外圈 40dp 自绘热区（卡片瘦身的一环）——不用
 * IconButton，是因为 M3 会把热区强制抬回 48dp 最小值，卡片高度就收不下来了；
 * 无波纹的灰底高亮与全 app 的按压反馈口径一致。
 */
@Composable
private fun CardActionButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    tint: Color,
    background: Color,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                role = Role.Button,
            ) {
                haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
