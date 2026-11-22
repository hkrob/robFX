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
    // Decimal places in the app (widgets have their own). Auto keeps the adaptive precision.
    val rateDecimals: Int = app.robfx.calc.Format.AUTO,
    val amountDecimals: Int = app.robfx.calc.Format.AUTO,
    val unitDecimals: Int = app.robfx.calc.Format.AUTO,
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
    /** When it last fired, and at what rate; cleared when re-armed. Null for one paused by hand. */
    val triggeredAt: Long? = null,
    val triggeredRate: Double? = null,
    /** "target" (crosses [target]) or "move" (moves [movePct]% either way from [base]). */
    val kind: String = TARGET,
    val movePct: Double? = null,
    val base: Double? = null,
    /** Keeps watching after it fires, instead of switching itself off. */
    val repeat: Boolean = false,
    /** A repeating target alert that fired and waits for the rate to come back before it can fire again. */
    val waiting: Boolean = false,
) {
    val isMove get() = kind == MOVE

    companion object {
        const val TARGET = "target"
        const val MOVE = "move"
    }
}

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
        val rateDecimals = androidx.datastore.preferences.core.intPreferencesKey("decimals_rate")
        val amountDecimals = androidx.datastore.preferences.core.intPreferencesKey("decimals_amount")
        val unitDecimals = androidx.datastore.preferences.core.intPreferencesKey("decimals_unit")
        val range = stringPreferencesKey("range")
        val recents = stringPreferencesKey("recents")
        val alerts = stringPreferencesKey("alerts")
        val watch = stringPreferencesKey("watch")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val sort = stringPreferencesKey("watch_sort")
        val sortDesc = booleanPreferencesKey("watch_sort_desc")
        val unitComparison = stringPreferencesKey("unit_comparison")
        val unitRecents = stringPreferencesKey("unit_recents")
        val recentLabels = stringPreferencesKey("recent_labels")
        val recentTags = stringPreferencesKey("recent_tags")
        val lastUpdateCheck = androidx.datastore.preferences.core.longPreferencesKey("update_checked_at")
        val lastNotified = stringPreferencesKey("update_notified")
        val multi = stringPreferencesKey("multi")
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
            rateDecimals = p[K.rateDecimals] ?: app.robfx.calc.Format.AUTO,
            amountDecimals = p[K.amountDecimals] ?: app.robfx.calc.Format.AUTO,
            unitDecimals = p[K.unitDecimals] ?: app.robfx.calc.Format.AUTO,
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

    /** Currencies shown in "In other currencies". Until edited: the Markets currencies plus a few majors. */
    val multi: Flow<List<String>> = context.dataStore.data.map { p -> parseMulti(p[K.multi]) ?: defaultMulti(parseWatch(p[K.watch]) ?: defaultWatchlist()) }

    private fun parseMulti(raw: String?): List<String>? = raw?.split(',')?.filter { it in Currencies.codes }

    private fun defaultMulti(watch: List<WatchPair>): List<String> =
        (watch.flatMap { listOf(it.from, it.to) } + listOf("USD", "EUR", "GBP", "JPY", "CNY", "AUD", "HKD")).distinct().take(8)

    suspend fun updateMulti(transform: (List<String>) -> List<String>) = context.dataStore.edit { p ->
        val cur = parseMulti(p[K.multi]) ?: defaultMulti(parseWatch(p[K.watch]) ?: defaultWatchlist())
        p[K.multi] = transform(cur).distinct().joinToString(",")
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
    suspend fun unitComparison(): app.robfx.unitprice.Comparison? = context.dataStore.data.first()[K.unitComparison]?.let {
        runCatching { json.decodeFromString(app.robfx.unitprice.Comparison.serializer(), it) }.getOrNull()
    }

    suspend fun setUnitComparison(c: app.robfx.unitprice.Comparison) = context.dataStore.edit {
        it[K.unitComparison] = json.encodeToString(app.robfx.unitprice.Comparison.serializer(), c)
    }

    val unitRecents: Flow<List<app.robfx.unitprice.SavedComparison>> = context.dataStore.data.map { p ->
        p[K.unitRecents]?.let { runCatching { json.decodeFromString(app.robfx.unitprice.SavedFile.serializer(), it).items }.getOrNull() } ?: emptyList()
    }

    suspend fun updateUnitRecents(transform: (List<app.robfx.unitprice.SavedComparison>) -> List<app.robfx.unitprice.SavedComparison>) = context.dataStore.edit { p ->
        val cur = p[K.unitRecents]?.let { runCatching { json.decodeFromString(app.robfx.unitprice.SavedFile.serializer(), it).items }.getOrNull() } ?: emptyList()
        p[K.unitRecents] = json.encodeToString(app.robfx.unitprice.SavedFile.serializer(), app.robfx.unitprice.SavedFile(items = transform(cur)))
    }

    // Names you typed, newest first, kept as soon as you save them: not waiting for a comparison to finish and reach history.
    private fun names(raw: String?): List<String> = raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    val recentLabels: Flow<List<String>> = context.dataStore.data.map { names(it[K.recentLabels]) }
    val recentTags: Flow<List<String>> = context.dataStore.data.map { names(it[K.recentTags]) }

    suspend fun addRecentLabel(v: String) = context.dataStore.edit {
        it[K.recentLabels] = app.robfx.unitprice.Recents.mru(names(it[K.recentLabels]), v).joinToString("\n")
    }

    suspend fun addRecentTag(v: String) = context.dataStore.edit {
        it[K.recentTags] = app.robfx.unitprice.Recents.mru(names(it[K.recentTags]), v).joinToString("\n")
    }

    // One entry per widget on the home screen, keyed by the id Android gives that instance.
    private fun widgetKey(id: Int) = stringPreferencesKey("widget_$id")

    suspend fun widgetConfig(id: Int): app.robfx.widget.WidgetConfig? = context.dataStore.data.first()[widgetKey(id)]?.let {
        runCatching { json.decodeFromString(app.robfx.widget.WidgetConfig.serializer(), it) }.getOrNull()
    }

    suspend fun setWidgetConfig(id: Int, c: app.robfx.widget.WidgetConfig) = context.dataStore.edit {
        it[widgetKey(id)] = json.encodeToString(app.robfx.widget.WidgetConfig.serializer(), c)
    }

    suspend fun removeWidgetConfig(id: Int) = context.dataStore.edit { it.remove(widgetKey(id)) }

    /** Wipes every saved preference. Tests use it to start from a clean slate; DataStore is shared by the whole process. */
    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[K.autoUpdate] = v }
    suspend fun lastUpdateCheck(): Long = context.dataStore.data.map { it[K.lastUpdateCheck] ?: 0L }.first()
    suspend fun setLastUpdateCheck(v: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = v }
    suspend fun lastNotifiedVersion(): String? = context.dataStore.data.map { it[K.lastNotified] }.first()
    suspend fun setLastNotifiedVersion(v: String) = context.dataStore.edit { it[K.lastNotified] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setRateDecimals(v: Int) = context.dataStore.edit { it[K.rateDecimals] = v }
    suspend fun setAmountDecimals(v: Int) = context.dataStore.edit { it[K.amountDecimals] = v }
    suspend fun setUnitDecimals(v: Int) = context.dataStore.edit { it[K.unitDecimals] = v }
    suspend fun setRange(v: Range) = context.dataStore.edit { it[K.range] = v.name }

    suspend fun updateAlerts(transform: (List<Alert>) -> List<Alert>) = context.dataStore.edit { p ->
        val cur = p[K.alerts]?.let { runCatching { json.decodeFromString(ListSerializer(Alert.serializer()), it) }.getOrNull() } ?: emptyList()
        p[K.alerts] = json.encodeToString(ListSerializer(Alert.serializer()), transform(cur))
    }
}
