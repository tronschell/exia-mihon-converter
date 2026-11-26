package android.content

import exia.js.Host
import java.io.File

open class Context {
    open fun getApplicationContext(): Context = this
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = ExiaPreferences(name)
    open fun getPackageName(): String = "app.exia"
    open fun getCacheDir(): File = File("/cache")
    open fun getCodeCacheDir(): File = File("/codecache")
    open fun getFilesDir(): File = File("/files")
    open fun getClassLoader(): ClassLoader = javaClass.classLoader!!
    open fun getSystemService(name: String): Any? = null
    open fun getResources(): android.content.res.Resources = android.content.res.Resources.getSystem()
    companion object {
        const val MODE_PRIVATE = 0
    }
}

open class ContextWrapper : Context()

interface SharedPreferences {
    fun getAll(): Map<String, *>
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {}
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) {}

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

/** Settings kept by the app, one JSON-encoded value per key ("s:", "b:", "i:", "l:", "f:", "set:" prefixes). */
class ExiaPreferences(private val name: String) : SharedPreferences {
    private fun raw(key: String): String? = Host.prefGet("$name/$key")
    override fun getAll(): Map<String, *> = emptyMap<String, Any>()
    override fun getString(key: String, defValue: String?): String? = raw(key)?.takeIf { it.startsWith("s:") }?.substring(2) ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        raw(key)?.takeIf { it.startsWith("set:") }?.substring(4)?.let { if (it.isEmpty()) emptySet() else it.split('\u0000').toSet() } ?: defValues
    override fun getInt(key: String, defValue: Int): Int = raw(key)?.takeIf { it.startsWith("i:") }?.substring(2)?.toIntOrNull() ?: defValue
    override fun getLong(key: String, defValue: Long): Long = raw(key)?.takeIf { it.startsWith("l:") }?.substring(2)?.toLongOrNull() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = raw(key)?.takeIf { it.startsWith("f:") }?.substring(2)?.toFloatOrNull() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = raw(key)?.takeIf { it.startsWith("b:") }?.let { it == "b:true" } ?: defValue
    override fun contains(key: String): Boolean = raw(key) != null
    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, String?>()
        override fun putString(key: String, value: String?) = apply { changes[key] = value?.let { "s:$it" } }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values?.let { "set:" + it.joinToString("\u0000") } }
        override fun putInt(key: String, value: Int) = apply { changes[key] = "i:$value" }
        override fun putLong(key: String, value: Long) = apply { changes[key] = "l:$value" }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = "f:$value" }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = "b:$value" }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = this
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { for ((k, v) in changes) Host.prefSet("$name/$k", v) }
    }
}

open class Intent {
    constructor()
    constructor(action: String?) 
    constructor(action: String?, uri: android.net.Uri?)
    open fun getData(): android.net.Uri? = null
    open fun setAction(action: String?): Intent = this
    open fun putExtra(name: String, value: String?): Intent = this
    open fun addFlags(flags: Int): Intent = this
}

open class ActivityNotFoundException : RuntimeException()
