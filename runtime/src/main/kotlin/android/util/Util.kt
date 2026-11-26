package android.util

import exia.js.Host

object Log {
    @JvmStatic fun d(tag: String?, msg: String): Int { Host.log(0, "$tag: $msg"); return 0 }
    @JvmStatic fun i(tag: String?, msg: String): Int { Host.log(1, "$tag: $msg"); return 0 }
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int { Host.log(1, "$tag: $msg $tr"); return 0 }
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int { Host.log(0, "$tag: $msg $tr"); return 0 }
    @JvmStatic fun v(tag: String?, msg: String): Int { Host.log(0, "$tag: $msg"); return 0 }
    @JvmStatic fun w(tag: String?, msg: String): Int { Host.log(2, "$tag: $msg"); return 0 }
    @JvmStatic fun w(tag: String?, msg: String, tr: Throwable?): Int { Host.log(2, "$tag: $msg $tr"); return 0 }
    @JvmStatic fun e(tag: String?, msg: String): Int { Host.log(3, "$tag: $msg"); return 0 }
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int { Host.log(3, "$tag: $msg $tr"); return 0 }
    @JvmStatic fun wtf(tag: String?, msg: String?): Int { Host.log(3, "$tag: $msg"); return 0 }
    @JvmStatic fun println(priority: Int, tag: String?, msg: String): Int { Host.log(1, "$tag: $msg"); return 0 }
}

/** android.util.Base64 on java.util.Base64. */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @JvmStatic fun decode(str: String, flags: Int): ByteArray = decode(str.encodeToByteArray(), flags)
    @JvmStatic fun decode(input: ByteArray, flags: Int): ByteArray {
        val cleaned = String(input.map { it.toInt().toChar() }.filter { !it.isWhitespace() }.toCharArray())
            .replace('-', '+').replace('_', '/')
        val padded = cleaned.trimEnd('=').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return java.util.Base64.getDecoder().decode(padded)
    }
    @JvmStatic fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).encodeToByteArray()
    @JvmStatic fun encodeToString(input: ByteArray, flags: Int): String {
        var s = java.util.Base64.getEncoder().encodeToString(input)
        if (flags and URL_SAFE != 0) s = s.replace('+', '-').replace('/', '_')
        if (flags and NO_PADDING != 0) s = s.trimEnd('=')
        if (flags and NO_WRAP == 0) s = s.chunked(76).joinToString("\n", postfix = "\n")
        return s
    }
}

open class DisplayMetrics {
    @JvmField var widthPixels = 1170
    @JvmField var heightPixels = 2532
    @JvmField var densityDpi = 460
    @JvmField var density = 3f
    @JvmField var xdpi = 460f
    @JvmField var ydpi = 460f
}

open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    fun get(key: K): V? = map[key]
    fun put(key: K, value: V): V? { val old = map.put(key, value); if (map.size > maxSize) map.remove(map.keys.first()); return old }
    fun remove(key: K): V? = map.remove(key)
    fun evictAll() = map.clear()
}
