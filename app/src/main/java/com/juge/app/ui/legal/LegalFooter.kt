package com.juge.app.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juge.app.data.LegalDocs
import com.juge.app.ui.theme.accentLightBlue

/**
 * 「跃然纸上」「个性定制」两页底部共用的协议、许可与备案页脚。
 * 备案号需在 App 内可见，点击跳转工信部备案查询系统。
 */
@Composable
fun LegalFooter(onOpenDoc: (String) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "《用户协议》",
                fontSize = 13.sp,
                color = accentLightBlue,
                modifier = Modifier.clickable { onOpenDoc("user") }
            )
            Text(
                text = "《隐私政策》",
                fontSize = 13.sp,
                color = accentLightBlue,
                modifier = Modifier.clickable { onOpenDoc("privacy") }
            )
            Text(
                text = "《开源许可》",
                fontSize = 13.sp,
                color = accentLightBlue,
                modifier = Modifier.clickable { onOpenDoc("license") }
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = LegalDocs.ICP_LICENSE,
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocs.ICP_QUERY_URL))
                    )
                }
            }
        )
    }
}
