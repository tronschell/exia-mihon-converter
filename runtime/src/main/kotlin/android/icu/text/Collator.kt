package android.icu.text

import java.util.Locale

/** ICU collation on the platform's localeCompare (numeric option supported). */
open class Collator protected constructor(protected val locale: Locale) : Comparator<Any?> {
    protected var numeric = false
    protected var strengthLevel = 2
    override fun compare(a: Any?, b: Any?): Int = exia.js.Text.localeCompare(a.toString(), b.toString(), locale.toLanguageTag(), numeric, strengthLevel)
    open fun freeze(): Collator = this
    open fun setStrength(s: Int) { strengthLevel = s }
    companion object {
        const val PRIMARY = 0
        const val SECONDARY = 1
        const val TERTIARY = 2
        @JvmStatic fun getInstance(locale: Locale): Collator = RuleBasedCollator(locale)
        @JvmStatic fun getInstance(): Collator = RuleBasedCollator(Locale.getDefault())
    }
}

open class RuleBasedCollator(locale: Locale) : Collator(locale) {
    open fun setNumericCollation(on: Boolean) { numeric = on }
}
