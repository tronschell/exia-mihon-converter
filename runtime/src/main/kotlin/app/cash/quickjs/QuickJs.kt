package app.cash.quickjs

import exia.js.Host
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** QuickJS for extensions that run a site's script: the extension already runs in JavaScript, so it evaluates there. */
class QuickJs private constructor() : java.io.Closeable {
    fun evaluate(script: String): Any? = evaluate(script, "?")

    fun evaluate(script: String, fileName: String): Any? {
        val r = Host.evaluate(script) ?: return null
        val e = Json.parseToJsonElement(r)
        if (e is JsonObject && e["error"] != null) throw QuickJsException((e["error"] as JsonPrimitive).content)
        return convert((e as JsonObject)["value"] ?: JsonNull)
    }

    private fun convert(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.intOrNull ?: e.doubleOrNull
        is JsonArray -> e.map { convert(it) }.toTypedArray()
        is JsonObject -> null
    }

    override fun close() {}

    companion object {
        @JvmStatic fun create(): QuickJs = QuickJs()
    }
}

class QuickJsException(message: String) : RuntimeException(message)
