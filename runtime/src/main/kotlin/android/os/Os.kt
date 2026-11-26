package android.os

import exia.js.Host

open class Bundle

object Build {
    @JvmField val ID: String = "EXIA"
    @JvmField val MANUFACTURER: String = "Apple"
    @JvmField val MODEL: String = "iPhone"
    object VERSION {
        @JvmField val RELEASE: String = "14"
        @JvmField val SDK_INT: Int = 34
    }
}

object SystemClock {
    @JvmStatic fun elapsedRealtime(): Long = Host.now().toLong()
    @JvmStatic fun uptimeMillis(): Long = Host.now().toLong()
    @JvmStatic fun sleep(ms: Long) { Host.sleep(ms.toDouble()) }
}

class LocaleList private constructor(private val locales: List<java.util.Locale>) {
    fun get(index: Int): java.util.Locale = locales[index]
    fun size(): Int = locales.size
    fun isEmpty(): Boolean = locales.isEmpty()
    companion object {
        @JvmStatic fun getDefault(): LocaleList = LocaleList(listOf(java.util.Locale("es")))
    }
}

/** One looper: work posted to it runs on a new green thread (extensions only use it to reach the main thread). */
class Looper private constructor() {
    companion object {
        private val main = Looper()
        @JvmStatic fun getMainLooper(): Looper = main
        @JvmStatic fun myLooper(): Looper? = main
        @JvmStatic fun prepare() {}
    }
}

open class Handler(private val looper: Looper) {
    constructor() : this(Looper.getMainLooper())
    fun getLooper(): Looper = looper
    fun post(r: Runnable): Boolean { Thread(r).start(); return true }
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        Thread { exia.js.Host.sleep(delayMillis.toDouble()); r.run() }.start(); return true
    }
    fun removeCallbacks(r: Runnable) {}
    fun removeCallbacksAndMessages(token: Any?) {}
}

class ParcelFileDescriptor private constructor(val file: java.io.File) : java.io.Closeable {
    override fun close() {}
    companion object {
        const val MODE_READ_ONLY = 0x10000000
        @JvmStatic fun open(file: java.io.File, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor(file)
    }
}
