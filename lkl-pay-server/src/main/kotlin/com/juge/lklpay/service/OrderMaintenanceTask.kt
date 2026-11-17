package com.juge.lklpay.service

import com.juge.lklpay.config.MaintenanceProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 订单表的定期维护。
 *
 * 为什么需要它：`@EnableScheduling` 之前是空挂着的注解（全仓 0 处 `@Scheduled`），
 * 而 `create` 是公网无鉴权入口、每次调用写一行 CREATED 且永不删除。
 * 攻击者或一个写错的客户端重试循环就能把这唯一一张表刷到几十万行，
 * 之后 `query` 的异常日志、备份、Heap 压力都跟着上来。
 *
 * 只清 CREATED：
 *   - PAID 是资产凭证，删了用户就没法找回权益
 *   - CLOSED 是争议与对账的留档
 * 保留期默认 48 小时，远大于订单本身的 120 分钟有效期——留出足够的人工排查窗口。
 */
@Component
class OrderMaintenanceTask(
    private val payOrderService: PayOrderService,
    private val props: MaintenanceProperties,
) {

    private val log = LoggerFactory.getLogger(OrderMaintenanceTask::class.java)

    /**
     * 每小时清一次。`initialDelay` 同样给一个完整间隔，避开启动期。
     * `fixedDelay` 而非 `fixedRate`：单次清理耗时波动时不会堆积任务。
     * 用 `timeUnit` 表达分钟，避免在字符串里手写 `* 60 * 1000` —— 那种写法一改单位就错。
     */
    @Scheduled(
        fixedDelayString = "\${juge.maintenance.cleanup-interval-minutes:60}",
        initialDelayString = "\${juge.maintenance.cleanup-interval-minutes:60}",
        timeUnit = TimeUnit.MINUTES,
    )
    fun cleanup() {
        runCatching {
            payOrderService.cleanupStaleCreated(props.staleOrderRetentionHours * 60 * 60 * 1000)
        }.onFailure {
            // 清理失败不能影响服务：下一轮再来。但要留日志，否则永远发现不了
            log.error("僵尸订单清理失败，下一轮重试", it)
        }
    }
}
