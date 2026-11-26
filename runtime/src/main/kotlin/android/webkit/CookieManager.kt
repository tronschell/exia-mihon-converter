package android.webkit

import exia.js.Host

/** The app's cookie store, which the network and the web view share. */
open class CookieManager private constructor() {
    fun getCookie(url: String): String? = Host.cookieGet(url)
    fun setCookie(url: String, value: String) = Host.cookieSet(url, value)
    fun setCookie(url: String, value: String, callback: ValueCallback<Boolean>?) { Host.cookieSet(url, value); callback?.onReceiveValue(true) }
    fun setAcceptCookie(accept: Boolean) {}
    fun acceptCookie(): Boolean = true
    fun flush() {}
    fun removeAllCookies(callback: ValueCallback<Boolean>?) { callback?.onReceiveValue(false) }
    companion object {
        private val shared = CookieManager()
        @JvmStatic fun getInstance(): CookieManager = shared
    }
}

fun interface ValueCallback<T> { fun onReceiveValue(value: T) }

object URLUtil {
    @JvmStatic fun isValidUrl(url: String?): Boolean = url != null && (url.startsWith("http://") || url.startsWith("https://"))
}
