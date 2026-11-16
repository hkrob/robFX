package app.robfx.about

import android.content.Context

data class Release(val version: String, val items: List<String>)

/**
 * Reads changelog/CHANGELOG.md, which is bundled into the app as an asset. The same file feeds the GitHub
 * release notes, so what the About tab says is new is exactly what the release said.
 */
object Changelog {
    const val ASSET = "CHANGELOG.md"

    fun parse(markdown: String): List<Release> {
        val out = ArrayList<Release>()
        var version: String? = null
        var items = ArrayList<String>()
        fun flush() {
            version?.let { if (items.isNotEmpty()) out += Release(it, items) }
            items = ArrayList()
        }
        for (raw in markdown.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("## ") -> { flush(); version = line.removePrefix("## ").trim().removePrefix("v") }
                version != null && (line.startsWith("- ") || line.startsWith("* ")) -> items += line.drop(2).trim()
                // A wrapped bullet continues on the next line.
                version != null && line.isNotEmpty() && items.isNotEmpty() && !line.startsWith("#") -> items[items.lastIndex] = items.last() + " " + line
            }
        }
        flush()
        return out
    }

    fun load(context: Context): List<Release> =
        runCatching { context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())
}
