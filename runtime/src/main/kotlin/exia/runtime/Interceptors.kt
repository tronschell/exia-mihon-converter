package exia.runtime

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

// Mihon's default client interceptors. Keiyoushi checks for them by simple class name, so the names match Mihon's.

/** Errors other than IOException would cancel OkHttp's dispatcher thread; Mihon turns them into IOExceptions. */
class UncaughtExceptionInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: Exception) {
        if (e is IOException) throw e else throw IOException(e)
    }
}

class UserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return if (request.header("User-Agent").isNullOrEmpty()) {
            chain.proceed(request.newBuilder().removeHeader("User-Agent").addHeader("User-Agent", userAgent()).build())
        } else {
            chain.proceed(request)
        }
    }
}

/** A site behind a Cloudflare check: the app shows Verify Website, solves it in a web view and retries. */
class CloudflareBlockedException(val url: String) : IOException("Cloudflare check at $url")

class CloudflareInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code in CHALLENGE_CODES && response.header("Server") in SERVERS) {
            val solved = exia.js.Host.challenge(request.url.toString(), request.header("User-Agent") ?: NetworkHelper.DEFAULT_USER_AGENT)
            if (solved) {
                response.close()
                return chain.proceed(request)
            }
            throw CloudflareBlockedException(request.url.toString())
        }
        return response
    }

    private companion object {
        val CHALLENGE_CODES = setOf(403, 503)
        val SERVERS = setOf("cloudflare-nginx", "cloudflare")
    }
}
