package com.juge.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.Category
import com.juge.app.data.Reminder
import com.juge.app.ui.legal.LegalFooter
import com.juge.app.ui.theme.selectBlue

/** 金句列表：逐条卡片（进场动画、分类角标、编辑/删除）+ 页脚。 */
@Composable
internal fun ReminderList(
    reminders: List<Reminder>,
    categories: List<Category>,
    onReminderClick: (Reminder) -> Unit,
    onEditRequest: (Reminder) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onOpenDoc: (String) -> Unit
) {
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
) {
    items(reminders, key = { it.id }) { reminder ->
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
                                        onEditRequest(reminder)
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
