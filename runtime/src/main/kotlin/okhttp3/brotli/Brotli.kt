// Exia: the app's network stack already decodes br/zstd/gzip, so these only name the encodings.
package okhttp3.brotli

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.Source

object Brotli : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding: String get() = "br"
    override fun decompress(compressedSource: BufferedSource): Source = compressedSource
}

object BrotliInterceptor : okhttp3.Interceptor by CompressionInterceptor(Brotli, okhttp3.Gzip)
