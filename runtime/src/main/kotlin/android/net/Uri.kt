package android.net

import java.net.URLDecoder
import java.net.URLEncoder

/** android.net.Uri for the calls extensions make: parse, parts, encode and a query builder. */
abstract class Uri {
    abstract fun getScheme(): String?
    abstract fun getHost(): String?
    abstract fun getPath(): String?
    abstract fun getEncodedPath(): String?
    abstract fun getQuery(): String?
    abstract fun getQueryParameter(key: String): String?
    abstract fun getPathSegments(): List<String>
    abstract fun getLastPathSegment(): String?
    abstract fun buildUpon(): Builder

    private class Parsed(val text: String) : Uri() {
        private val uri = java.net.URI(text)
        override fun getScheme(): String? = uri.scheme
        override fun getHost(): String? = uri.host
        override fun getPath(): String? = uri.path
        override fun getEncodedPath(): String? = uri.rawPath
        override fun getQuery(): String? = uri.query
        override fun getQueryParameter(key: String): String? =
            uri.rawQuery?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { decode(it[0]) == key }?.let { if (it.size > 1) decode(it[1]) else "" }
        override fun getPathSegments(): List<String> = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        override fun getLastPathSegment(): String? = getPathSegments().lastOrNull()
        override fun buildUpon(): Builder = Builder(text)
        override fun toString(): String = text
    }

    class Builder internal constructor(private var base: String) {
        constructor() : this("")
        private val query = mutableListOf<String>()
        fun appendQueryParameter(key: String, value: String?): Builder = apply { query += encode(key) + "=" + encode(value ?: "") }
        fun appendPath(segment: String): Builder = apply { base = base.trimEnd('/') + "/" + encode(segment) }
        fun appendEncodedPath(path: String): Builder = apply { base = base.trimEnd('/') + "/" + path.trimStart('/') }
        fun build(): Uri = parse(toString())
        override fun toString(): String =
            if (query.isEmpty()) base else base + (if ('?' in base) "&" else "?") + query.joinToString("&")
    }

    companion object {
        @JvmStatic fun parse(uriString: String): Uri = Parsed(uriString)
        @JvmStatic fun encode(s: String?): String? = s?.let { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        @JvmStatic fun encode(s: String?, allow: String?): String? = encode(s)
        @JvmStatic fun decode(s: String?): String? = s?.let { URLDecoder.decode(it, "UTF-8") }
    }
}
