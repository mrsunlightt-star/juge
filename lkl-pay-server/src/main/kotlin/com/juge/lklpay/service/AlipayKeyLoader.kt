package com.juge.lklpay.service

import java.nio.file.Files
import java.nio.file.Path

/**
 * 支付宝密钥文件读取：统一归一化为单行 Base64。
 *
 * 支付宝 SDK 只接受不带 PEM 头尾、不含换行的 Base64 串。
 * 抽成一处是因为下单/查单/验签（[AlipayPayService]）与身份核验
 * （[AlipayIdentityService]）都要读同一份密钥，两处各写一遍迟早会分叉。
 */
internal object AlipayKeyLoader {

    fun load(path: String): String {
        val content = Files.readString(Path.of(path))
        val single = content
            .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
            .replace(Regex("-----END [A-Z ]+-----"), "")
            .filter { !it.isWhitespace() }
        check(single.isNotEmpty()) { "密钥文件为空：$path" }
        return single
    }
}
