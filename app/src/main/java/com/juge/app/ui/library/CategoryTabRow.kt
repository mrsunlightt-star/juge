package com.juge.app.ui.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.juge.app.data.Category
import com.juge.app.ui.AppFilterChip
import com.juge.app.ui.theme.AppSpacing
import com.juge.app.ui.theme.textGray

/**
 * 文库顶部分类筛选条：全部 / 各分类 / 分类管理入口。
 * 长按分类只上报「请求删除」，二次确认弹窗由页面持有。
 */
@Composable
internal fun CategoryTabRow(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelect: (Category?) -> Unit,
    onDeleteRequest: (Category) -> Unit,
    onOpenManager: () -> Unit
) {
LazyRow(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AppSpacing.screen),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    item {
        AppFilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelect(null) },
            label = "全部",
        )
    }
    items(categories) { cat ->
        AppFilterChip(
            selected = selectedCategory?.id == cat.id,
            onClick = { onCategorySelect(cat) },
            label = cat.name,
            modifier = Modifier
                .semantics {
                    // 长按删除是隐藏手势，至少让 TalkBack 能通过“长按操作”触发
                    onLongClick(label = "删除分类") {
                        onDeleteRequest(cat)
                        true
                    }
                }
                .pointerInput(cat) {
                    detectTapGestures(
                        onLongPress = {
                            onDeleteRequest(cat)
                        }
                    )
                }
        )
    }
    item {
        // 不覆写尺寸：IconButton 自带 48dp 触控热区
        IconButton(onClick = onOpenManager) {
            Icon(Icons.Default.Settings, contentDescription = "管理分类", tint = textGray)
        }
    }
}
}
