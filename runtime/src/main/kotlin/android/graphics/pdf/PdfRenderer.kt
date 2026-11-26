package android.graphics.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.ParcelFileDescriptor

/** PDF pages aren't rendered in the extension engine yet; opening one reports it as unsupported. */
class PdfRenderer(fd: ParcelFileDescriptor) : java.io.Closeable {
    init { throw UnsupportedOperationException("PDF chapters aren't supported yet") }
    fun getPageCount(): Int = 0
    fun openPage(index: Int): Page = throw UnsupportedOperationException()
    override fun close() {}
    class Page : java.io.Closeable {
        fun getWidth(): Int = 0
        fun getHeight(): Int = 0
        fun render(destination: Bitmap, destClip: Rect?, transform: Matrix?, renderMode: Int) {}
        override fun close() {}
        companion object { const val RENDER_MODE_FOR_DISPLAY = 1 }
    }
}
