package com.juge.app

import com.juge.app.pay.AlipayRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 授权结果解析：SDK 版本之间返回格式不一致，两种都得认。
 *
 * 老版本给 `result_code=200&auth_code=xxx&state=ok` 这样的表单串（值可能被 URL 编码），
 * 新版本给 JSON。解析错了的表现是「授权成功但拿不到 auth_code」，
 * 用户看到的是「取消了」，而实际上他点了同意——这类问题极难靠肉眼发现，必须单测兜住。
 */
class AlipayRecoveryTest {

    @Test
    fun `JSON 形态能取到 auth_code`() {
        val result = mapOf("result" to """{"result_code":"200","auth_code":"abc123","state":"ok"}""")
        assertEquals("abc123", AlipayRecovery.parseAuthCode(result))
    }

    @Test
    fun `表单串形态能取到 auth_code`() {
        val result = mapOf("result" to "success=true&result_code=200&auth_code=abc123&state=ok")
        assertEquals("abc123", AlipayRecovery.parseAuthCode(result))
    }

    @Test
    fun `auth_code 被 URL 编码时能还原`() {
        val result = mapOf("result" to "result_code=200&auth_code=abc%2B123%3D&state=ok")
        assertEquals("abc+123=", AlipayRecovery.parseAuthCode(result))
    }

    @Test
    fun `用户取消时不返回 auth_code`() {
        assertNull(AlipayRecovery.parseAuthCode(mapOf("result" to "result_code=6001")))
        assertNull(AlipayRecovery.parseAuthCode(mapOf("result" to """{"result_code":"6001"}""")))
    }

    @Test
    fun `空结果与缺字段都返回 null 而不是抛异常`() {
        assertNull(AlipayRecovery.parseAuthCode(null))
        assertNull(AlipayRecovery.parseAuthCode(emptyMap()))
        assertNull(AlipayRecovery.parseAuthCode(mapOf("result" to "")))
        assertNull(AlipayRecovery.parseAuthCode(mapOf("memo" to "没有 result 字段")))
    }
}
