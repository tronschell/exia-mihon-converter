// Exia: Keiyoushi caches JSON zstd-compressed on disk; here the cache is stored as is.
@file:JvmName("OkioZstd")

package com.squareup.zstd.okio

import okio.Sink
import okio.Source

fun Source.zstdDecompress(): Source = this
fun Sink.zstdCompress(): Sink = this
