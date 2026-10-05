package com.juge.lklpay.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 支付开放接口节流。
 *
 * 下单/查单是公网无鉴权入口：
 *  - 不限制下单：攻击者可每秒写大量订单行，刷爆数据库与日志
 *  - 不限制查单：游客订单凭订单号即可查，且每次查单都会转发调用支付宝网关，
 *    可被用来遍历订单号、放大消耗支付宝侧 QPS 配额
 *
 * 与 [LoginThrottle] 相互独立：登录节流按「失败次数」计，这里按「调用次数」计——
 * 下单/查单没有失败概念，每次调用都是一次真实资源消耗。
 *
 * 计数放内存：单实例部署下够用，重启清零可接受。
 */
@Component
class PayApiThrottle {

    private val log = LoggerFactory.getLogger(PayApiThrottle::class.java)

    private class Bucket {
        var windowStart: Long = 0
        var count: Int = 0
    }

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val lastSweep = AtomicLong(0)

    /**
     * 检查并消耗一次配额；超限返回 false，调用方应拒绝本次请求。
     *
     * @param bucketName 接口桶名（create / query），各桶独立计数
     * @param clientIp   来源 IP；为空时按全局桶计数（Nginx 未正确传递 XFF 的兜底）
     * @param limitPerMinute 每 IP 每分钟限额
     */
    fun tryConsume(bucketName: String, clientIp: String?, limitPerMinute: Int): Boolean {
        val now = System.currentTimeMillis()
        val key = "$bucketName:${clientIp?.takeIf { it.isNotBlank() } ?: "unknown"}"
        val bucket = buckets.compute(key) { _, existing ->
            val current = existing ?: Bucket().also { it.windowStart = now }
            if (now - current.windowStart > WINDOW_MS) {
                current.windowStart = now
                current.count = 0
            }
            current.count++
            current
        }
        sweepIfNeeded(now)
        val allowed = bucket != null && bucket.count <= limitPerMinute
        if (!allowed) {
            log.warn("支付接口触发限流 bucket={} key={} 本窗口调用次数={}", bucketName, key, bucket?.count)
        }
        return allowed
    }

    /** 定期清理过期桶，防止大量随机 IP 撑爆内存 */
    private fun sweepIfNeeded(now: Long) {
        val last = lastSweep.get()
        if (now - last < SWEEP_INTERVAL_MS || !lastSweep.compareAndSet(last, now)) return
        val expireBefore = now - WINDOW_MS
        buckets.entries.removeIf { it.value.windowStart < expireBefore }
    }

    companion object {
        /** 下单限额：正常用户一次购买产生 1-2 次下单，20 次/分钟已极宽裕 */
        const val CREATE_LIMIT_PER_MINUTE = 20

        /** 查单限额：支付后轮询间隔通常 2-3 秒、持续十几秒，60 次/分钟覆盖重试场景 */
        const val QUERY_LIMIT_PER_MINUTE = 60

        /**
         * 异步通知限额。
         *
         * notify 是无鉴权公网入口，每次都要跑一次 RSA 验签，是现成的 CPU 放大点。
         * 但它同时是**唯一**能把订单置为已支付的通路，绝不能因为限流丢单——
         * 所以这里只做防洪：限额取得极宽（支付宝正常投递远低于此），
         * 被拦下的请求回 "failure"，支付宝会按官方节奏重投（最长 25 小时），最坏只是延迟到账。
         */
        const val NOTIFY_LIMIT_PER_MINUTE = 300

        private const val WINDOW_MS = 60_000L
        private const val SWEEP_INTERVAL_MS = 10 * 60_000L
    }
}
