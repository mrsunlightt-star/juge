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
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.borderBlue
import com.juge.app.ui.theme.cardBg
import com.juge.app.ui.theme.selectBlue
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textWhite
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
                    value = name,
                    onValueChange = onNameChange,
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
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Color(0xFF94A3B8))
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
                        Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
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
                    value = content,
                    onValueChange = onContentChange,
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
                            .clickable { onRequestNewCategory() }
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
                                .clickable { onSelectCategory(cat) }
                                .background(if (selectedCategory?.id == cat.id) selectBlue.copy(alpha = 0.25f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory?.id == cat.id,
                                onClick = { onSelectCategory(cat) },
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
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = textGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                                    if (content.isNotBlank() && selectedCategory != null) {
                                        onConfirm()
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
                    value = content,
                    onValueChange = onContentChange,
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
                            .clickable { onRequestNewCategory() }
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
                                .clickable { onSelectCategory(cat) }
                                .background(if (selectedCategory?.id == cat.id) Color(0xFFCBFBEF) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCategory?.id == cat.id,
                                onClick = { onSelectCategory(cat) },
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
                    TextButton(onClick = onDismiss) {
                         Text("取消", color = Color(0xFF6B7280))
                     }
                     Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                                    if (content.isNotBlank() && selectedCategory != null) {
                                        onSave()
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
