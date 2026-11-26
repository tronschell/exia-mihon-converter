package eu.kanade.tachiyomi.network

import android.content.Context
import exia.runtime.ExiaCookieJar
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit




class NetworkHelper(context: Context?) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(ExiaCookieJar)
        .addInterceptor(exia.runtime.UncaughtExceptionInterceptor())
        .addInterceptor(exia.runtime.UserAgentInterceptor(::defaultUserAgentProvider))
        .addInterceptor(exia.runtime.CloudflareInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    @Deprecated("The regular client handles Cloudflare by default")
    val cloudflareClient: OkHttpClient get() = client

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"
        val shared by lazy { NetworkHelper(null) }
    }
}

class HttpException(val code: Int) : IllegalStateException("HTTP error $code")

class JavaScriptEngine(context: Context) {
    suspend fun <T> evaluate(script: String): T = throw UnsupportedOperationException("JavaScriptEngine")
}
