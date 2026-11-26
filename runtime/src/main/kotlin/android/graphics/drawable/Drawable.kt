package android.graphics.drawable

abstract class Drawable
open class ColorDrawable(@JvmField val color: Int) : Drawable() { constructor() : this(0) }
