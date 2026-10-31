package app.orchard.tv.channels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.BaseColumns
import org.json.JSONObject

/** Read-only adapter. Never forwards arbitrary provider URIs or exposes raw provider metadata. */
object YaosChannels {
    data class Group(val id: String, val name: String, val order: Int)
    data class Channel(val id: Long, val input: String, val name: String, val number: String,
                       val contentId: String, val groups: List<Group>, val artwork: String? = null) {
        val key: String get() = "$input|${contentId.ifBlank { id.toString() }}"
    }
    fun categories(raw: String): List<Group> = runCatching {
        val json = JSONObject(raw)
        val a = json.optJSONObject("custom")?.optJSONArray("categories") ?: json.optJSONArray("categories")
        if (a == null) emptyList() else (0 until minOf(a.length(), 100)).mapNotNull { i ->
            val g = a.optJSONObject(i) ?: return@mapNotNull null
            val id = g.optString("id").take(160)
            val name = g.optString("name").take(120)
            if (id.isBlank() || name.isBlank()) null else Group(id, name, g.optInt("number", i))
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    data class Program(val title: String, val description: String, val start: Long, val end: Long)

    fun artwork(raw: String): String? = runCatching {
        val custom = JSONObject(raw).optJSONObject("custom") ?: return@runCatching null
        listOf("logo_with_bg_url", "channel_thumbnail", "icon_url").map { custom.optString(it) }
            .firstOrNull { value ->
                val uri = Uri.parse(value)
                value.length <= 4096 && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
            }
    }.getOrNull()

    /** EPG is optional: never let a missing or inaccessible program table hide channels. */
    fun programs(context: Context, signal: CancellationSignal, now: Long = System.currentTimeMillis()): Map<Long, Program> {
        val uri = Uri.parse("content://com.yandex.tv.channels_redirect_proxy/program")
            .buildUpon().appendQueryParameter("original_authority", "android.media.tv")
            .appendQueryParameter("start_time", now.toString()).appendQueryParameter("end_time", now.toString()).build()
        val result = mutableMapOf<Long, Program>()
        context.contentResolver.query(uri, arrayOf("channel_id", "title", "short_description", "start_time_utc_millis", "end_time_utc_millis"), null, null, null, signal)?.use { c ->
            var scanned = 0
            while (scanned++ < 10000 && c.moveToNext()) {
                signal.throwIfCanceled()
                val start = c.getLong(3); val end = c.getLong(4)
                val title = c.getString(1).orEmpty().take(250)
                if (start <= now && end > now && title.isNotBlank()) {
                    val id = c.getLong(0)
                    if (start >= (result[id]?.start ?: Long.MIN_VALUE))
                        result[id] = Program(title, c.getString(2).orEmpty().take(600), start, end)
                }
            }
        }
        return result
    }

    fun load(context: Context, signal: CancellationSignal): List<Channel> {
        val uri = Uri.parse("content://com.yandex.tv.channels_redirect_proxy/channel")
            .buildUpon().appendQueryParameter("original_authority", "android.media.tv").build()
        val columns = arrayOf(BaseColumns._ID, "input_id", "display_name", "display_number", "internal_provider_data")
        val rows = mutableListOf<Channel>()
        context.contentResolver.query(uri, columns, null, null, null, signal)?.use { c ->
            while (rows.size < 2000 && c.moveToNext()) {
                signal.throwIfCanceled()
                val input = c.getString(1).orEmpty()
                if (!input.startsWith("com.yandex.tv.input.efir/")) continue
                val name = c.getString(2).orEmpty().take(160)
                if (name.isBlank()) continue
                val raw = if (c.isNull(4)) "" else if (c.getType(4) == android.database.Cursor.FIELD_TYPE_BLOB)
                    c.getBlob(4)?.takeIf { it.size <= 262144 }?.toString(Charsets.UTF_8).orEmpty()
                else c.getString(4).orEmpty().take(262144)
                val contentId = runCatching { JSONObject(raw).optString("content_id").take(160) }.getOrDefault("")
                rows += Channel(c.getLong(0), input, name, c.getString(3).orEmpty().take(20), contentId, categories(raw), artwork(raw))
            }
        } ?: error("Catalog provider returned no cursor")
        return rows.distinctBy { it.key }.sortedWith(compareBy({ it.number.toIntOrNull()?.takeIf { number -> number > 0 } ?: Int.MAX_VALUE }, { it.name }))
    }

    fun playback(channel: Channel, group: String?): Intent = Intent("yandex.intent.action.TV_CHANNEL")
        .setClassName("com.yandex.tv.live", "com.yandex.tv.live.playback.ChannelPlaybackActivity")
        .putExtra("input_id", channel.input)
        .putExtra("channel_uri", Uri.parse("content://android.media.tv/channel/${channel.id}"))
        .apply { if (group != null) putExtra("current_category_id", group) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
