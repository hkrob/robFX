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

enum class Sheet { None, FromPicker, ToPicker, Alerts, Settings, SettingsUpdate, AddFrom, AddTo, Multi, MultiAdd, Export }

sealed interface ExportState {
    data object Idle : ExportState
    data class Loading(val period: app.robfx.data.ExportPeriod) : ExportState
    data class Ready(val fileName: String, val csv: String) : ExportState
    data class Failed(val message: String) : ExportState
}

enum class Screen { Home, Converter }

/** The sections of the home screen. */
enum class HomeTab(val title: String) { Markets("Markets"), UnitPrice("Unit price"), About("About") }

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

    private val _homeTab = MutableStateFlow(HomeTab.Markets)
    val homeTab: StateFlow<HomeTab> = _homeTab

    fun selectTab(t: HomeTab) {
        _homeTab.value = t
        if (t != HomeTab.Markets) _editing.value = false
    }

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing

    val watchlist: StateFlow<List<WatchPair>> = c.settings.watchlist.stateIn(viewModelScope, SharingStarted.Eagerly, defaultWatchlist())
    private val sparks = MutableStateFlow<Map<String, List<HistoryPoint>>>(emptyMap())

    val rows: StateFlow<List<WatchRow>> = combine(
        watchlist, sparks, c.rates.snapshot, settings.map { it.sort to it.sortDescending }.distinctUntilChanged(),
    ) { list, sp, _, (sort, desc) ->
        WatchSorting.sort(list.map { WatchRow(it, c.rates.pair(it.from, it.to), sp[it.key]) }, sort, desc)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var pendingFrom: String? = null

    private val _history = MutableStateFlow(HistoryState())
    private val pairNow = combine(c.rates.snapshot, settings.map { it.from to it.to }.distinctUntilChanged()) { _, (f, t) -> c.rates.pair(f, t) }

    /**
     * The chart's series ends at the live rate. Daily history stops at the last published day, so without this the chart
     * and its "change" disagreed with the rate printed just above it whenever the market had moved since.
     */
    val history: StateFlow<HistoryState> = combine(_history, pairNow) { h, p ->
        val last = h.points.lastOrNull()
        if (p == null || last == null) return@combine h
        val today = java.time.Instant.ofEpochMilli(p.updatedAt).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        when {
            today > last.date -> h.copy(points = h.points + HistoryPoint(today, p.rate))
            today == last.date -> h.copy(points = h.points.dropLast(1) + HistoryPoint(today, p.rate))
            else -> h
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryState())

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
        viewModelScope.launch {
            // Rates fetched while the app is open count for alerts too, not just the half-hourly background check.
            if (c.rates.refresh()) app.robfx.work.AlertRunner.run(getApplication())
        }
    }

    // True after Clear (back to the default "1") or a tap on the amount: the next number typed replaces the entry.
    private var replaceNext = false

    fun key(k: String) {
        val next = Calc.keyReplacing(_expr.value, k, replaceNext)
        replaceNext = k == "C"
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

    fun tapSort(tapped: WatchSort) {
        val s = settings.value
        val (sort, desc) = WatchSorting.tap(s.sort, s.sortDescending, tapped)
        viewModelScope.launch { c.settings.setSort(sort, desc) }
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
    /** Tapping the amount: bring the keypad back and treat the amount as selected, so typing replaces it. */
    fun showKeypad() {
        _keypadOpen.value = true
        replaceNext = true
    }
    fun open(sheet: Sheet) { _sheet.value = sheet }
    fun closeSheet() { _sheet.value = Sheet.None }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { c.settings.setDynamic(v) }
    fun checkForUpdate() { viewModelScope.launch { c.updater.check() } }
    fun installUpdate(info: UpdateInfo) { viewModelScope.launch { c.updater.downloadAndInstall(info) } }
    fun setAutoUpdate(v: Boolean) = viewModelScope.launch { c.settings.setAutoUpdate(v) }
    fun setRateDecimals(v: Int) = viewModelScope.launch { c.settings.setRateDecimals(v) }
    fun setAmountDecimals(v: Int) = viewModelScope.launch { c.settings.setAmountDecimals(v) }
    fun setUnitDecimals(v: Int) = viewModelScope.launch { c.settings.setUnitDecimals(v) }
    fun setHaptics(v: Boolean) = viewModelScope.launch { c.settings.setHaptics(v) }

    fun addAlert(above: Boolean, target: Double, repeat: Boolean = false) {
        val s = settings.value
        viewModelScope.launch {
            c.settings.updateAlerts { it + Alert(System.currentTimeMillis(), s.from, s.to, target, above, repeat = repeat) }
        }
        // First alert is the moment the periodic worker starts to matter; make sure it has fresh numbers to compare.
        refreshIfStale()
    }

    /** "Tell me when it moves [pct]% either way" from today's rate. */
    fun addMoveAlert(pct: Double, repeat: Boolean) {
        val s = settings.value
        val now = pair.value?.rate ?: return
        viewModelScope.launch {
            c.settings.updateAlerts { it + Alert(System.currentTimeMillis(), s.from, s.to, now, above = true, kind = Alert.MOVE, movePct = pct, base = now, repeat = repeat) }
        }
        refreshIfStale()
    }

    fun setAlertActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            c.settings.updateAlerts { l ->
                l.map { a ->
                    when {
                        a.id != id -> a
                        // Re-armed: forget the last hit, and a move alert measures from today's rate again.
                        active -> a.copy(active = true, triggeredAt = null, triggeredRate = null, waiting = false, base = if (a.isMove) c.rates.pair(a.from, a.to)?.rate ?: a.base else a.base)
                        else -> a.copy(active = false, triggeredAt = null, triggeredRate = null)
                    }
                }
            }
        }
    }

    // ---- In other currencies ----
    val multi: StateFlow<List<String>> = c.settings.multi.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val snapshot = c.rates.snapshot
    fun rate(from: String, to: String): Double? = c.rates.pair(from, to)?.rate
    fun addMulti(code: String) { viewModelScope.launch { c.settings.updateMulti { it + code } }; _sheet.value = Sheet.Multi }
    fun removeMulti(code: String) { viewModelScope.launch { c.settings.updateMulti { it - code } } }
    /** Tapping a currency in the list makes it the converter's target. */
    fun pickMulti(code: String) = pick(code, isFrom = false)

    // ---- Rate history export ----
    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export

    fun prepareExport(period: app.robfx.data.ExportPeriod) {
        val s = settings.value
        _export.value = ExportState.Loading(period)
        viewModelScope.launch {
            c.history.loadSpan(s.from, s.to, period.start, period.end).fold(
                { pts ->
                    _export.value = ExportState.Ready(
                        app.robfx.data.RateCsv.fileName(s.from, s.to, period),
                        app.robfx.data.RateCsv.build(s.from, s.to, period, pts),
                    )
                },
                { _export.value = ExportState.Failed("No daily rates for ${s.from}/${s.to} in that period") },
            )
        }
    }

    fun exportDone() { _export.value = ExportState.Idle }

    fun deleteAlert(id: Long) {
        viewModelScope.launch { c.settings.updateAlerts { l -> l.filterNot { it.id == id } } }
    }
}
