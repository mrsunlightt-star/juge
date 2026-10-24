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
import com.juge.app.ui.AppText
import com.juge.app.ui.theme.AppTextRole
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.rememberAppHaptics
import com.juge.app.ui.theme.textSecondary

/**
 * 「跃然纸上」「个性定制」两页底部共用的协议、许可与备案页脚。
 * 备案号需在 App 内可见，点击跳转工信部备案查询系统。
 * 链接文字纵向补 15dp 内边距，把 18sp 的行高撑到 48dp 触控热区。
 */
@Composable
fun LegalFooter(onOpenDoc: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppText(
                text = "《用户协议》",
                role = AppTextRole.link,
                modifier = Modifier
                    .clickable {
                        haptics.click()
                        onOpenDoc("user")
                    }
                    .padding(vertical = 16.dp)
            )
            AppText(
                text = "《隐私政策》",
                role = AppTextRole.link,
                modifier = Modifier
                    .clickable {
                        haptics.click()
                        onOpenDoc("privacy")
                    }
                    .padding(vertical = 16.dp)
            )
            AppText(
                text = "《字体许可》",
                role = AppTextRole.link,
                modifier = Modifier
                    .clickable {
                        haptics.click()
                        onOpenDoc("license")
                    }
                    .padding(vertical = 16.dp)
            )
        }
        AppText(
            text = LegalDocs.ICP_LICENSE,
            role = AppTextRole.captionSecondary,
            modifier = Modifier
                .clickable {
                    haptics.click()
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(LegalDocs.ICP_QUERY_URL))
                        )
                    }
                }
                .padding(vertical = 16.dp)
        )
    }
}
