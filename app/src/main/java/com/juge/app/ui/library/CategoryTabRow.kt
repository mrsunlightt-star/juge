package com.juge.app.ui.library

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juge.app.data.Category
import com.juge.app.ui.theme.selectBlue

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
                        onDeleteRequest(cat)
                    }
                )
            }
        )
    }
    item {
        IconButton(
        onClick = onOpenManager,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Manage Categories", tint = Color(0xFF64748B))
        }
    }
}
}
