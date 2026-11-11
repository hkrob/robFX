package app.parity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.parity.calc.Calc
import app.parity.data.Alert
import app.parity.data.HistoryPoint
import app.parity.data.PairRate
import app.parity.data.Range
import app.parity.data.Settings
import app.parity.data.SourceStatus
import app.parity.data.ThemeMode
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

enum class Sheet { None, FromPicker, ToPicker, Alerts, Settings }

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
        viewModelScope.launch {
            combine(settings.map { it.from to it.to }.distinctUntilChanged(), settings.map { it.range }.distinctUntilChanged()) { p, r -> p to r }
                .collect { (p, r) -> loadHistory(p.first, p.second, r) }
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

    fun key(k: String) {
        val next = Calc.key(_expr.value, k)
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

    fun toggleKeypad() = _keypadOpen.update { !it }
    fun open(sheet: Sheet) { _sheet.value = sheet }
    fun closeSheet() { _sheet.value = Sheet.None }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { c.settings.setDynamic(v) }
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
