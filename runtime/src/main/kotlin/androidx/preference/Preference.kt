// Source settings: the extension builds a PreferenceScreen, the runner reads it out as JSON for the app's UI.
package androidx.preference

import android.content.Context

open class Preference(val context: Context) {
    var key: String? = null
    var title: CharSequence? = null
    var summary: CharSequence? = null
    var isEnabled: Boolean = true
    var isVisible: Boolean = true
    var defaultValue: Any? = null
    var onPreferenceChangeListener: OnPreferenceChangeListener? = null
    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }
}

open class TwoStatePreference(context: Context) : Preference(context) {
    var isChecked: Boolean = false
    var summaryOn: CharSequence? = null
    var summaryOff: CharSequence? = null
}

open class SwitchPreferenceCompat(context: Context) : TwoStatePreference(context)
open class CheckBoxPreference(context: Context) : TwoStatePreference(context)

open class DialogPreference(context: Context) : Preference(context) {
    var dialogTitle: CharSequence? = null
    var dialogMessage: CharSequence? = null
}

open class EditTextPreference(context: Context) : DialogPreference(context) {
    var text: String? = null
    fun setOnBindEditTextListener(listener: OnBindEditTextListener?) {}
    fun interface OnBindEditTextListener {
        fun onBindEditText(editText: android.widget.EditText)
    }
}

open class ListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var value: String? = null
    fun findIndexOfValue(value: String?): Int = entryValues.indexOfFirst { it.toString() == value }
}

open class MultiSelectListPreference(context: Context) : DialogPreference(context) {
    var entries: Array<CharSequence> = emptyArray()
    var entryValues: Array<CharSequence> = emptyArray()
    var values: Set<String> = emptySet()
}

open class PreferenceScreen(context: Context) : Preference(context) {
    val preferences = mutableListOf<Preference>()
    fun addPreference(preference: Preference): Boolean { preferences += preference; return true }
}
