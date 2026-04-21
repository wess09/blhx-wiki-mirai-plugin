package org.iris.wiki.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration

object HttpUtils {

    private val cookie: String = ""

    private var client: OkHttpClient = OkHttpClient().newBuilder().connectTimeout(Duration.ofMillis(20000)).build()

    private val ua = listOf(
        "Mozilla/5.0 (Windows; U; MSIE 9.0; Windows NT 9.0; en-US);",
        "Mozilla/5.0 (compatible; MSIE 10.0; Macintosh; Intel Mac OS X 10_7_3; Trident/6.0)'",
        "Mozilla/5.0 (compatible; MSIE 8.0; Windows NT 6.1; Trident/4.0; GTB7.4; InfoPath.2; SV1; .NET CLR 3.3.69573; WOW64; en-US)",
        "Opera/9.80 (X11; Linux i686; U; ru) Presto/2.8.131 Version/11.11",
        "Mozilla/5.0 (Windows NT 6.2; Win64; x64; rv:16.0.1) Gecko/20121011 Firefox/16.0.1",
        "Mozilla/5.0 (X11; Ubuntu; Linux i686; rv:15.0) Gecko/20100101 Firefox/15.0.1",
        "Mozilla/5.0 (iPad; CPU OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5355d Safari/8536.25"
    )

    private fun sendRequest(request: Request): String {
        return try {
            client.newCall(request).execute().use { response ->
                response.body?.string() ?: ""
            }
        } catch (_: Exception) {
            ""
        }
//        return json.parseToJsonElement(body)
    }

    private fun sendByteRequest(request: Request): ByteArray? {
        return try {
            client.newCall(request).execute().use { response ->
                response.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildGetRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("cookie", cookie)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("user-agent", ua.random())
            .get()
            .build()
    }

    suspend fun get(url: String, useCache: Boolean = true): String = withContext(Dispatchers.IO) {
        val request = buildGetRequest(url)
        if (useCache) {
            RemoteCacheUtils.getText(url) { sendRequest(request) }
        } else {
            sendRequest(request)
        }
    }

    fun post(url: String, postBody: String): String {
        val media = "application/x-www-form-urlencoded; charset=utf-8"
        val request = Request.Builder().url(url)
            .header("Content-Type", media)
            .header("user-agent", ua.random())
            .post(postBody.toRequestBody(media.toMediaTypeOrNull())).build()
        return sendRequest(request)
    }


    fun getBytes(url: String, useCache: Boolean = true): ByteArray? {
        val request = buildGetRequest(url)
        return if (useCache) {
            RemoteCacheUtils.getBinary(url) { sendByteRequest(request) }
        } else {
            sendByteRequest(request)
        }
    }

    fun getByteArray(url: String, useCache: Boolean = true): ByteArrayOutputStream? {
        val bytes = getBytes(url, useCache) ?: return null
        return ByteArrayOutputStream(bytes.size).apply {
            ByteArrayInputStream(bytes).copyTo(this)
        }
    }

    fun clearRemoteCache() {
        RemoteCacheUtils.clearAll()
    }

}
