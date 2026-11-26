package exia.runtime

import android.app.Application
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import exia.js.Factory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * The app's side of a translated extension: one JSON request in, one JSON reply out.
 * Requests: {"method": "sources" | "popular" | "latest" | "search" | "details" | "chapters" | "pages" | "image", "source": id, ...}.
 */
object Runner {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    private val sources: List<Source> by lazy {
        when (val created = Factory.create()) {
            is SourceFactory -> created.createSources()
            is Source -> listOf(created)
            else -> emptyList()
        }
    }

    fun handle(request: String): String = try {
        val r = json.parseToJsonElement(request).jsonObject
        val result = run(r)
        buildJsonObject { put("ok", result) }.toString()
    } catch (e: Throwable) {
        exia.js.Host.log(3, "failed: " + e + "\n" + exia.js.Host.jsStack(e))
        buildJsonObject {
            put("error", e.toString())
            put("type", e::class.simpleName ?: "Error")
        }.toString()
    }

    private fun source(r: JsonObject): CatalogueSource {
        val id = r["source"]?.jsonPrimitive?.contentOrNull
        val s = if (id == null) sources.first() else sources.first { it.id.toString() == id }
        return s as CatalogueSource
    }

    private fun run(r: JsonObject): JsonElement = when (r["method"]!!.jsonPrimitive.content) {
        "sources" -> buildJsonArray {
            for (s in sources) add(buildJsonObject {
                put("id", s.id.toString())
                put("name", s.name)
                put("lang", (s as? CatalogueSource)?.lang)
                put("supportsLatest", s.supportsLatest)
                put("baseUrl", (s as? HttpSource)?.baseUrl)
            })
        }
        "popular" -> page(block { source(r).getPopularManga(r["page"]!!.jsonPrimitive.int) })
        "latest" -> page(block { source(r).getLatestUpdates(r["page"]!!.jsonPrimitive.int) })
        "search" -> {
            val s = source(r)
            page(block { s.getSearchManga(r["page"]!!.jsonPrimitive.int, r["query"]?.jsonPrimitive?.content ?: "", s.getFilterList()) })
        }
        "details", "chapters" -> {
            val details = r["method"]!!.jsonPrimitive.content == "details"
            val update = block { source(r).getMangaUpdate(manga(r["manga"]!!.jsonObject), emptyList(), details, !details) }
            if (details) manga(update.manga) else JsonArray(update.chapters.map(::chapter))
        }
        "pages" -> JsonArray(block { source(r).getPageList(chapter(r["chapter"]!!.jsonObject)) }.map { p ->
            buildJsonObject { put("index", p.index); put("url", p.url); put("imageUrl", p.imageUrl) }
        })
        "imageData" -> {
            // The page image through the extension's client, so its interceptors (descrambling and the like) run.
            val s = source(r) as HttpSource
            val p = r["page"]!!.jsonObject
            val page = Page(p["index"]!!.jsonPrimitive.int, p["url"]?.jsonPrimitive?.content ?: "", p["imageUrl"]?.jsonPrimitive?.contentOrNull)
            if (page.imageUrl == null) page.imageUrl = block { s.getImageUrl(page) }
            val response = s.client.newCall(s.exiaImageRequest(page)).execute()
            val bytes = response.body.bytes()
            buildJsonObject {
                put("status", response.code)
                put("type", response.header("Content-Type"))
                put("bytes", bytes.size)
                // The bytes go to the app directly (no base64): the reply names them.
                put("data", exia.js.Host.deliver(bytes))
            }
        }
        "headers" -> {
            // What the source sends with every request (User-Agent, Referer), for covers the app loads itself.
            val s = source(r) as HttpSource
            buildJsonArray { for ((k, v) in s.headers) add(buildJsonArray { add(JsonPrimitive(k)); add(JsonPrimitive(v)) }) }
        }
        "image" -> {
            val s = source(r) as HttpSource
            val p = r["page"]!!.jsonObject
            val page = Page(p["index"]!!.jsonPrimitive.int, p["url"]?.jsonPrimitive?.content ?: "", p["imageUrl"]?.jsonPrimitive?.contentOrNull)
            if (page.imageUrl == null) page.imageUrl = block { s.getImageUrl(page) }
            val req = s.exiaImageRequest(page)
            buildJsonObject {
                put("url", req.url.toString())
                put("method", req.method)
                put("headers", buildJsonArray { for ((k, v) in req.headers) add(buildJsonArray { add(JsonPrimitive(k)); add(JsonPrimitive(v)) }) })
            }
        }
        "filters" -> JsonArray(source(r).getFilterList().map { f -> JsonPrimitive("${f::class.simpleName}:${f.name}") })
        "preferences" -> {
            val saved = prefs(r)
            JsonArray(screen(r).preferences.filter { it.isVisible && it.key != null }.map { preference(it, saved) })
        }
        "setPreference" -> {
            // As Mihon's settings page does: the extension's listener may refuse the value, else it's saved.
            val key = r["key"]!!.jsonPrimitive.content
            val p = screen(r).preferences.first { it.key == key }
            val value: Any? = when (val v = r["value"]) {
                is JsonArray -> v.map { it.jsonPrimitive.content }.toSet()
                is JsonPrimitive -> if (p is TwoStatePreference) v.boolean else v.contentOrNull
                else -> null
            }
            val accepted = p.onPreferenceChangeListener?.onPreferenceChange(p, value) ?: true
            if (accepted) {
                val editor = prefs(r).edit()
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                    null -> editor.remove(key)
                    else -> editor.putString(key, value.toString())
                }
                editor.apply()
            }
            JsonPrimitive(accepted)
        }
        else -> throw IllegalArgumentException("Unknown method")
    }

    /** The source's settings, built by the extension as Mihon's settings page would. */
    private fun screen(r: JsonObject): PreferenceScreen {
        val screen = PreferenceScreen(Application.shared)
        (source(r) as? ConfigurableSource)?.setupPreferenceScreen(screen)
        return screen
    }

    /** Where Mihon keeps a source's settings ("source_<id>"), which the extension reads them from too. */
    private fun prefs(r: JsonObject) = Application.shared.getSharedPreferences("source_${source(r).id}", 0)

    /** A setting as the app shows it, with its saved value (Android's settings page reads it the same way). */
    private fun preference(p: Preference, saved: android.content.SharedPreferences): JsonObject = buildJsonObject {
        val key = p.key!!
        put("key", key)
        put("title", p.title?.toString())
        put("summary", p.summary?.toString())
        put("enabled", p.isEnabled)
        when (p) {
            is TwoStatePreference -> {
                put("type", "toggle")
                put("value", saved.getBoolean(key, p.defaultValue as? Boolean ?: p.isChecked))
            }
            is ListPreference -> {
                put("type", "list")
                put("entries", JsonArray(p.entries.map { JsonPrimitive(it.toString()) }))
                put("values", JsonArray(p.entryValues.map { JsonPrimitive(it.toString()) }))
                put("value", saved.getString(key, null) ?: p.value ?: p.defaultValue?.toString())
            }
            is MultiSelectListPreference -> {
                put("type", "multi")
                put("entries", JsonArray(p.entries.map { JsonPrimitive(it.toString()) }))
                put("values", JsonArray(p.entryValues.map { JsonPrimitive(it.toString()) }))
                @Suppress("UNCHECKED_CAST")
                val chosen = saved.getStringSet(key, null) ?: p.values.ifEmpty { (p.defaultValue as? Set<String>).orEmpty() }
                put("value", JsonArray(chosen.map { JsonPrimitive(it) }))
            }
            is EditTextPreference -> {
                put("type", "text")
                put("value", saved.getString(key, null) ?: p.text ?: p.defaultValue?.toString())
            }
            else -> put("type", "other")
        }
    }

    /** Runs a suspend function to its end on this (green) thread: network calls block it, nothing else waits. */
    private fun <T> block(fn: suspend () -> T): T {
        var outcome: Result<T>? = null
        val lock = Object()
        fn.startCoroutine(object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                synchronized(lock) {
                    outcome = result
                    lock.notifyAll()
                }
            }
        })
        synchronized(lock) {
            while (outcome == null) lock.wait()
        }
        return outcome!!.getOrThrow()
    }

    private fun page(p: MangasPage): JsonElement = buildJsonObject {
        put("mangas", JsonArray(p.mangas.map(::manga)))
        put("hasNextPage", p.hasNextPage)
    }

    private fun manga(m: SManga): JsonObject = buildJsonObject {
        put("url", runCatching { m.url }.getOrNull()); put("title", runCatching { m.title }.getOrNull()); put("thumbnail_url", m.thumbnail_url); put("artist", m.artist)
        put("author", m.author); put("status", m.status); put("description", m.description); put("genre", m.genre)
        put("memo", m.memo)
    }

    private fun manga(o: JsonObject): SManga = SManga.create().apply {
        url = o["url"]!!.jsonPrimitive.content
        title = o["title"]?.jsonPrimitive?.contentOrNull ?: ""
        thumbnail_url = o["thumbnail_url"]?.jsonPrimitive?.contentOrNull
        (o["memo"] as? JsonObject)?.let { memo = it }
    }

    private fun chapter(c: SChapter): JsonObject = buildJsonObject {
        put("url", c.url); put("name", c.name); put("chapter_number", c.chapter_number); put("scanlator", c.scanlator)
        put("date_upload", c.date_upload); put("memo", c.memo)
    }

    private fun chapter(o: JsonObject): SChapter = SChapter.create().apply {
        url = o["url"]!!.jsonPrimitive.content
        name = o["name"]?.jsonPrimitive?.contentOrNull ?: ""
        (o["memo"] as? JsonObject)?.let { memo = it }
    }
}
