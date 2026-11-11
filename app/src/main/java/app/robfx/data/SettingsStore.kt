package app.robfx.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.robfx.data.source.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

data class Settings(
    val from: String,
    val to: String,
    val expr: String = "1",
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val haptics: Boolean = true,
    val range: Range = Range.M1,
    val recents: List<String> = emptyList(),
    val autoUpdate: Boolean = true,
    val sort: app.robfx.WatchSort = app.robfx.WatchSort.Custom,
    val sortDescending: Boolean = false,
)

@Serializable
data class Alert(
    val id: Long,
    val from: String,
    val to: String,
    val target: Double,
    val above: Boolean,
    val active: Boolean = true,
)

data class WatchPair(val from: String, val to: String) {
    val key get() = "$from/$to"
}

/** Home-screen defaults: the phone's own pair first, then the usual suspects, never the same pair twice. */
fun defaultWatchlist(local: Pair<String, String> = Currencies.localPair()): List<WatchPair> =
    (listOf(local) + listOf("USD" to "EUR", "USD" to "JPY", "EUR" to "GBP", "USD" to "CNY", "USD" to "HKD", "AUD" to "USD"))
        .filter { it.first != it.second }
        .distinct()
        .take(6)
        .map { WatchPair(it.first, it.second) }

private val Context.dataStore by preferencesDataStore("robfx")

class SettingsStore(private val context: Context) {
    private object K {
        val from = stringPreferencesKey("from")
        val to = stringPreferencesKey("to")
        val expr = stringPreferencesKey("expr")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic")
        val haptics = booleanPreferencesKey("haptics")
        val range = stringPreferencesKey("range")
        val recents = stringPreferencesKey("recents")
        val alerts = stringPreferencesKey("alerts")
        val watch = stringPreferencesKey("watch")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val sort = stringPreferencesKey("watch_sort")
        val sortDesc = booleanPreferencesKey("watch_sort_desc")
        val lastUpdateCheck = androidx.datastore.preferences.core.longPreferencesKey("update_checked_at")
        val lastNotified = stringPreferencesKey("update_notified")
    }

    private val defaults = Currencies.localPair()

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            from = p[K.from] ?: defaults.first,
            to = p[K.to] ?: defaults.second,
            expr = p[K.expr] ?: "1",
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            dynamicColor = p[K.dynamic] ?: false,
            haptics = p[K.haptics] ?: true,
            range = p[K.range]?.let { runCatching { Range.valueOf(it) }.getOrNull() } ?: Range.M1,
            recents = p[K.recents]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            autoUpdate = p[K.autoUpdate] ?: true,
            sort = p[K.sort]?.let { runCatching { app.robfx.WatchSort.valueOf(it) }.getOrNull() } ?: app.robfx.WatchSort.Custom,
            sortDescending = p[K.sortDesc] ?: false,
        )
    }

    private fun parseWatch(raw: String?): List<WatchPair>? = raw?.split(',')?.mapNotNull {
        val (f, t) = it.split('/').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
        if (f in Currencies.codes && t in Currencies.codes && f != t) WatchPair(f, t) else null
    }

    /** Null until the user first edits it, so the defaults can follow the locale rather than being frozen at install. */
    val watchlist: Flow<List<WatchPair>> = context.dataStore.data.map { p -> parseWatch(p[K.watch]) ?: defaultWatchlist() }

    suspend fun updateWatchlist(transform: (List<WatchPair>) -> List<WatchPair>) = context.dataStore.edit { p ->
        val cur = parseWatch(p[K.watch]) ?: defaultWatchlist()
        p[K.watch] = transform(cur).distinct().joinToString(",") { it.key }
    }

    val alerts: Flow<List<Alert>> = context.dataStore.data.map { p ->
        p[K.alerts]?.let { runCatching { json.decodeFromString(ListSerializer(Alert.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    suspend fun setPair(from: String, to: String) = context.dataStore.edit {
        it[K.from] = from
        it[K.to] = to
        val recents = (listOf(from, to) + (it[K.recents]?.split(',') ?: emptyList())).filter { c -> c.isNotBlank() }.distinct().take(6)
        it[K.recents] = recents.joinToString(",")
    }

    suspend fun setExpr(v: String) = context.dataStore.edit { it[K.expr] = v }
    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setDynamic(v: Boolean) = context.dataStore.edit { it[K.dynamic] = v }
    suspend fun setSort(sort: app.robfx.WatchSort, descending: Boolean) = context.dataStore.edit { it[K.sort] = sort.name; it[K.sortDesc] = descending }
    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[K.autoUpdate] = v }
    suspend fun lastUpdateCheck(): Long = context.dataStore.data.map { it[K.lastUpdateCheck] ?: 0L }.first()
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
    suspend fun lastNotifiedVersion(): String? = context.dataStore.data.map { it[K.lastNotified] }.first()
    suspend fun setLastNotifiedVersion(v: String) = context.dataStore.edit { it[K.lastNotified] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setRange(v: Range) = context.dataStore.edit { it[K.range] = v.name }

    suspend fun updateAlerts(transform: (List<Alert>) -> List<Alert>) = context.dataStore.edit { p ->
        val cur = p[K.alerts]?.let { runCatching { json.decodeFromString(ListSerializer(Alert.serializer()), it) }.getOrNull() } ?: emptyList()
        p[K.alerts] = json.encodeToString(ListSerializer(Alert.serializer()), transform(cur))
    }
}
