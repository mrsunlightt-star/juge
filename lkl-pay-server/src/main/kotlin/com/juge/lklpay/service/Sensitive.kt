package com.juge.lklpay.service

/**
 * 个人信息的入库脱敏。
 *
 * 背景：`PayOrder.buyerLogonId` 的实体注释一直写着「脱敏后的买家账号」，
 * 但代码路径里从来没有脱敏过——支付宝返回什么就原样落库什么。
 * 于是一条「注释说已脱敏」的字段，实际存的是完整手机号/邮箱，
 * 数据库被拖走后这批买家的登录名就是明文。
 *
 * 这里在最靠近入库的一层（[PayOrderService.markPaid]）统一脱敏，
 * 而不是在 Controller 或实体 setter 里做，因为写入面有多处（异步通知、主动查单），
 * 只要有一处漏掉就等于没做。
 *
 * 脱敏规则：
 *   - 手机号 138****0000：保留前 3 后 4
 *   - 邮箱   abc***@example.com：保留本地部分前 3 位，域名完整保留
 *   - 其它  过长截断到 [MAX_LEN]
 * 原始值**不入库、不写日志**：人工核对靠的是「前 3 后 4 能否对上」，
 * 对不上就让用户自己报完整账号，由客服核对支付宝后台。
 */
internal object Sensitive {

    private const val MAX_LEN = 128
    private const val MAX_LOG_CHARS = 128

    /**
     * 日志参数净化：外部可控值写进日志前统一过一遍。
     *
     * `/notify` 的验签失败分支必须在验签**之前**记 `out_trade_no`，否则运维
     * 完全不知道是哪笔通知被拒；但那个值是攻击者随手可填的，直接拼进日志
     * 可以用 `\r\n` 伪造出整条假日志行（日志注入 / 绕过告警规则）。
     * 限流桶的 key 里带 XFF、金额校验日志里带 `total_amount`，同样受外部控制，
     * 所以净化放在这里共用，而不是只在 Controller 里做一遍。
     */
    fun safeLog(value: Any?): String {
        if (value == null) return "null"
        val text = value.toString()
        val trimmed = if (text.length > MAX_LOG_CHARS) text.take(MAX_LOG_CHARS) + "…" else text
        return buildString(trimmed.length) {
            for (ch in trimmed) {
                append(if (ch == '\n' || ch == '\r' || ch.code < 0x20 || ch.code == 0x7F) '?' else ch)
            }
        }
    }

    fun maskLogonId(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val truncated = if (value.length > MAX_LEN) value.take(MAX_LEN) else value

        val at = truncated.indexOf('@')
        if (at > 0) {
            val local = truncated.substring(0, at)
            val domain = truncated.substring(at)
            val kept = if (local.length <= 2) "*".repeat(local.length) else local.take(3) + "***"
            return kept + domain
        }

        return when {
            // 纯数字且够长 → 按手机号处理
            truncated.length >= 7 && truncated.all { it.isDigit() } ->
                truncated.take(3) + "****" + truncated.takeLast(4)
            truncated.length <= 4 -> "*".repeat(truncated.length)
            else -> truncated.take(3) + "***" + truncated.takeLast(2)
        }
    }

    /**
     * 通知留档里的买家账号同样要脱敏。
     *
     * `notifyRaw` 是整段表单参数拼串，`buyer_logon_id=13800001234` 会原样躺在里面。
     * 只脱敏 `buyer_logon_id` 这一个字段，其余原样保留——留档的价值在于事后对账，
     * 不能被过度破坏。用正则按 `key=value&` 的边界匹配，避免误伤值里恰好出现的子串。
     */
    private val LOGON_ID_IN_RAW = Regex("(buyer_logon_id=)([^&]*)")

    fun maskNotifyRaw(raw: String): String =
        LOGON_ID_IN_RAW.replace(raw) { m -> m.groupValues[1] + maskLogonId(m.groupValues[2]) }
}
