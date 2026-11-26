// Exia: coroutine dispatchers on TeaVM green threads instead of thread pools.
@file:OptIn(InternalCoroutinesApi::class)

package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext

object Dispatchers {
    @JvmStatic val Default: CoroutineDispatcher = GreenDispatcher
    @JvmStatic val Main: MainCoroutineDispatcher = GreenMainDispatcher
    @JvmStatic val Unconfined: CoroutineDispatcher = GreenUnconfined
    @JvmStatic val IO: CoroutineDispatcher = GreenDispatcher
    fun shutdown() {}
}

private object GreenDispatcher : CoroutineDispatcher(), Delay {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Thread(block).start()
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        Thread {
            exia.js.Host.sleep(timeMillis.toDouble())
            with(continuation) { resumeUndispatched(Unit) }
        }.start()
    }

    override fun toString(): String = "Dispatchers.Green"
}

private object GreenMainDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher get() = this
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Thread(block).start()
    }
}

private object GreenUnconfined : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}
