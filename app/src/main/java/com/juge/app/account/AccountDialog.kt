package com.juge.app.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.juge.app.ui.AppDangerButton
import com.juge.app.ui.AppDialog
import com.juge.app.ui.AppDialogSurface
import com.juge.app.ui.AppDialogTitle
import com.juge.app.ui.AppTextField
import com.juge.app.ui.AppPrimaryButton
import com.juge.app.ui.AppTextButton
import com.juge.app.ui.theme.AppType
import com.juge.app.ui.theme.accentBlue
import com.juge.app.ui.theme.dangerRed
import com.juge.app.ui.theme.textGray
import com.juge.app.ui.theme.textTertiary
import kotlinx.coroutines.launch

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
    show: Boolean,
    onProConfirmed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf(AccountStore.snapshot(appContext)) }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
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

    // 退出登录/注销账号与「我的」子页面共用 [AccountActions]：两处对「要不要动本地 PRO、
    // 服务端失败时本地会话留不留」的规则必须只有一份实现
    val logout: () -> Unit = {
        if (!busy) {
            busy = true
            message = ""
            scope.launch {
                AccountActions.logout(appContext)
                snapshot = null
                busy = false
            }
        }
    }

    val deleteAccount: () -> Unit = {
        if (!busy) {
            busy = true
            message = ""
            scope.launch {
                AccountActions.deleteAccount(appContext).fold(
                    onSuccess = {
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

    AppDialog(
        visible = show,
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppDialogSurface(scrollable = true) {
            val current = snapshot

            // 标题与其它子页面统一为 AppDialogTitle（18sp），不再单独用 20sp hero
            AppDialogTitle("账号")

            if (current == null) {
                Text(
                    text = "登录后，已购的 PRO 会跟着账号走 —— 换手机、重装应用都能找回。",
                    style = AppType.body,
                    color = textGray
                )
                // 原 1dp 分割线删除：上下相邻元素靠 AppDialogSurface 的 spacedBy
                // (controlGap) 间距分组，层次只用明度色阶
                Row(horizontalArrangement = Arrangement.Center) {
                    AppTextButton(
                        text = "登录",
                        onClick = { if (!busy) { isRegister = false; message = "" } },
                        enabled = !busy,
                        color = if (isRegister) textTertiary else accentBlue
                    )
                    AppTextButton(
                        text = "注册",
                        onClick = { if (!busy) { isRegister = true; message = "" } },
                        enabled = !busy,
                        color = if (isRegister) accentBlue else textTertiary
                    )
                }

                // 全 App 统一输入框：无 1dp 描边，靠明度台阶区分
                AppTextField(
                    value = username,
                    onValueChange = { username = it.take(32) },
                    label = "用户名",
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = AppType.body,
                )
                AppTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = "密码",
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isRegister) ImeAction.Next else ImeAction.Done,
                    ),
                    // 密码仍走 PasswordVisualTransformation：绘制层拿到的就是圆点串，
                    // 不能泄漏明文
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = AppType.body,
                )
                if (isRegister) {
                    AppTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(32) },
                        label = "昵称（选填）",
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = AppType.body,
                    )
                }

                Text(
                    text = if (isRegister) {
                        "用户名 3~32 位字母、数字或下划线；密码至少 6 位。密码无法自助找回，请牢记。"
                    } else {
                        "忘记密码可联系我们，凭支付宝交易记录人工找回。"
                    },
                    style = AppType.micro,
                    color = textTertiary,
                )
                if (message.isNotEmpty()) {
                    Text(message, style = AppType.caption, color = dangerRed)
                }

                AppPrimaryButton(
                    text = when {
                        busy && isRegister -> "注册中…"
                        busy -> "登录中…"
                        isRegister -> "注册并登录"
                        else -> "登录"
                    },
                    onClick = submit,
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    loading = busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppTextButton(
                    text = "暂不登录",
                    onClick = onDismiss,
                    enabled = !busy,
                    color = textTertiary
                )
            } else {
                Text(
                    text = "👤 ${current.displayName}",
                    style = AppType.bodyLarge,
                    color = accentBlue,
                )
                Text(
                    text = "@${current.username}",
                    style = AppType.caption,
                    color = textTertiary
                )
                Text(
                    text = if (current.pro) {
                        "✨ 服务端已记录你的 PRO，换手机登录本账号即可恢复"
                    } else {
                        "尚未开通 PRO。开通后换手机登录本账号即可恢复"
                    },
                    style = AppType.body,
                    color = textGray,
                )
                // 原 1dp 分割线删除：上下相邻元素靠 AppDialogSurface 的 spacedBy
                // (controlGap) 间距分组，层次只用明度色阶
                Text(
                    text = "退出登录不会收回已解锁的风格。",
                    style = AppType.micro,
                    color = textTertiary
                )
                if (message.isNotEmpty()) {
                    Text(message, style = AppType.caption, color = dangerRed)
                }

                AppPrimaryButton(
                    text = if (busy && !confirmDelete) "处理中…" else "退出登录",
                    onClick = logout,
                    enabled = !busy,
                    loading = busy && !confirmDelete,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!confirmDelete) {
                    AppTextButton(
                        text = "注销账号",
                        onClick = { if (!busy) { confirmDelete = true; message = "" } },
                        enabled = !busy,
                        color = dangerRed
                    )
                } else {
                    // 不再画 1dp 分割线：注销确认区靠 AppDialogSurface 的 spacedBy
                    // 间距 + dangerRed 文字自成一组
                    Text(
                        text = "注销后将永久删除服务端账号与订单绑定信息，且无法恢复、无法再通过本账号找回 PRO。本机已解锁的风格不受影响。",
                        style = AppType.caption,
                        color = dangerRed,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppTextButton(
                            text = "取消",
                            onClick = { if (!busy) confirmDelete = false },
                            enabled = !busy,
                            color = textTertiary
                        )
                        // 危险操作用实心 AppDangerButton（设计规范 §6.1.3），
                        // 与「我的」页的注销账号确认同一形态
                        AppDangerButton(
                            text = if (busy) "注销中…" else "确认注销",
                            onClick = deleteAccount,
                            enabled = !busy,
                        )
                    }
                }

                AppTextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    enabled = !busy,
                    color = textTertiary
                )
            }
        }
    }
}
