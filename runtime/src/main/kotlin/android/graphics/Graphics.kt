// android.graphics on the app's image engine (ImageIO/CoreGraphics on Apple devices). Bitmaps live in the app as
// handles; a Canvas keeps Android's transform stack here and sends each draw with its current matrix.
package android.graphics

import exia.js.Host
import java.io.InputStream
import java.io.OutputStream

class Bitmap internal constructor(internal val handle: Int, private var w: Int, private var h: Int) {
    enum class Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    private var recycled = false
    fun getWidth(): Int = w
    fun getHeight(): Int = h
    fun isRecycled(): Boolean = recycled
    fun isMutable(): Boolean = true
    fun getConfig(): Config = Config.ARGB_8888
    fun getByteCount(): Int = w * h * 4

    fun recycle() { if (!recycled) { recycled = true; Host.image("free", "{\"id\":$handle}", null) } }

    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean {
        val f = when (format) { CompressFormat.PNG -> "png"; CompressFormat.JPEG -> "jpeg"; else -> "webp" }
        val bytes = Host.imageBytes("encode", "{\"id\":$handle,\"format\":\"$f\",\"quality\":$quality}", null) ?: return false
        stream.write(bytes)
        return true
    }

    fun copy(config: Config, isMutable: Boolean): Bitmap {
        val copy = createBitmap(w, h, config)
        Canvas(copy).drawBitmap(this, 0f, 0f, null)
        return copy
    }

    fun eraseColor(color: Int) { Host.image("fill", "{\"id\":$handle,\"color\":$color,\"m\":[1,0,0,1,0,0]}", null) }

    fun getPixel(x: Int, y: Int): Int { val a = IntArray(1); getPixels(a, 0, 1, x, y, 1, 1); return a[0] }

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val rgba = Host.imageBytes("read", "{\"id\":$handle,\"x\":$x,\"y\":$y,\"w\":$width,\"h\":$height}", null) ?: return
        for (row in 0 until height) for (col in 0 until width) {
            val i = (row * width + col) * 4
            val r = rgba[i].toInt() and 255; val g = rgba[i + 1].toInt() and 255
            val b = rgba[i + 2].toInt() and 255; val a = rgba[i + 3].toInt() and 255
            pixels[offset + row * stride + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        val rgba = ByteArray(width * height * 4)
        for (row in 0 until height) for (col in 0 until width) {
            val p = pixels[offset + row * stride + col]
            val i = (row * width + col) * 4
            rgba[i] = (p shr 16).toByte(); rgba[i + 1] = (p shr 8).toByte(); rgba[i + 2] = p.toByte(); rgba[i + 3] = (p ushr 24).toByte()
        }
        Host.image("write", "{\"id\":$handle,\"x\":$x,\"y\":$y,\"w\":$width,\"h\":$height}", rgba)
    }

    companion object {
        @JvmStatic fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            val r = Host.image("create", "{\"w\":$width,\"h\":$height}", null)
            return Bitmap(r!!.toInt(), width, height)
        }

        @JvmStatic fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
            val out = createBitmap(width, height, Config.ARGB_8888)
            Canvas(out).drawBitmap(source, Rect(x, y, x + width, y + height), Rect(0, 0, width, height), null)
            return out
        }

        @JvmStatic fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap {
            if (m == null) return createBitmap(source, x, y, width, height)
            val cropped = createBitmap(source, x, y, width, height)
            val v = m.values
            val ow = Math.abs(v[0] * width + v[1] * height).toInt().coerceAtLeast(1)
            val oh = Math.abs(v[3] * width + v[4] * height).toInt().coerceAtLeast(1)
            val out = createBitmap(ow, oh, Config.ARGB_8888)
            Canvas(out).drawBitmap(cropped, m, null)
            cropped.recycle()
            return out
        }

        @JvmStatic fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val out = createBitmap(dstWidth, dstHeight, Config.ARGB_8888)
            Canvas(out).drawBitmap(src, Rect(0, 0, src.getWidth(), src.getHeight()), Rect(0, 0, dstWidth, dstHeight), null)
            return out
        }

        internal fun decode(bytes: ByteArray): Bitmap? {
            val r = Host.image("decode", "{}", bytes) ?: return null
            val parts = r.split(',')
            return Bitmap(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }
    }
}

object BitmapFactory {
    class Options {
        @JvmField var inMutable = false
        @JvmField var inJustDecodeBounds = false
        @JvmField var inSampleSize = 1
        @JvmField var inPreferredConfig: Bitmap.Config? = null
        @JvmField var outWidth = 0
        @JvmField var outHeight = 0
        @JvmField var outMimeType: String? = null
    }

    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
        Bitmap.decode(if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length))

    @JvmStatic fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        val b = decodeByteArray(data, offset, length)
        if (opts != null && b != null) { opts.outWidth = b.getWidth(); opts.outHeight = b.getHeight() }
        if (opts?.inJustDecodeBounds == true) { b?.recycle(); return null }
        return b
    }

    @JvmStatic fun decodeStream(stream: InputStream?): Bitmap? = stream?.readBytes()?.let { Bitmap.decode(it) }

    @JvmStatic fun decodeStream(stream: InputStream?, outPadding: Rect?, opts: Options?): Bitmap? {
        val bytes = stream?.readBytes() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

open class Rect {
    @JvmField var left = 0
    @JvmField var top = 0
    @JvmField var right = 0
    @JvmField var bottom = 0
    constructor()
    constructor(left: Int, top: Int, right: Int, bottom: Int) { set(left, top, right, bottom) }
    constructor(r: Rect) { set(r.left, r.top, r.right, r.bottom) }
    fun set(left: Int, top: Int, right: Int, bottom: Int) { this.left = left; this.top = top; this.right = right; this.bottom = bottom }
    fun width(): Int = right - left
    fun height(): Int = bottom - top
    fun isEmpty(): Boolean = left >= right || top >= bottom
    fun centerX(): Int = (left + right) / 2
    fun centerY(): Int = (top + bottom) / 2
    fun offset(dx: Int, dy: Int) { left += dx; right += dx; top += dy; bottom += dy }
    override fun toString(): String = "Rect($left, $top - $right, $bottom)"
}

open class RectF(@JvmField var left: Float, @JvmField var top: Float, @JvmField var right: Float, @JvmField var bottom: Float) {
    constructor() : this(0f, 0f, 0f, 0f)
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

open class Point(@JvmField var x: Int, @JvmField var y: Int) {
    constructor() : this(0, 0)
    override fun equals(other: Any?): Boolean = other is Point && other.x == x && other.y == y
    override fun hashCode(): Int = x * 31 + y
}

open class PointF(@JvmField var x: Float, @JvmField var y: Float) { constructor() : this(0f, 0f) }

/** 2D affine matrix in Android's order: [scaleX, skewX, transX, skewY, scaleY, transY]. */
open class Matrix {
    internal var values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)

    private fun post(a: FloatArray) {
        val m = values
        values = floatArrayOf(
            a[0] * m[0] + a[1] * m[3], a[0] * m[1] + a[1] * m[4], a[0] * m[2] + a[1] * m[5] + a[2],
            a[3] * m[0] + a[4] * m[3], a[3] * m[1] + a[4] * m[4], a[3] * m[2] + a[4] * m[5] + a[5],
        )
    }

    private fun pre(a: FloatArray) {
        val m = values
        values = floatArrayOf(
            m[0] * a[0] + m[1] * a[3], m[0] * a[1] + m[1] * a[4], m[0] * a[2] + m[1] * a[5] + m[2],
            m[3] * a[0] + m[4] * a[3], m[3] * a[1] + m[4] * a[4], m[3] * a[2] + m[4] * a[5] + m[5],
        )
    }

    fun reset() { values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f) }
    fun setScale(sx: Float, sy: Float) { values = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f) }
    fun setTranslate(dx: Float, dy: Float) { values = floatArrayOf(1f, 0f, dx, 0f, 1f, dy) }
    fun setRotate(degrees: Float) { reset(); postRotate(degrees) }
    fun postTranslate(dx: Float, dy: Float): Boolean { post(floatArrayOf(1f, 0f, dx, 0f, 1f, dy)); return true }
    fun postScale(sx: Float, sy: Float): Boolean { post(floatArrayOf(sx, 0f, 0f, 0f, sy, 0f)); return true }
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean {
        postTranslate(-px, -py); postScale(sx, sy); postTranslate(px, py); return true
    }
    fun postRotate(degrees: Float): Boolean {
        val r = Math.toRadians(degrees.toDouble()); val c = Math.cos(r).toFloat(); val s = Math.sin(r).toFloat()
        post(floatArrayOf(c, -s, 0f, s, c, 0f)); return true
    }
    fun postRotate(degrees: Float, px: Float, py: Float): Boolean {
        postTranslate(-px, -py); postRotate(degrees); postTranslate(px, py); return true
    }
    fun preTranslate(dx: Float, dy: Float): Boolean { pre(floatArrayOf(1f, 0f, dx, 0f, 1f, dy)); return true }
    fun preScale(sx: Float, sy: Float): Boolean { pre(floatArrayOf(sx, 0f, 0f, 0f, sy, 0f)); return true }
    fun preRotate(degrees: Float): Boolean {
        val r = Math.toRadians(degrees.toDouble()); val c = Math.cos(r).toFloat(); val s = Math.sin(r).toFloat()
        pre(floatArrayOf(c, -s, 0f, s, c, 0f)); return true
    }
    internal fun concat(other: Matrix) { pre(other.values) }
    internal fun copyFrom(other: Matrix) { values = other.values.copyOf() }
    internal fun json(): String = "[${values[0]},${values[3]},${values[1]},${values[4]},${values[2]},${values[5]}]"
}

open class ColorFilter
open class ColorMatrix(@JvmField val array: FloatArray) { constructor() : this(floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)) }
open class ColorMatrixColorFilter(@JvmField val matrix: ColorMatrix) : ColorFilter()
open class PorterDuffColorFilter(color: Int, mode: PorterDuff.Mode) : ColorFilter()
object PorterDuff { enum class Mode { CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN, LIGHTEN, MULTIPLY, SCREEN, ADD, OVERLAY } }

class Typeface private constructor(@JvmField internal val name: String, @JvmField internal val bold: Boolean) {
    companion object {
        @JvmField val DEFAULT = Typeface("sans-serif", false)
        @JvmField val DEFAULT_BOLD = Typeface("sans-serif", true)
        @JvmField val SANS_SERIF = Typeface("sans-serif", false)
        @JvmField val SERIF = Typeface("serif", false)
        @JvmField val MONOSPACE = Typeface("monospace", false)
        const val NORMAL = 0
        const val BOLD = 1
        @JvmStatic fun createFromFile(file: java.io.File?): Typeface = DEFAULT
        @JvmStatic fun createFromFile(path: String?): Typeface = DEFAULT
        @JvmStatic fun create(family: Typeface?, style: Int): Typeface = if (style and 1 != 0) DEFAULT_BOLD else DEFAULT
    }
}

open class Paint {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Align { LEFT, CENTER, RIGHT }
    class FontMetrics { @JvmField var top = 0f; @JvmField var ascent = 0f; @JvmField var descent = 0f; @JvmField var bottom = 0f; @JvmField var leading = 0f }

    private var color = 0xFF000000.toInt()
    private var style = Style.FILL
    private var strokeWidth = 0f
    private var textSize = 12f
    private var textScaleX = 1f
    private var typeface: Typeface = Typeface.DEFAULT
    private var colorFilter: ColorFilter? = null
    private var strokeJoin = Join.MITER
    private var textAlign = Align.LEFT
    private var antiAlias = false

    constructor()
    constructor(flags: Int) { antiAlias = flags and 1 != 0 }
    constructor(p: Paint) {
        color = p.color; style = p.style; strokeWidth = p.strokeWidth; textSize = p.textSize; textScaleX = p.textScaleX
        typeface = p.typeface; colorFilter = p.colorFilter; strokeJoin = p.strokeJoin; textAlign = p.textAlign; antiAlias = p.antiAlias
    }

    fun setColor(color: Int) { this.color = color }
    fun getColor(): Int = color
    fun setAlpha(a: Int) { color = (color and 0xFFFFFF) or (a shl 24) }
    fun setStyle(style: Style) { this.style = style }
    fun getStyle(): Style = style
    fun setStrokeWidth(w: Float) { strokeWidth = w }
    fun getStrokeWidth(): Float = strokeWidth
    fun setStrokeJoin(j: Join) { strokeJoin = j }
    fun setTextSize(s: Float) { textSize = s }
    fun getTextSize(): Float = textSize
    fun setTextScaleX(s: Float) { textScaleX = s }
    fun setTextAlign(a: Align) { textAlign = a }
    fun setTypeface(t: Typeface?): Typeface? { typeface = t ?: Typeface.DEFAULT; return t }
    fun getTypeface(): Typeface = typeface
    fun setColorFilter(f: ColorFilter?): ColorFilter? { colorFilter = f; return f }
    fun setAntiAlias(a: Boolean) { antiAlias = a }
    fun setFilterBitmap(f: Boolean) {}
    fun setDither(d: Boolean) {}
    fun hasGlyph(s: String): Boolean = true

    internal fun font(): String = "\"font\":{\"size\":$textSize,\"bold\":${typeface.bold},\"family\":\"${typeface.name}\",\"scaleX\":$textScaleX}"
    internal fun json(): String {
        val filter = (colorFilter as? ColorMatrixColorFilter)?.matrix?.array?.joinToString(",", "[", "]") ?: "null"
        return "\"color\":$color,\"style\":\"$style\",\"stroke\":$strokeWidth,\"align\":\"$textAlign\",\"filter\":$filter,${font()}"
    }

    fun measureText(text: String): Float =
        Host.image("measure", "{${font()},\"text\":${exia.runtime.jsonString(text)}}", null)?.toFloat() ?: (text.length * textSize * 0.5f)

    fun getFontMetrics(): FontMetrics = FontMetrics().also {
        it.top = -textSize * 1.06f; it.ascent = -textSize * 0.93f; it.descent = textSize * 0.24f; it.bottom = textSize * 0.27f
    }
}

open class Canvas(private val bitmap: Bitmap) {
    private var matrix = Matrix()
    private val stack = ArrayList<Matrix>()

    fun getWidth(): Int = bitmap.getWidth()
    fun getHeight(): Int = bitmap.getHeight()

    fun save(): Int { stack += Matrix().also { it.copyFrom(matrix) }; return stack.size }
    fun restore() { if (stack.isNotEmpty()) matrix = stack.removeAt(stack.size - 1) }
    fun restoreToCount(count: Int) { while (stack.size >= count && stack.isNotEmpty()) restore() }
    fun translate(dx: Float, dy: Float) { matrix.preTranslate(dx, dy) }
    fun scale(sx: Float, sy: Float) { matrix.preScale(sx, sy) }
    fun rotate(degrees: Float) { matrix.preRotate(degrees) }
    fun rotate(degrees: Float, px: Float, py: Float) { translate(px, py); rotate(degrees); translate(-px, -py) }
    fun concat(m: Matrix?) { if (m != null) matrix.concat(m) }

    private fun draw(src: Bitmap, sx: Int, sy: Int, sw: Int, sh: Int, dx: Float, dy: Float, dw: Float, dh: Float, m: String, paint: Paint?) {
        val p = paint?.json()?.let { ",$it" } ?: ""
        Host.image("draw", "{\"id\":${bitmap.handle},\"src\":${src.handle},\"s\":[$sx,$sy,$sw,$sh],\"d\":[$dx,$dy,$dw,$dh],\"m\":$m$p}", null)
    }

    fun drawBitmap(src: Bitmap, left: Float, top: Float, paint: Paint?) =
        draw(src, 0, 0, src.getWidth(), src.getHeight(), left, top, src.getWidth().toFloat(), src.getHeight().toFloat(), matrix.json(), paint)

    fun drawBitmap(src: Bitmap, srcRect: Rect?, dst: Rect, paint: Paint?) {
        val s = srcRect ?: Rect(0, 0, src.getWidth(), src.getHeight())
        draw(src, s.left, s.top, s.width(), s.height(), dst.left.toFloat(), dst.top.toFloat(), dst.width().toFloat(), dst.height().toFloat(), matrix.json(), paint)
    }

    fun drawBitmap(src: Bitmap, srcRect: Rect?, dst: RectF, paint: Paint?) {
        val s = srcRect ?: Rect(0, 0, src.getWidth(), src.getHeight())
        draw(src, s.left, s.top, s.width(), s.height(), dst.left, dst.top, dst.width(), dst.height(), matrix.json(), paint)
    }

    fun drawBitmap(src: Bitmap, m: Matrix, paint: Paint?) {
        val combined = Matrix().also { it.copyFrom(matrix); it.concat(m) }
        draw(src, 0, 0, src.getWidth(), src.getHeight(), 0f, 0f, src.getWidth().toFloat(), src.getHeight().toFloat(), combined.json(), paint)
    }

    fun drawColor(color: Int) { Host.image("fill", "{\"id\":${bitmap.handle},\"color\":$color,\"m\":[1,0,0,1,0,0]}", null) }
    fun drawColor(color: Int, mode: PorterDuff.Mode) = drawColor(color)

    fun drawRect(r: RectF, paint: Paint) = shape("rect", r.left, r.top, r.right, r.bottom, 0f, 0f, paint)
    fun drawRect(r: Rect, paint: Paint) = shape("rect", r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(), 0f, 0f, paint)
    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) = shape("rect", l, t, r, b, 0f, 0f, paint)
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, paint: Paint) = shape("rect", r.left, r.top, r.right, r.bottom, rx, ry, paint)

    private fun shape(kind: String, l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, paint: Paint) {
        Host.image("shape", "{\"id\":${bitmap.handle},\"kind\":\"$kind\",\"r\":[$l,$t,$r,$b,$rx,$ry],\"m\":${matrix.json()},${paint.json()}}", null)
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        Host.image("text", "{\"id\":${bitmap.handle},\"text\":${exia.runtime.jsonString(text)},\"x\":$x,\"y\":$y,\"m\":${matrix.json()},${paint.json()}}", null)
    }
}

object Color {
    const val BLACK = -0x1000000
    const val WHITE = -0x1
    const val TRANSPARENT = 0
    const val RED = -0x10000
    const val GRAY = -0x777778
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun red(c: Int): Int = (c shr 16) and 0xFF
    @JvmStatic fun green(c: Int): Int = (c shr 8) and 0xFF
    @JvmStatic fun blue(c: Int): Int = c and 0xFF
    @JvmStatic fun alpha(c: Int): Int = (c ushr 24) and 0xFF
    @JvmStatic fun parseColor(s: String): Int {
        if (s.startsWith("#")) {
            val v = s.substring(1).toLong(16)
            return when (s.length) { 7 -> (v or 0xFF000000).toInt(); 9 -> v.toInt(); else -> throw IllegalArgumentException("Unknown color") }
        }
        return when (s.lowercase()) { "black" -> BLACK; "white" -> WHITE; "red" -> RED; "gray", "grey" -> GRAY; "transparent" -> TRANSPARENT; else -> throw IllegalArgumentException("Unknown color") }
    }
}
