package exia.runtime

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Cookies are kept by the app's network stack (HTTPCookieStorage), so the jar itself stays empty. */
object ExiaCookieJar : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
}

/** MD5 (RFC 1321), for Mihon's source ids and the extensions that hash with MessageDigest. */
object Md5 {
    private val S = intArrayOf(7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21)
    private val K = IntArray(64) { (Math.abs(Math.sin((it + 1).toDouble())) * 4294967296.0).toLong().toInt() }

    fun digest(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 8) / 64 + 1) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 8 + i] = (bitLength ushr (8 * i)).toByte()
        var a0 = 0x67452301; var b0 = 0xefcdab89.toInt(); var c0 = 0x98badcfe.toInt(); var d0 = 0x10325476
        val m = IntArray(16)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                m[i] = (padded[chunk + i * 4].toInt() and 0xff) or ((padded[chunk + i * 4 + 1].toInt() and 0xff) shl 8) or
                    ((padded[chunk + i * 4 + 2].toInt() and 0xff) shl 16) or ((padded[chunk + i * 4 + 3].toInt() and 0xff) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                var f: Int; val g: Int
                when (i / 16) {
                    0 -> { f = (b and c) or (b.inv() and d); g = i }
                    1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                f += a + K[i] + m[g]
                a = d; d = c; c = b
                b += Integer.rotateLeft(f, S[i])
            }
            a0 += a; b0 += b; c0 += c; d0 += d
        }
        val out = ByteArray(16)
        for ((n, v) in intArrayOf(a0, b0, c0, d0).withIndex()) for (i in 0 until 4) out[n * 4 + i] = (v ushr (8 * i)).toByte()
        return out
    }
}

/** A JSON string literal for [s]. */
fun jsonString(s: String): String {
    val b = StringBuilder(s.length + 2).append('"')
    for (c in s) when {
        c == '"' -> b.append("\\\"")
        c == '\\' -> b.append("\\\\")
        c == '\n' -> b.append("\\n")
        c == '\r' -> b.append("\\r")
        c == '\t' -> b.append("\\t")
        c < ' ' -> b.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> b.append(c)
    }
    return b.append('"').toString()
}
