// Exia's OkHttpClient: same public API as OkHttp 5.4, but calls go through the app's network stack
// (URLSession on iOS) instead of sockets. Request, Response, Headers, HttpUrl, bodies and the rest are
// OkHttp's own classes; only the client, its builder, calls and the dispatcher are replaced.
package okhttp3

import exia.runtime.ExiaCall
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

open class OkHttpClient internal constructor(builder: Builder) : Call.Factory {
    constructor() : this(Builder())

    private val interceptors: List<Interceptor> = builder.interceptors.toList()
    private val networkInterceptors: List<Interceptor> = builder.networkInterceptors.toList()
    private val dispatcher: Dispatcher = builder.dispatcher
    private val followRedirects: Boolean = builder.followRedirects
    private val followSslRedirects: Boolean = builder.followSslRedirects
    private val cookieJar: CookieJar = builder.cookieJar
    private val cache: Cache? = builder.cache
    private val authenticator: Authenticator = builder.authenticator
    private val retryOnConnectionFailure: Boolean = builder.retryOnConnectionFailure
    private val callTimeoutMillis: Int = builder.callTimeout
    private val connectTimeoutMillis: Int = builder.connectTimeout
    private val readTimeoutMillis: Int = builder.readTimeout
    private val writeTimeoutMillis: Int = builder.writeTimeout

    fun dispatcher(): Dispatcher = dispatcher
    fun interceptors(): List<Interceptor> = interceptors
    fun networkInterceptors(): List<Interceptor> = networkInterceptors
    fun followRedirects(): Boolean = followRedirects
    fun followSslRedirects(): Boolean = followSslRedirects
    fun cookieJar(): CookieJar = cookieJar
    fun cache(): Cache? = cache
    fun authenticator(): Authenticator = authenticator
    fun proxyAuthenticator(): Authenticator = Authenticator.NONE
    fun retryOnConnectionFailure(): Boolean = retryOnConnectionFailure
    fun callTimeoutMillis(): Int = callTimeoutMillis
    fun connectTimeoutMillis(): Int = connectTimeoutMillis
    fun readTimeoutMillis(): Int = readTimeoutMillis
    fun writeTimeoutMillis(): Int = writeTimeoutMillis

    override fun newCall(request: Request): Call = ExiaCall(this, request)

    open fun newBuilder(): Builder = Builder(this)

    open fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
        throw UnsupportedOperationException("WebSockets aren't supported")

    class Builder() {
        internal var dispatcher: Dispatcher = Dispatcher()
        internal val interceptors: MutableList<Interceptor> = mutableListOf()
        internal val networkInterceptors: MutableList<Interceptor> = mutableListOf()
        internal var followRedirects = true
        internal var followSslRedirects = true
        internal var cookieJar: CookieJar = CookieJar.NO_COOKIES
        internal var cache: Cache? = null
        internal var authenticator: Authenticator = Authenticator.NONE
        internal var retryOnConnectionFailure = true
        internal var callTimeout = 0
        internal var connectTimeout = 10_000
        internal var readTimeout = 10_000
        internal var writeTimeout = 10_000

        internal constructor(client: OkHttpClient) : this() {
            dispatcher = client.dispatcher
            interceptors += client.interceptors
            networkInterceptors += client.networkInterceptors
            followRedirects = client.followRedirects
            followSslRedirects = client.followSslRedirects
            cookieJar = client.cookieJar
            cache = client.cache
            authenticator = client.authenticator
            retryOnConnectionFailure = client.retryOnConnectionFailure
            callTimeout = client.callTimeoutMillis
            connectTimeout = client.connectTimeoutMillis
            readTimeout = client.readTimeoutMillis
            writeTimeout = client.writeTimeoutMillis
        }

        fun dispatcher(dispatcher: Dispatcher) = apply { this.dispatcher = dispatcher }
        fun interceptors(): MutableList<Interceptor> = interceptors
        fun addInterceptor(interceptor: Interceptor) = apply { interceptors += interceptor }
        @JvmName("-addInterceptor")
        inline fun addInterceptor(crossinline block: (chain: Interceptor.Chain) -> Response) =
            addInterceptor(Interceptor { chain -> block(chain) })
        fun networkInterceptors(): MutableList<Interceptor> = networkInterceptors
        fun addNetworkInterceptor(interceptor: Interceptor) = apply { networkInterceptors += interceptor }
        @JvmName("-addNetworkInterceptor")
        inline fun addNetworkInterceptor(crossinline block: (chain: Interceptor.Chain) -> Response) =
            addNetworkInterceptor(Interceptor { chain -> block(chain) })
        fun retryOnConnectionFailure(value: Boolean) = apply { retryOnConnectionFailure = value }
        fun authenticator(value: Authenticator) = apply { authenticator = value }
        fun followRedirects(value: Boolean) = apply { followRedirects = value }
        fun followSslRedirects(value: Boolean) = apply { followSslRedirects = value }
        fun cookieJar(value: CookieJar) = apply { cookieJar = value }
        fun cache(value: Cache?) = apply { cache = value }
        fun callTimeout(timeout: Long, unit: TimeUnit) = apply { callTimeout = unit.toMillis(timeout).toInt() }
        fun callTimeout(duration: Duration) = apply { callTimeout = duration.inWholeMilliseconds.toInt() }
        fun connectTimeout(timeout: Long, unit: TimeUnit) = apply { connectTimeout = unit.toMillis(timeout).toInt() }
        fun connectTimeout(duration: Duration) = apply { connectTimeout = duration.inWholeMilliseconds.toInt() }
        fun readTimeout(timeout: Long, unit: TimeUnit) = apply { readTimeout = unit.toMillis(timeout).toInt() }
        fun readTimeout(duration: Duration) = apply { readTimeout = duration.inWholeMilliseconds.toInt() }
        fun writeTimeout(timeout: Long, unit: TimeUnit) = apply { writeTimeout = unit.toMillis(timeout).toInt() }
        fun writeTimeout(duration: Duration) = apply { writeTimeout = duration.inWholeMilliseconds.toInt() }
        fun pingInterval(interval: Long, unit: TimeUnit) = this
        fun protocols(protocols: List<Protocol>) = this
        fun dns(dns: Dns) = this
        fun proxy(proxy: java.net.Proxy?) = this
        fun connectionPool(pool: ConnectionPool) = this
        fun build(): OkHttpClient = OkHttpClient(this)
    }
}

/** Calls run one at a time per green thread; the dispatcher only keeps its settings. */
class Dispatcher() {
    var maxRequests: Int = 64
    var maxRequestsPerHost: Int = 5
    var idleCallback: Runnable? = null
    fun cancelAll() {}
    fun queuedCallsCount(): Int = 0
    fun runningCallsCount(): Int = 0
}
