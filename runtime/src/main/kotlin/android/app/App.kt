package android.app

import android.content.ContextWrapper

open class Application : ContextWrapper() {
    open fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {}
    open fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {}

    interface ActivityLifecycleCallbacks

    companion object {
        val shared = Application()
    }
}

open class Activity : ContextWrapper() {
    open fun getApplication(): Application = Application.shared
    open fun isFinishing(): Boolean = true
    open fun isDestroyed(): Boolean = true
    protected open fun onCreate(savedInstanceState: android.os.Bundle?) {}
    open fun finish() {}
    open fun startActivity(intent: android.content.Intent) {}
    open fun getIntent(): android.content.Intent = android.content.Intent()
}
