package com.juge.lklpay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 本地维护任务的参数。
 *
 * 这些任务都跑在单实例上（与 [com.juge.lklpay.service.PayApiThrottle] 的内存计数前提一致），
 * 不需要分布式调度。
 */
@ConfigurationProperties(prefix = "juge.maintenance")
data class MaintenanceProperties(
    /** 未支付订单保留时长（小时），超过即删除 */
    var staleOrderRetentionHours: Long = 48,
    // 这里**故意没有** cleanupIntervalMinutes 字段。
    // @Scheduled 的 fixedDelayString 在 Bean 创建期由 Spring 直接解析
    // `${juge.maintenance.cleanup-interval-minutes:60}` 占位符，
    // 拿不到运行时的 @ConfigurationProperties 实例；早先在这里放一个同名字段
    // 却无人读取，会让「改配置有没有生效」变得无法判断。该键由调度器直接读 yml。
)
