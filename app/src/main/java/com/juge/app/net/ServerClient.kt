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

    /** 正式环境走备案域名；本地联调改为 http://10.0.2.2:8081（模拟器）或局域网 IP */
    var baseUrl: String = "https://puretxt.cn"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    fun get(path: String, token: String? = null, params: Map<String, String> = emptyMap()): JSONObject {
        val query = params.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        val url = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            applyHeaders(token)
        }
        return readResponse(conn)
    }

    fun post(path: String, body: JSONObject, token: String? = null): JSONObject {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            applyHeaders(token)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return readResponse(conn)
    }

    private fun HttpURLConnection.applyHeaders(token: String?) {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
    }

    /**
     * 非 2xx 也要读 errorStream：服务端的失败响应体里带着 code 与 message，
     * 那正是客户端要用来区分「登录失效」和「网络故障」的依据。
     */
    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return runCatching { JSONObject(text) }.getOrElse { JSONObject() }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}