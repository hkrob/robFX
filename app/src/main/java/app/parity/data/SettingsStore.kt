package app.parity.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.parity.data.source.json
import kotlinx.coroutines.flow.Flow
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

private val Context.dataStore by preferencesDataStore("parity")

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
        )
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
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setRange(v: Range) = context.dataStore.edit { it[K.range] = v.name }

    suspend fun updateAlerts(transform: (List<Alert>) -> List<Alert>) = context.dataStore.edit { p ->
        val cur = p[K.alerts]?.let { runCatching { json.decodeFromString(ListSerializer(Alert.serializer()), it) }.getOrNull() } ?: emptyList()
        p[K.alerts] = json.encodeToString(ListSerializer(Alert.serializer()), transform(cur))
    }
}
