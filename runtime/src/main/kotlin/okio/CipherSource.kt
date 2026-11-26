package okio

import javax.crypto.Cipher

/**
 * okio's CipherSource for the extension engine: the app's cipher works on whole messages, so this reads the
 * source to the end, runs the cipher once and serves the result.
 */
class CipherSource(private val source: BufferedSource, val cipher: Cipher) : Source {
    private var result: Buffer? = null
    private var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
        check(!closed) { "closed" }
        val out = result ?: Buffer().also { it.write(cipher.doFinal(source.readByteArray())); result = it }
        if (out.size == 0L) return -1
        return out.read(sink, byteCount)
    }

    override fun timeout(): Timeout = source.timeout()

    override fun close() { closed = true; source.close() }
}
