package com.juge.lklpay.config

import com.juge.lklpay.service.AlipayKeyLoader
import com.juge.lklpay.service.EntitlementSigner
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 支付宝配置自检结果（启动时算一次，之后只读）。
 *
 * 存在的理由：`alipay.pid` 为空、私钥路径写错这类问题原先都不会在启动时暴露——
 * `AlipayPayService` / `AlipayIdentityService` 的密钥都是 `by lazy`，第一次下单或
 * 第一次走「找回」时才加载。故障被推迟到用户已经站在收银台前的那一刻：
 *
 *   - `pid` 为空 → `/auth-info` 恒 503，换机找回 100% 不可用，但**下单查单一切正常**，
 *     所以线上跑几个月都不会有人发现（本次审查就是这么发现的）
 *   - 私钥路径错 → 第一笔真实订单 500，日志里只有一句「支付宝下单异常」
 *
 * 现在改成启动时把能查的都查掉，并把结论暴露成 [keyReady]，由 Controller 在入口挡下：
 * 缺密钥时返回明确的 503 CONFIG_UNAVAILABLE，而不是每笔订单都炸一次 500。
 */
@Component
class AlipayConfigStatus(
    private val props: AlipayProperties,
    private val entitlementSigner: EntitlementSigner,
) {

    private val log = LoggerFactory.getLogger(AlipayConfigStatus::class.java)

    /**
     * 两把密钥是否都读得出来。false 时所有需要加签/验签的接口都应拒绝服务。
     *
     * 用「私有可变字段 + 只读属性」而不是 `var ... private set`：
     * Spring 的 all-open 插件把 @Component 类变成 open，Kotlin 不允许 open 属性有私有 setter。
     */
    @Volatile
    private var keysLoaded: Boolean = false

    val keyReady: Boolean get() = keysLoaded

    /** 找回功能是否可用：需要 pid + 密钥就绪 */
    val recoveryReady: Boolean get() = keyReady && props.pid.isNotBlank()

    /**
     * 权益凭证签发是否可用。
     *
     * 与 [keyReady] 独立：凭证密钥换掉不影响下单，支付宝密钥坏掉也不该让
     * 已经买过的用户连「找回」都做不了——所以 `/recover` 的结论不受这一位影响，
     * 它只决定响应里带不带 `credential`（与 App 侧 `optString("credential")` 对应），
     * 以及客户端能否把权益落到新设备上。
     */
    val entitlementReady: Boolean get() = entitlementSigner.ready

    @PostConstruct
    fun validate() {
        val keyErrors = mutableListOf<String>()
        runCatching { AlipayKeyLoader.load(props.privateKeyPath) }
            .onFailure { keyErrors += "应用私钥读取失败（${props.privateKeyPath}）：${it.message}" }
        runCatching { AlipayKeyLoader.load(props.alipayPublicKeyPath) }
            .onFailure { keyErrors += "支付宝公钥读取失败（${props.alipayPublicKeyPath}）：${it.message}" }

        keysLoaded = keyErrors.isEmpty()
        keyErrors.forEach { log.error("启动自检失败：{}", it) }
        if (!keyReady) {
            log.error(
                """
                ┌─────────────────────────────────────────────────────────────────────
                │ 支付宝密钥不可用：下单/查单/验签全部会失败
                │   相关接口将返回 503 CONFIG_UNAVAILABLE（而不是每笔订单 500）
                │ 修法：确认 certs/alipay_app_private_key.txt 与 certs/alipay_public_key.txt
                │       存在，或用启动参数指向正确路径：
                │       --alipay.private-key-path=/etc/juge/alipay_app_private_key.txt
                │       --alipay.alipay-public-key-path=/etc/juge/alipay_public_key.txt
                │ 详见 README「部署安全基线」第 3 条
                └─────────────────────────────────────────────────────────────────────
                """.trimIndent()
            )
        }

        if (props.appId.isBlank()) {
            log.error("启动自检失败：alipay.app-id 为空，无法下单")
        }
        if (props.notifyUrl.isBlank()) {
            log.error("启动自检失败：alipay.notify-url 为空，支付宝收不到异步通知")
        } else if (!props.notifyUrl.startsWith("https://")) {
            log.warn("启动自检提醒：alipay.notify-url 不是 https：{}", props.notifyUrl)
        }

        // 非致命：pid 只影响「找回已购」，不影响下单/查单
        if (props.pid.isBlank()) {
            log.warn(
                """
                ┌─────────────────────────────────────────────────────────────────────
                │ 启动自检：alipay.pid 未配置 —— 「换机找回 PRO」将 100% 不可用
                │   /api/alipay/auth-info 会恒返回 503 RECOVERY_UNAVAILABLE
                │   下单与查单不受影响，所以这个缺口不会自己暴露出来
                │ 修法：把签约支付宝账号的 PID（2088 开头，开放平台 → 账号中心可见）
                │       填进 application.yml 的 alipay.pid，或启动参数
                │       --alipay.pid=2088xxxxxxxxxxxx
                │ 若本期不开放找回功能，可忽略本条 WARN，但 App 内的找回入口应同步隐藏
                └─────────────────────────────────────────────────────────────────────
                """.trimIndent()
            )
        }

        // 凭证密钥不可用只影响「新设备能不能拿到权益」，不该拦住进程启动，
        // 但必须有一条 ERROR 让人看见，否则又是一次「跑几个月没人发现」。
        if (!entitlementReady) {
            log.error(
                """
                ┌─────────────────────────────────────────────────────────────────────
                │ 权益凭证私钥不可用：/recover 仍会回答「找到/没找到」，但不带 credential
                │   后果：用户换了手机能查到「你买过」，却无法把 PRO 落到新设备上
                │ 修法：确认 certs/entitlement_private_key.pem 存在，或用启动参数
                │       --entitlement.private-key-path=/etc/juge/entitlement_private_key.pem
                └─────────────────────────────────────────────────────────────────────
                """.trimIndent(),
            )
        }

        log.info(
            "支付宝配置自检完成 appId={} pid={} keyReady={} recoveryReady={} entitlementReady={} "
                + "entitlementKeyFp={} notifyUrl={} expireMinutes={}",
            props.appId,
            if (props.pid.isBlank()) "<未配置>" else props.pid.take(4) + "****",
            keyReady,
            recoveryReady,
            entitlementReady,
            if (entitlementReady) entitlementSigner.publicKeyFingerprint() else "<不可用>",
            props.notifyUrl,
            props.orderExpireMinutes,
        )
    }
}
