package com.juge.lklpay

import com.alipay.api.AlipayClient
import com.alipay.api.domain.AlipayTradeQueryModel
import com.alipay.api.request.AlipayTradeQueryRequest
import com.alipay.api.response.AlipayTradeQueryResponse
import com.juge.lklpay.config.AlipayProperties
import com.juge.lklpay.service.AlipayPayService
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.assertEquals

class AlipayPayServiceTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["REQUESTED_ORDER", "OTHER_ORDER"])
    fun `查询保留网关实际订单号而不是用请求订单号掩盖异常`(returnedNo: String?) {
        val client = mock(AlipayClient::class.java)
        val service = AlipayPayService(mock(AlipayProperties::class.java))
        // 替换尚未初始化的 lazy 客户端，测试不接触证书、密钥或真实网关。
        ReflectionTestUtils.setField(service, "client\$delegate", lazyOf(client))
        val response = AlipayTradeQueryResponse().apply {
            outTradeNo = returnedNo
            code = "10000"
            tradeStatus = "TRADE_SUCCESS"
            totalAmount = "1.99"
        }
        `when`(client.execute(any(AlipayTradeQueryRequest::class.java))).thenReturn(response)

        val result = service.queryOrder("REQUESTED_ORDER")

        assertEquals(returnedNo, result["out_trade_no"])
        val request = ArgumentCaptor.forClass(AlipayTradeQueryRequest::class.java)
        verify(client).execute(request.capture())
        assertEquals("REQUESTED_ORDER", (request.value.bizModel as AlipayTradeQueryModel).outTradeNo)
    }
}
