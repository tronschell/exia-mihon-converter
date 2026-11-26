package exia.runtime

import exia.js.Host
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Timeout
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/** A call: the client's interceptors, then its network interceptors, then the app's network stack. */
class ExiaCall(val client: OkHttpClient, private val originalRequest: Request) : Call {
    @Volatile private var canceled = false
    private var executed = false

    override fun request(): Request = originalRequest
    override fun execute(): Response {
        check(!executed) { "Already Executed" }
        executed = true
        return Chain(this, client.interceptors() + client.networkInterceptors() + Terminal, 0, originalRequest).proceed(originalRequest)
    }

    override fun enqueue(responseCallback: Callback) {
        Thread {
            val response = try {
                execute()
            } catch (e: IOException) {
                responseCallback.onFailure(this, e)
                return@Thread
            } catch (e: Throwable) {
                responseCallback.onFailure(this, IOException(e.toString(), e))
                return@Thread
            }
            try {
                responseCallback.onResponse(this, response)
            } catch (e: Throwable) {
                Host.log(3, "callback failed: $e")
            }
        }.start()
    }

    override fun cancel() { canceled = true }
    override fun isExecuted(): Boolean = executed
    override fun isCanceled(): Boolean = canceled
    override fun timeout(): Timeout = Timeout.NONE
    override fun addEventListener(eventListener: EventListener) {}
    override fun <T : Any> tag(type: KClass<T>): T? = null
    override fun <T> tag(type: Class<out T>): T? = null
    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
    override fun clone(): Call = ExiaCall(client, originalRequest)

    private object Terminal : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val call = chain.call() as ExiaCall
            if (call.canceled) throw IOException("Canceled")
            return Network.send(chain.request(), call.client)
        }
    }

    private class Chain(val call: ExiaCall, val interceptors: List<Interceptor>, val index: Int, val request: Request) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            check(index < interceptors.size)
            return interceptors[index].intercept(Chain(call, interceptors, index + 1, request))
        }
        override fun connection(): Connection? = null
        override fun call(): Call = call
        override fun connectTimeoutMillis(): Int = call.client.connectTimeoutMillis()
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = call.client.readTimeoutMillis()
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = call.client.writeTimeoutMillis()
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override val followSslRedirects: Boolean get() = call.client.followSslRedirects()
        override val followRedirects: Boolean get() = call.client.followRedirects()
        override val dns: okhttp3.Dns get() = throw UnsupportedOperationException()
        override fun withDns(dns: okhttp3.Dns): Interceptor.Chain = this
        override val socketFactory: javax.net.SocketFactory get() = throw UnsupportedOperationException()
        override fun withSocketFactory(socketFactory: javax.net.SocketFactory): Interceptor.Chain = this
        override val retryOnConnectionFailure: Boolean get() = call.client.retryOnConnectionFailure()
        override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain = this
        override val authenticator: okhttp3.Authenticator get() = call.client.authenticator()
        override fun withAuthenticator(authenticator: okhttp3.Authenticator): Interceptor.Chain = this
        override val cookieJar: okhttp3.CookieJar get() = call.client.cookieJar()
        override fun withCookieJar(cookieJar: okhttp3.CookieJar): Interceptor.Chain = this
        override val cache: okhttp3.Cache? get() = call.client.cache()
        override fun withCache(cache: okhttp3.Cache?): Interceptor.Chain = this
        override val proxy: java.net.Proxy? get() = null
        override fun withProxy(proxy: java.net.Proxy?): Interceptor.Chain = this
        override val proxySelector: java.net.ProxySelector get() = throw UnsupportedOperationException()
        override fun withProxySelector(proxySelector: java.net.ProxySelector): Interceptor.Chain = this
        override val proxyAuthenticator: okhttp3.Authenticator get() = okhttp3.Authenticator.NONE
        override fun withProxyAuthenticator(proxyAuthenticator: okhttp3.Authenticator): Interceptor.Chain = this
        override val sslSocketFactoryOrNull: javax.net.ssl.SSLSocketFactory? get() = null
        override fun withSslSocketFactory(sslSocketFactory: javax.net.ssl.SSLSocketFactory?, x509TrustManager: javax.net.ssl.X509TrustManager?): Interceptor.Chain = this
        override val x509TrustManagerOrNull: javax.net.ssl.X509TrustManager? get() = null
        override val hostnameVerifier: javax.net.ssl.HostnameVerifier get() = throw UnsupportedOperationException()
        override fun withHostnameVerifier(hostnameVerifier: javax.net.ssl.HostnameVerifier): Interceptor.Chain = this
        override val certificatePinner: okhttp3.CertificatePinner get() = okhttp3.CertificatePinner.DEFAULT
        override fun withCertificatePinner(certificatePinner: okhttp3.CertificatePinner): Interceptor.Chain = this
        override val connectionPool: okhttp3.ConnectionPool get() = throw UnsupportedOperationException()
        override fun withConnectionPool(connectionPool: okhttp3.ConnectionPool): Interceptor.Chain = this
        override val eventListener: EventListener get() = EventListener.NONE
    }
}

/** The app side of a call: the request goes out as JSON metadata plus raw body bytes. */
object Network {
    private val json = Json { ignoreUnknownKeys = true }

    fun send(request: Request, client: OkHttpClient): Response {
        var headers = request.headers
        val cookies = client.cookieJar().loadForRequest(request.url)
        if (cookies.isNotEmpty() && headers["Cookie"] == null) {
            headers = headers.newBuilder().add("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" }).build()
        }
        var body: ByteArray? = null
        val requestBody = request.body
        if (requestBody != null) {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            body = buffer.readByteArray()
            requestBody.contentType()?.let { type ->
                if (headers["Content-Type"] == null) headers = headers.newBuilder().add("Content-Type", type.toString()).build()
            }
        }
        val meta = buildJsonObject {
            put("method", request.method)
            put("url", request.url.toString())
            put("followRedirects", client.followRedirects())
            put("headers", buildJsonArray { for ((k, v) in headers) add(buildJsonArray { add(JsonPrimitive(k)); add(JsonPrimitive(v)) }) })
        }
        val reply = Host.fetch(meta.toString(), body)
        val responseMeta = json.parseToJsonElement(reply[0] as String).jsonObject
        val responseBytes = reply[1] as ByteArray
        val responseHeaders = Headers.Builder().apply {
            for (pair in responseMeta["headers"]?.jsonArray ?: JsonArray(emptyList())) {
                val p = pair.jsonArray
                addUnsafeNonAscii(p[0].jsonPrimitive.content, p[1].jsonPrimitive.content)
            }
        }.build()
        val finalUrl = responseMeta["url"]?.jsonPrimitive?.content ?: request.url.toString()
        val finalRequest = if (finalUrl != request.url.toString()) request.newBuilder().url(finalUrl.toHttpUrl()).build() else request
        val response = Response.Builder()
            .request(finalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(responseMeta["status"]!!.jsonPrimitive.int)
            .message(responseMeta["statusText"]?.jsonPrimitive?.content ?: "")
            .headers(responseHeaders)
            .body(responseBytes.toResponseBody(responseHeaders["Content-Type"]?.toMediaTypeOrNull()))
            .sentRequestAtMillis(0)
            .receivedResponseAtMillis(0)
            .build()
        val setCookies = okhttp3.Cookie.parseAll(finalRequest.url, responseHeaders)
        if (setCookies.isNotEmpty()) client.cookieJar().saveFromResponse(finalRequest.url, setCookies)
        return response
    }
}
