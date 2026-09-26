package com.juge.lklpay.service

import com.alipay.api.AlipayClient
import com.alipay.api.DefaultAlipayClient
import com.alipay.api.domain.AlipayTradeAppPayModel
import com.alipay.api.domain.AlipayTradeQueryModel
import com.alipay.api.internal.util.AlipaySignature
import com.alipay.api.request.AlipayTradeAppPayRequest
import com.alipay.api.request.AlipayTradeQueryRequest
import com.juge.lklpay.config.AlipayProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 支付宝 App 支付服务。
 *
 * 接口清单：
 *   下单  alipay.trade.app.pay   —— 本地加签生成 orderStr，不产生网络请求
 *   查询  alipay.trade.query     —— 主动查单，用于「通知为主、查询为辅」
 *   验签  异步通知                —— 用支付宝公钥验证 notify 参数
 *
 * 关键约定（官方文档明确要求）：
 *   - 客户端返回 9000 仅代表「请求已发出」，**必须以异步通知或主动查询为最终结论**；
 *     8000（正在处理中）、6004（处理结果未知）必须主动查单。
 *   - 异步通知可能重复投递，业务侧必须幂等。
 */
@Service
class AlipayPayService(private val props: AlipayProperties) {

    private val log = LoggerFactory.getLogger(AlipayPayService::class.java)

    private val appPrivateKey: String by lazy { loadKey(props.privateKeyPath) }
    private val alipayPublicKey: String by lazy { loadKey(props.alipayPublicKeyPath) }

    private val client: AlipayClient by lazy {
        DefaultAlipayClient(
            props.gatewayUrl,
            props.appId,
            appPrivateKey,
            props.format,
            props.charset,
            alipayPublicKey,
            props.signType,
        )
    }

    /**
     * 下单：生成可信签名字符串 orderStr，由 App 端交给支付宝 SDK 的 PayTask.payV2() 拉起收银台。
     *
     * @param amountFen 金额，单位「分」。取自服务端商品目录，不接受客户端传入
     */
    fun createAppOrder(amountFen: Long, subject: String): Map<String, Any?> {
        require(amountFen in 1..MAX_AMOUNT_FEN) {
            "订单金额必须在 0.01 ~ 100000000.00 元之间（当前 ${amountFen} 分）"
        }
        // 订单号一律由服务端生成：若接受客户端传入，攻击者可复用已有订单号覆盖订单记录
        val orderNo = genOutTradeNo()

        val model = AlipayTradeAppPayModel().apply {
            this.outTradeNo = orderNo
            this.totalAmount = fenToYuan(amountFen)
            this.subject = sanitizeSubject(subject)
            // App 支付的固定销售产品码
            this.productCode = PRODUCT_CODE
            this.timeExpire = LocalDateTime.now().plusMinutes(props.orderExpireMinutes).format(DT)
        }
        val request = AlipayTradeAppPayRequest().apply {
            bizModel = model
            notifyUrl = props.notifyUrl
        }

        val response = client.sdkExecute(request)
        val orderStr = response.body
        if (orderStr.isNullOrBlank()) {
            log.error(
                "支付宝下单失败 code={} subCode={} msg={} subMsg={}",
                response.code, response.subCode, response.msg, response.subMsg,
            )
            throw IllegalStateException("支付宝下单失败：${response.subMsg ?: response.msg ?: "未返回 orderStr"}")
        }

        log.info("支付宝下单成功 outTradeNo={} amountFen={} subject={}", orderNo, amountFen, model.subject)
        return mapOf("out_trade_no" to orderNo, "order_str" to orderStr)
    }

    /**
     * 主动查单。交易不存在返回 code=40004 / sub_code=ACQ.TRADE_NOT_EXIST，属正常状态而非故障。
     */
    fun queryOrder(outTradeNo: String): Map<String, Any?> {
        val request = AlipayTradeQueryRequest().apply {
            bizModel = AlipayTradeQueryModel().apply { this.outTradeNo = outTradeNo }
        }
        val response = client.execute(request)
        log.info(
            "支付宝查单 outTradeNo={} code={} subCode={} tradeStatus={}",
            outTradeNo, response.code, response.subCode, response.tradeStatus,
        )
        return mapOf(
            "out_trade_no" to outTradeNo,
            "trade_no" to response.tradeNo,
            "trade_status" to response.tradeStatus,
            "total_amount" to response.totalAmount,
            "buyer_pay_amount" to response.buyerPayAmount,
            "receipt_amount" to response.receiptAmount,
            // 买家标识：查单路径同样带回，供订单落库与后续「找回激活」使用
            "buyer_user_id" to response.buyerUserId,
            "buyer_logon_id" to response.buyerLogonId,
            "code" to response.code,
            "sub_code" to response.subCode,
            "sub_msg" to response.subMsg,
        )
    }

    /**
     * 异步通知验签。
     *
     * 先验签，再信任参数——未验签的参数可被任意伪造，绝不能用于变更订单状态。
     * 验签通过后仍需校验 app_id 与订单金额，防止「签名有效但内容非本应用」的重放。
     *
     * @param rawParams 支付宝 POST 过来的原始表单参数（含 sign / sign_type）
     */
    fun verifyNotify(rawParams: Map<String, String>): Boolean {
        // SDK 的 getSignCheckContentV1 会 remove 掉 sign / sign_type，必须传入可变 Map
        val params = LinkedHashMap(rawParams)
        val verified = try {
            AlipaySignature.rsaCheckV1(params, alipayPublicKey, props.charset, props.signType)
        } catch (e: Exception) {
            log.error("支付宝通知验签异常", e)
            false
        }
        if (!verified) {
            return false
        }

        val notifyAppId = rawParams["app_id"]
        if (notifyAppId != props.appId) {
            log.warn("支付宝通知 app_id 不匹配：收到 {}，期望 {}", notifyAppId, props.appId)
            return false
        }
        return true
    }

    /** 应用私钥/支付宝公钥统一归一化为单行 Base64（剥离 PEM 头尾与换行） */
    private fun loadKey(path: String): String {
        val content = Files.readString(Path.of(path))
        val single = content
            .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
            .replace(Regex("-----END [A-Z ]+-----"), "")
            .filter { !it.isWhitespace() }
        check(single.isNotEmpty()) { "密钥文件为空：$path" }
        return single
    }

    private fun fenToYuan(amountFen: Long): String =
        BigDecimal(amountFen).movePointLeft(2).setScale(2).toPlainString()

    /** 官方约束：标题 ≤256 字符，且不可含 / = & 等特殊字符 */
    private fun sanitizeSubject(subject: String): String {
        val cleaned = subject.replace(Regex("[/=&]"), "-").trim().take(256)
        return cleaned.ifBlank { DEFAULT_SUBJECT }
    }

    private fun genOutTradeNo(): String {
        // 官方约束：≤64 位，仅字母、数字、下划线，且商户端唯一
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return (ts + UUID.randomUUID().toString().replace("-", "").take(12)).uppercase()
    }

    companion object {
        private const val PRODUCT_CODE = "QUICK_MSECURITY_PAY"
        private const val DEFAULT_SUBJECT = "句阁订单"

        /** 官方上限 100000000.00 元 */
        private const val MAX_AMOUNT_FEN = 10_000_000_000L

        private val DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}