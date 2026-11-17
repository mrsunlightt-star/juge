package com.juge.app.net

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 服务端 HTTP 客户端。
 *
 * 全项目唯一的服务端地址入口：切环境只改 [baseUrl] 一处。
 * 这里都是阻塞调用，须在 IO 线程执行——各 API 封装内部已自行切线程。
 */
object ServerClient {

    /**
     * 正式环境走备案域名；本地联调改为 http://10.0.2.2:8081（模拟器）或局域网 IP 后重新编译。
     * 注意：联调改成 http 时会被 networkSecurityConfig 拦截，需同步在
     * res/xml/network_security_config.xml 的 debug-overrides 中临时放行。
     *
     * 这里**不是**防篡改手段，别指望它挡住 Xposed：`val` 编译后就是个普通的静态 final 字段，
     * 挂个 hook 换个值照样生效。真正的防线是 HTTPS + 证书 pinning
     * （res/xml/network_security_config.xml）：地址被改到攻击者服务器后，
     * 那台机器拿不出被 pin 的中间证书，TLS 握手直接失败。
     */
    val baseUrl: String = "https://puretxt.cn"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    fun get(path: String, params: Map<String, String> = emptyMap()): Response {
        val query = params.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        val url = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            applyHeaders()
        }
        return readResponse(conn)
    }

    fun post(path: String, body: JSONObject): Response {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            applyHeaders()
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return readResponse(conn)
    }

    private fun HttpURLConnection.applyHeaders() {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
    }

    /**
     * 服务端响应：HTTP 状态码 + 解析后的响应体。
     *
     * 状态码**必须**和响应体一起带出来。之前的实现把 body 解析完就把状态码丢掉，
     * 于是 `/query` 拿到 429（限流）和拿到 200 + `success:false` 在客户端长得一模一样：
     * 前者应退避重试，后者该立即停手，但调用方无从区分。
     * 当时的注释还写着「非 2xx 也要读 errorStream，那正是客户端区分故障的依据」——
     * 意图是对的，只是读完就扔了。
     */
    class Response(val httpStatus: Int, val body: JSONObject) {
        val isSuccessHttp: Boolean get() = httpStatus in 200..299

        /** 网络层成功且业务层 success=true */
        val isOk: Boolean get() = isSuccessHttp && body.optBoolean("success")

        val code: String get() = body.optString("code")

        val message: String get() = body.optString("message")
    }

    /**
     * 非 2xx 也要读 errorStream：服务端的失败响应体里带着 code 与 message。
     * 读法与状态码一起返回，调用方才能区分「被限流」「业务拒绝」「网络故障」。
     */
    private fun readResponse(conn: HttpURLConnection): Response {
        val status = conn.responseCode
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        return Response(status, json)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
