package app.robfx.update

import app.robfx.data.source.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val notes: String,
)

/** Reads GitHub's "latest release" JSON and decides whether it is newer than what is installed. Pure, so it is unit-tested. */
object Releases {
    fun parse(body: String): UpdateInfo? {
        val o = json.parseToJsonElement(body).jsonObject
        if (o["draft"]?.jsonPrimitive?.boolean == true || o["prerelease"]?.jsonPrimitive?.boolean == true) return null
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        val assets = (o["assets"] as? JsonArray ?: return null).map { it.jsonObject }
        val apk = assets.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk", ignoreCase = true) == true } ?: return null
        val url = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        // Only ever fetch from GitHub over https: the update channel is the repo's releases and nothing else.
        if (!url.startsWith("https://github.com/")) return null
        return UpdateInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            apkUrl = url,
            sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: -1L,
            sha256 = apk["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?.lowercase()?.takeIf { it.length == 64 },
            notes = o["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
        )
    }

    /** Numeric dotted comparison: "1.0.10" > "1.0.9", "1.1" == "1.1.0", pre-release suffixes after '-' are ignored. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
