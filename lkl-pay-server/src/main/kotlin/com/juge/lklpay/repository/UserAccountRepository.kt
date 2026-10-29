package com.juge.lklpay.repository

import com.juge.lklpay.domain.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserAccountRepository : JpaRepository<UserAccount, Long> {

    fun findByUsername(username: String): UserAccount?

    fun existsByUsername(username: String): Boolean

    /** 只写登录时间；调用方不得先修改旧实体，清空上下文后必须重新读取账号快照。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccount u set u.lastLoginAt = :lastLoginAt where u.id = :userId")
    fun updateLastLoginAt(userId: Long, lastLoginAt: Long): Int

    /** 多笔订单并发付款时也只有一个赢家，且不会覆盖登录时间等非权益字段。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update UserAccount u
           set u.pro = true,
               u.proActivatedAt = :activatedAt,
               u.proOutTradeNo = :outTradeNo
         where u.id = :userId and u.pro = false
        """
    )
    fun grantProIfNotGranted(userId: Long, activatedAt: Long, outTradeNo: String): Int
}