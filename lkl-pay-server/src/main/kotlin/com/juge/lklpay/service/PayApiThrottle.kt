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
 *  - 不限制 auth-info：每次都要做一次 RSA 签名，是现成的 CPU 放大点
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
     * 判定必须与自增在**同一个** `compute` 原子段内完成。
     * 之前的写法是先 compute 里 count++、再在锁外读 `bucket.count <= limit` ——
     * 两个并发请求可以同时读到自增后的同一个值，一起放行，实际限额被放大到
     * 与并发度同阶。`compute` 对同一个 key 是串行的，把判定搬进来就没有这个窗口。
     *
     * @param bucketName 接口桶名（create / query / recover / notify / auth-info），各桶独立计数
     * @param clientIp   来源 IP；为空时按全局桶计数（Nginx 未正确传递 XFF 的兜底）
     * @param limitPerMinute 每 IP 每分钟限额
     */
    fun tryConsume(bucketName: String, clientIp: String?, limitPerMinute: Int): Boolean {
        val now = System.currentTimeMillis()
        val key = "$bucketName:${clientIp?.takeIf { it.isNotBlank() } ?: "unknown"}"

        // 判定在 compute 的 lambda 里完成并直接带出来。
        // compute 对同一 key 互斥，所以「自增」与「比较限额」之间不存在别的线程插入点。
        val decision = intArrayOf(0, 0) // [0]=本窗口次数, [1]=是否放行(1/0)
        buckets.compute(key) { _, existing ->
            val current = existing ?: Bucket().also { it.windowStart = now }
            if (now - current.windowStart > WINDOW_MS) {
                current.windowStart = now
                current.count = 0
            }
            current.count++
            decision[0] = current.count
            decision[1] = if (current.count <= limitPerMinute) 1 else 0
            current
        }

        val allowed = decision[1] == 1
        sweepIfNeeded(now)
        if (!allowed) {
            // key 里带 X-Forwarded-For 解析出来的来源地址，受外部请求头控制：
            // 不过一遍净化，就能用 \r\n 往日志里塞伪造行。
            log.warn(
                "支付接口触发限流 bucket={} key={} 本窗口调用次数={}",
                bucketName, Sensitive.safeLog(key), decision[0],
            )
        }
        return allowed
    }

    /**
     * 定期清理过期桶，防止大量随机 IP 撑爆内存。
     *
     * 间隔内被跳过是正常的（CAS 抢不到就说明别的线程刚扫过）。
     * 真正的兜底在 [ensureBounded]：窗口内涌入超过 [MAX_BUCKETS] 个不同 IP 时
     * 直接整表清零——等价于一次重启，只发生在洪水期间，且换来的是内存上界确定。
     */
    private fun sweepIfNeeded(now: Long) {
        val last = lastSweep.get()
        if (now - last < SWEEP_INTERVAL_MS || !lastSweep.compareAndSet(last, now)) return
        val expireBefore = now - WINDOW_MS
        buckets.entries.removeIf { it.value.windowStart < expireBefore }
        ensureBounded()
    }

    private fun ensureBounded() {
        if (buckets.size <= MAX_BUCKETS) return
        log.warn(
            "支付接口限流桶超过 {} 个（当前 {}），整表清零。通常意味着正在被大范围 IP 洪水刷接口。",
            MAX_BUCKETS, buckets.size,
        )
        buckets.clear()
    }

    companion object {
        /** 下单限额：正常用户一次购买产生 1-2 次下单，20 次/分钟已极宽裕 */
        const val CREATE_LIMIT_PER_MINUTE = 20

        /** 查单限额：支付后轮询间隔通常 2-3 秒、持续十几秒，60 次/分钟覆盖重试场景 */
        const val QUERY_LIMIT_PER_MINUTE = 60

        /**
         * 找回限额：每次找回都要换一次 auth_code，成本在支付宝侧的授权校验。
         * 正常用户一生只会用几次，10 次/分钟足够，也挡住了拿随机 auth_code 试探的路子。
         */
        const val RECOVER_LIMIT_PER_MINUTE = 10

        /**
         * auth-info 限额：每次调用做一次 RSA 签名（无网络请求，纯 CPU）。
         * 正常用户只在点「找回」时调一次，10 次/分钟足够。
         */
        const val AUTH_INFO_LIMIT_PER_MINUTE = 10

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

        /** 内存上界：10 万个桶约 6~8 MB，超出即视为洪水 */
        private const val MAX_BUCKETS = 100_000
    }
}
