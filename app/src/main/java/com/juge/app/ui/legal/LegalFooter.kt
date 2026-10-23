package com.juge.app.ui.legal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.juge.app.data.LegalDocs
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentLightBlue
import com.juge.app.ui.theme.textSecondary

/**
 * 「跃然纸上」「个性定制」两页底部共用的协议、许可与备案页脚。
 * 备案号需在 App 内可见，点击跳转工信部备案查询系统。
 * 链接文字纵向补 15dp 内边距，把 18sp 的行高撑到 48dp 触控热区。
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
                style = AppType.link,
                color = accentLightBlue,
                modifier = Modifier
                    .clickable { onOpenDoc("user") }
                    .padding(vertical = 15.dp)
            )
            Text(
                text = "《隐私政策》",
                style = AppType.link,
                color = accentLightBlue,
                modifier = Modifier
                    .clickable { onOpenDoc("privacy") }
                    .padding(vertical = 15.dp)
            )
            Text(
                text = "《开源许可》",
                style = AppType.link,
                color = accentLightBlue,
                modifier = Modifier
                    .clickable { onOpenDoc("license") }
                    .padding(vertical = 15.dp)
            )
        }
        Text(
            text = LegalDocs.ICP_LICENSE,
            style = AppType.caption,
            color = textSecondary,
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocs.ICP_QUERY_URL))
                        )
                    }
                }
                .padding(vertical = 15.dp)
        )
    }
}
