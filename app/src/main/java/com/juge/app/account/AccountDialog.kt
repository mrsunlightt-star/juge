package com.juge.app.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val ACCOUNT_GRADIENT = listOf(Color(0xFF059669), Color(0xFF0284C7))

/**
 * 账号弹窗：登录 / 注册 / 已登录三种形态。
 *
 * 刻意做成「可选登录」——弹窗右下角永远有「暂不登录」，
 * 不登录也能完整使用 App，登录的唯一收益是换机后能找回已购的 PRO。
 *
 * @param onProConfirmed 服务端确认该账号已购 PRO 时回调，由调用方执行本地激活
 */
@Composable
fun AccountDialog(
    onProConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(AccountStore.snapshot(appContext)) }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 注销是破坏性操作，需二次确认，确认后才允许点最终按钮
    var confirmDelete by remember { mutableStateOf(false) }

    val submit: () -> Unit = {
        if (!busy) {
            message = ""
            busy = true
            scope.launch {
                val result = if (isRegister) {
                    AccountApi.register(username.trim(), password, nickname.trim().ifBlank { null })
                } else {
                    AccountApi.login(username.trim(), password)
                }
                result.fold(
                    onSuccess = { session ->
                        AccountStore.saveSession(
                            context = appContext,
                            token = session.token,
                            username = session.account.username,
                            nickname = session.account.nickname,
                            pro = session.account.pro,
                        )
                        snapshot = AccountStore.snapshot(appContext)
                        busy = false
                        if (session.account.pro) onProConfirmed()
                    },
                    onFailure = { e ->
                        busy = false
                        message = e.message ?: "操作失败，请稍后重试"
                    },
                )
            }
        }
    }

    val logout: () -> Unit = {
        if (!busy) {
            val token = snapshot?.token
            busy = true
            message = ""
            scope.launch {
                if (token != null) AccountApi.logout(token)
                AccountStore.clear(appContext)
                snapshot = null
                busy = false
            }
        }
    }

    val deleteAccount: () -> Unit = {
        if (!busy) {
            val token = snapshot?.token
            if (token == null) {
                AccountStore.clear(appContext)
                snapshot = null
            } else {
                busy = true
                message = ""
                scope.launch {
                    AccountApi.deleteAccount(token).fold(
                        onSuccess = {
                            AccountStore.clear(appContext)
                            snapshot = null
                            confirmDelete = false
                            busy = false
                        },
                        onFailure = { e ->
                            busy = false
                            message = e.message ?: "注销失败，请稍后重试"
                        },
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = snapshot

                Text("账号", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))

                if (current == null) {
                    Text(
                        "登录后，已购的 PRO 会跟着账号走 —— 换手机、重装应用都能找回。",
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563),
                    )
                    Divider()
                    Row(horizontalArrangement = Arrangement.Center) {
                        TextButton(
                            onClick = { if (!busy) { isRegister = false; message = "" } },
                            enabled = !busy,
                        ) {
                            Text(
                                "登录",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRegister) Color(0xFF9CA3AF) else Color(0xFF0F766E),
                            )
                        }
                        TextButton(
                            onClick = { if (!busy) { isRegister = true; message = "" } },
                            enabled = !busy,
                        ) {
                            Text(
                                "注册",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRegister) Color(0xFF0F766E) else Color(0xFF9CA3AF),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.take(32) },
                        label = { Text("用户名") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.take(64) },
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isRegister) {
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(32) },
                            label = { Text("昵称（选填）") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Text(
                        if (isRegister) {
                            "用户名 3~32 位字母、数字或下划线；密码至少 6 位。密码无法自助找回，请牢记。"
                        } else {
                            "忘记密码可联系我们，凭支付宝交易记录人工找回。"
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF),
                    )
                    if (message.isNotEmpty()) {
                        Text(message, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }

                    Button(
                        onClick = submit,
                        enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(ACCOUNT_GRADIENT), RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isRegister) "注册中…" else "登录中…",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        } else {
                            Text(
                                if (isRegister) "注册并登录" else "登录",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text("暂不登录", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                } else {
                    Text(
                        "👤 ${current.displayName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F766E),
                    )
                    Text("@${current.username}", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    Text(
                        if (current.pro) {
                            "✨ 服务端已记录你的 PRO，换手机登录本账号即可恢复"
                        } else {
                            "尚未开通 PRO。开通后换手机登录本账号即可恢复"
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563),
                    )
                    Divider()
                    Text("退出登录不会收回已解锁的风格。", fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    if (message.isNotEmpty()) {
                        Text(message, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }

                    Button(
                        onClick = logout,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .shadow(2.dp, RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(ACCOUNT_GRADIENT), RoundedCornerShape(12.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            if (busy && !confirmDelete) "处理中…" else "退出登录",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }

                    if (!confirmDelete) {
                        TextButton(onClick = { if (!busy) { confirmDelete = true; message = "" } }, enabled = !busy) {
                            Text("注销账号", color = Color(0xFFDC2626), fontSize = 12.sp)
                        }
                    } else {
                        Divider()
                        Text(
                            "注销后将永久删除服务端账号与订单绑定信息，且无法恢复、无法再通过本账号找回 PRO。本机已解锁的风格不受影响。",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { if (!busy) confirmDelete = false }, enabled = !busy) {
                                Text("取消", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                            }
                            TextButton(onClick = deleteAccount, enabled = !busy) {
                                Text(
                                    if (busy) "注销中…" else "确认注销",
                                    color = Color(0xFFDC2626),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text("关闭", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** 与激活弹窗一致的浅分隔线，避免用默认 Divider 的深色 */
@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))
}