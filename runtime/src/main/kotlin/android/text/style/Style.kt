package android.text.style

import android.text.TextPaint

abstract class CharacterStyle { open fun updateDrawState(tp: TextPaint) {} }
abstract class MetricAffectingSpan : CharacterStyle() { open fun updateMeasureState(textPaint: TextPaint) {} }
