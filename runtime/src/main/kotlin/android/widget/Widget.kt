package android.widget

import android.content.Context
import exia.js.Host

open class EditText

/** Extensions show toasts from their settings ("restart the app to apply"); the app has no toast for them, so they're logged. */
open class Toast(private var text: CharSequence?) {
    open fun show() { Host.log(1, "toast: $text") }
    open fun cancel() {}
    open fun setText(s: CharSequence?) { text = s }
    open fun setText(resId: Int) {}
    open fun setDuration(duration: Int) {}
    open fun getDuration(): Int = LENGTH_SHORT

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        @JvmStatic fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast = Toast(text)
        @JvmStatic fun makeText(context: Context?, resId: Int, duration: Int): Toast = Toast(null)
    }
}
