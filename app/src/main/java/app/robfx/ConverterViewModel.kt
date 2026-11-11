package app.robfx

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.robfx.calc.Calc
import app.robfx.data.Alert
import app.robfx.data.HistoryPoint
import app.robfx.data.PairRate
import app.robfx.data.Range
import app.robfx.data.Settings
import app.robfx.data.SourceStatus
import app.robfx.data.ThemeMode
import app.robfx.data.WatchPair
import app.robfx.update.UpdateInfo
import app.robfx.update.UpdateState
import app.robfx.data.defaultWatchlist
import app.robfx.data.Currencies
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class HistoryState(
    val points: List<HistoryPoint> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
)

enum class Sheet { None, FromPicker, ToPicker, Alerts, Settings, AddFrom, AddTo }

enum class Screen { Home, Converter }

/** One line of the watchlist. [points] is the past week, used for the sparkline and the weekly change. */
data class WatchRow(val pair: WatchPair, val rate: PairRate?, val points: List<HistoryPoint>?) {
    val change: Double?
        get() = points?.takeIf { it.size >= 2 && it.first().value != 0.0 }?.let { it.last().value / it.first().value - 1 }
}

class ConverterViewModel(app: Application, initial: Settings) : AndroidViewModel(app) {
    private val c = app.container

    val settings: StateFlow<Settings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, initial)
    val alerts: StateFlow<List<Alert>> = c.settings.alerts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sources: StateFlow<List<SourceStatus>> = c.rates.status
    val refreshing: StateFlow<Boolean> = c.rates.refreshing

    private val _expr = MutableStateFlow(initial.expr)
    val expr: StateFlow<String> = _expr

    private val _keypadOpen = MutableStateFlow(true)
    val keypadOpen: StateFlow<Boolean> = _keypadOpen

    private val _sheet = MutableStateFlow(Sheet.None)
    val sheet: StateFlow<Sheet> = _sheet

    val updateState: StateFlow<UpdateState> = c.updater.state
    val currentVersion: String = c.updater.currentVersion

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing

    val watchlist: StateFlow<List<WatchPair>> = c.settings.watchlist.stateIn(viewModelScope, SharingStarted.Eagerly, defaultWatchlist())
    private val sparks = MutableStateFlow<Map<String, List<HistoryPoint>>>(emptyMap())

    val rows: StateFlow<List<WatchRow>> = combine(watchlist, sparks, c.rates.snapshot) { list, sp, _ ->
        list.map { WatchRow(it, c.rates.pair(it.from, it.to), sp[it.key]) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pendingFrom: String? = null

    private val _history = MutableStateFlow(HistoryState())
    val history: StateFlow<HistoryState> = _history

    /** The live consensus rate for the pair on screen. Null only before any source has ever answered. */
    val pair: StateFlow<PairRate?> = combine(c.rates.snapshot, settings.map { it.from to it.to }.distinctUntilChanged()) { _, (f, t) ->
        c.rates.pair(f, t)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, c.rates.pair(initial.from, initial.to))

    val amount: StateFlow<BigDecimal?> = _expr.map { Calc.evaluate(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Calc.evaluate(initial.expr))

    val converted: StateFlow<Double?> = combine(amount, pair) { a, p -> if (a == null || p == null) null else a.toDouble() * p.rate }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        refreshIfStale()
        // At most every three hours on launch; the background worker covers the rest.
        viewModelScope.launch { if (System.currentTimeMillis() - c.settings.lastUpdateCheck() > 3 * 3_600_000L) c.updater.check() }
        viewModelScope.launch { watchlist.collect { loadSparks(it) } }
        viewModelScope.launch {
            combine(settings.map { it.from to it.to }.distinctUntilChanged(), settings.map { it.range }.distinctUntilChanged()) { p, r -> p to r }
                .collect { (p, r) -> loadHistory(p.first, p.second, r) }
        }
    }

    private var sparkJob: kotlinx.coroutines.Job? = null

    private fun loadSparks(list: List<WatchPair>) {
        sparkJob?.cancel()
        sparkJob = viewModelScope.launch {
            // Each pair lands as soon as it's ready; a slow or unsupported pair never holds up the others.
            list.forEach { w ->
                launch {
                    c.history.load(w.from, w.to, Range.W1).onSuccess { pts -> sparks.update { it + (w.key to pts) } }
                }
            }
        }
    }

    private var historyJob: kotlinx.coroutines.Job? = null

    private fun loadHistory(from: String, to: String, range: Range, force: Boolean = false) {
        historyJob?.cancel()
        _history.update { it.copy(loading = true, error = false) }
        historyJob = viewModelScope.launch {
            c.history.load(from, to, range, force).fold(
                { pts -> _history.value = HistoryState(pts, loading = false) },
                { _history.value = HistoryState(emptyList(), loading = false, error = true) },
            )
        }
    }

    fun retryHistory() = settings.value.let { loadHistory(it.from, it.to, it.range, force = true) }

    fun refreshIfStale() {
        if (c.rates.isStale()) refresh()
    }

    fun refresh() {
        viewModelScope.launch { c.rates.refresh() }
    }

    // True while the entry is the default "1" that Clear restores: the next digit replaces it instead of extending it.
    private var onDefault = false

    fun key(k: String) {
        val next = if (onDefault && k != "C" && k != "<" && k != "=") Calc.keyOnDefault(k) else Calc.key(_expr.value, k)
        onDefault = k == "C"
        _expr.value = next
        viewModelScope.launch { c.settings.setExpr(next) }
    }

    fun clear() = key("C")

    fun setRange(r: Range) {
        viewModelScope.launch { c.settings.setRange(r) }
    }

    fun swap() {
        val s = settings.value
        viewModelScope.launch { c.settings.setPair(s.to, s.from) }
    }

    fun pick(code: String, isFrom: Boolean) {
        val s = settings.value
        viewModelScope.launch {
            when {
                isFrom && code == s.to -> c.settings.setPair(s.to, s.from)
                isFrom -> c.settings.setPair(code, s.to)
                code == s.from -> c.settings.setPair(s.to, s.from)
                else -> c.settings.setPair(s.from, code)
            }
        }
        _sheet.value = Sheet.None
    }

    fun openPair(w: WatchPair) {
        viewModelScope.launch { c.settings.setPair(w.from, w.to) }
        _screen.value = Screen.Converter
    }

    fun goHome() { _screen.value = Screen.Home }
    fun toggleEditing() = _editing.update { !it }
    fun setEditing(on: Boolean) { _editing.value = on }

    fun isWatched(f: String, t: String) = watchlist.value.any { it.from == f && it.to == t }

    fun toggleWatch() {
        val s = settings.value
        val w = WatchPair(s.from, s.to)
        viewModelScope.launch { c.settings.updateWatchlist { if (w in it) it - w else it + w } }
    }

    fun removeWatch(w: WatchPair) {
        viewModelScope.launch { c.settings.updateWatchlist { it - w } }
        sparks.update { it - w.key }
    }

    fun startAdd() { pendingFrom = null; _sheet.value = Sheet.AddFrom }

    /** Two taps to add a pair: base, then quote. Choosing the same currency twice just re-asks for the quote. */
    fun pickForWatch(code: String) {
        val from = pendingFrom
        if (from == null) {
            pendingFrom = code
            _sheet.value = Sheet.AddTo
        } else if (code != from) {
            val w = WatchPair(from, code)
            viewModelScope.launch { c.settings.updateWatchlist { it + w } }
            pendingFrom = null
            _sheet.value = Sheet.None
        }
    }

    fun toggleKeypad() = _keypadOpen.update { !it }
    fun open(sheet: Sheet) { _sheet.value = sheet }
    fun closeSheet() { _sheet.value = Sheet.None }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { c.settings.setDynamic(v) }
    fun checkForUpdate() { viewModelScope.launch { c.updater.check() } }
    fun installUpdate(info: UpdateInfo) { viewModelScope.launch { c.updater.downloadAndInstall(info) } }
    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { c.settings.setAutoUpdate(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { c.settings.setHaptics(v) }

    fun addAlert(above: Boolean, target: Double) {
        val s = settings.value
        viewModelScope.launch {
            c.settings.updateAlerts { it + Alert(System.currentTimeMillis(), s.from, s.to, target, above) }
        }
        // First alert is the moment the periodic worker starts to matter; make sure it has fresh numbers to compare.
        refreshIfStale()
    }

    fun setAlertActive(id: Long, active: Boolean) {
        viewModelScope.launch { c.settings.updateAlerts { l -> l.map { if (it.id == id) it.copy(active = active) else it } } }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { c.settings.updateAlerts { l -> l.filterNot { it.id == id } } }
    }
}
