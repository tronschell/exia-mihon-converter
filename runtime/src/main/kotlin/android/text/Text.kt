package android.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

interface Spanned : CharSequence
interface Spannable : Spanned

private class PlainSpanned(private val text: String) : Spannable {
    override val length: Int get() = text.length
    override fun get(index: Int): Char = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text
}

object Html {
    const val FROM_HTML_MODE_LEGACY = 0
    const val FROM_HTML_MODE_COMPACT = 63

    @JvmStatic fun fromHtml(source: String, flags: Int): Spanned {
        val doc = org.jsoup.Jsoup.parseBodyFragment(source)
        doc.outputSettings().prettyPrint(false)
        doc.select("br").append("\\n")
        doc.select("p, div").prepend("\\n\\n")
        val text = doc.text().replace("\\n", "\n").trim()
        return PlainSpanned(text)
    }

    @JvmStatic fun fromHtml(source: String): Spanned = fromHtml(source, 0)
}

open class SpannableString(private val source: CharSequence) : Spannable {
    override val length: Int get() = source.length
    override fun get(index: Int): Char = source[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = source.subSequence(startIndex, endIndex)
    override fun toString(): String = source.toString()
    open fun setSpan(what: Any?, start: Int, end: Int, flags: Int) {}
}

open class TextPaint : Paint {
    @JvmField var bgColor: Int = 0
    constructor() : super()
    constructor(flags: Int) : super(flags)
    constructor(p: Paint) : super(p)
}

open class Layout {
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
}

/** Wrapped text drawn by the app (CoreText on Apple devices). */
open class StaticLayout(
    private val text: CharSequence,
    private val paint: TextPaint,
    private val width: Int,
    private val align: Layout.Alignment,
    private val spacingMult: Float,
    private val spacingAdd: Float,
    private val includePad: Boolean,
) : Layout() {
    private val lines: List<String> by lazy { wrap() }

    private fun wrap(): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.toString().split('\n')) {
            var line = ""
            for (word in paragraph.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && paint.measureText(candidate) > width) { out += line; line = word } else line = candidate
            }
            out += line
        }
        return out
    }

    private val lineHeight: Float get() = paint.getTextSize() * 1.2f * spacingMult + spacingAdd

    fun getHeight(): Int = (lines.size * lineHeight).toInt()
    fun getLineCount(): Int = lines.size
    fun getWidth(): Int = width

    fun draw(canvas: Canvas) {
        var y = paint.getTextSize()
        for (line in lines) {
            val w = paint.measureText(line)
            val x = when (align) {
                Alignment.ALIGN_CENTER -> (width - w) / 2f
                Alignment.ALIGN_OPPOSITE -> width - w
                else -> 0f
            }
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
    }

    class Builder private constructor(private val text: CharSequence, private val paint: TextPaint, private val width: Int) {
        private var align = Alignment.ALIGN_NORMAL
        private var spacingMult = 1f
        private var spacingAdd = 0f
        private var includePad = true
        fun setAlignment(a: Alignment) = apply { align = a }
        fun setBreakStrategy(s: Int) = this
        fun setHyphenationFrequency(f: Int) = this
        fun setIncludePad(p: Boolean) = apply { includePad = p }
        fun setLineSpacing(add: Float, mult: Float) = apply { spacingAdd = add; spacingMult = mult }
        fun build(): StaticLayout = StaticLayout(text, paint, width, align, spacingMult, spacingAdd, includePad)
        companion object {
            @JvmStatic fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int): Builder =
                Builder(source.subSequence(start, end), paint, width)
        }
    }
}
