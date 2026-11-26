package android.content.res

import android.util.DisplayMetrics

open class Resources {
    open fun getDisplayMetrics(): DisplayMetrics = DisplayMetrics()
    open fun getConfiguration(): Configuration = Configuration()
    companion object {
        private val system = Resources()
        @JvmStatic fun getSystem(): Resources = system
    }
}

open class Configuration {
    @JvmField var uiMode: Int = 0x20
}
