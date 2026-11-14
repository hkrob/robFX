package app.robfx

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.robfx.calc.Calc
import app.robfx.data.PairRate
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.Evaluation
import app.robfx.unitprice.Recents
import app.robfx.unitprice.SavedComparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import app.robfx.unitprice.Units
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Field { Price, Size, Qty }

data class FocusTarget(val item: Int, val field: Field)

class UnitPriceViewModel(app: Application, initial: Comparison) : AndroidViewModel(app) {
    private val c = app.container
    private val us = Units.isUs()

    private val _comparison = MutableStateFlow(initial)
    val comparison: StateFlow<Comparison> = _comparison

    private val _focus = MutableStateFlow<FocusTarget?>(FocusTarget(0, Field.Price))
    val focus: StateFlow<FocusTarget?> = _focus

    private val _keypadOpen = MutableStateFlow(true)
    val keypadOpen: StateFlow<Boolean> = _keypadOpen

    val recents: StateFlow<List<SavedComparison>> = c.settings.unitRecents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val currencyRecents: StateFlow<List<String>> = c.settings.settings.map { it.recents }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun evaluate(cmp: Comparison): Evaluation = UnitPrice.evaluate(cmp) { f, t -> c.rates.pair(f, t) }

    val evaluation: StateFlow<Evaluation> = combine(_comparison, c.rates.snapshot) { cmp, _ -> evaluate(cmp) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, evaluate(initial))

    /** For the status pill: how healthy the rates behind any conversion are. */
    val status: StateFlow<PairRate?> = combine(_comparison.map { it.currency }.distinctUntilChanged(), c.rates.snapshot) { home, _ ->
        c.rates.pair(home, if (home == "USD") "EUR" else "USD")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(FlowPreview::class)
    private val persist = viewModelScope.launch {
        _comparison.drop(1).debounce(300).collect { c.settings.setUnitComparison(it) }
    }

    private fun edit(transform: (Comparison) -> Comparison) = _comparison.update(transform)

    private fun editItem(i: Int, transform: (UnitItem) -> UnitItem) = edit { cmp ->
        cmp.copy(items = cmp.items.mapIndexed { idx, it -> if (idx == i) transform(it) else it })
    }

    fun focusOn(item: Int, field: Field) { _focus.value = FocusTarget(item, field) }

    private fun valueOf(item: UnitItem, f: Field) = when (f) { Field.Price -> item.price; Field.Size -> item.size; Field.Qty -> item.qty }

    private fun withValue(item: UnitItem, f: Field, v: String) = when (f) {
        Field.Price -> item.copy(price = v)
        Field.Size -> item.copy(size = v)
        Field.Qty -> item.copy(qty = v)
    }

    /** Keypad input goes to whichever field has focus. Clear empties it; "=" settles an expression into a number. */
    fun key(k: String) {
        val f = _focus.value ?: return
        val item = _comparison.value.items.getOrNull(f.item) ?: return
        val next = if (k == "C") "" else Calc.key(valueOf(item, f.field), k)
        editItem(f.item) { withValue(it, f.field, next) }
    }

    /** Price, then size, then quantity, then on to the next item's price. */
    fun next() {
        val f = _focus.value ?: FocusTarget(0, Field.Price)
        val n = _comparison.value.items.size
        _focus.value = when (f.field) {
            Field.Price -> FocusTarget(f.item, Field.Size)
            Field.Size -> FocusTarget(f.item, Field.Qty)
            Field.Qty -> FocusTarget((f.item + 1) % n, Field.Price)
        }
    }

    fun setGroup(g: UnitGroup) = edit { if (it.group == g) it else it.withGroup(g, us) }
    fun setBasis(id: String) = edit { it.copy(basis = id) }
    fun setUnit(i: Int, unitId: String) = editItem(i) { it.copy(unit = unitId) }
    fun setCurrency(i: Int, code: String) = editItem(i) { it.copy(currency = code) }
    fun setHomeCurrency(code: String) = edit { it.withHomeCurrency(code) }

    fun addItem() {
        edit { it.withItemAdded() }
        _focus.value = FocusTarget(_comparison.value.items.lastIndex, Field.Price)
    }

    fun removeItem(i: Int) {
        edit { it.withItemRemoved(i) }
        _focus.update { f -> f?.let { FocusTarget(it.item.coerceAtMost(_comparison.value.items.lastIndex), it.field) } }
    }

    fun toggleKeypad() = _keypadOpen.update { !it }

    /** A comparison is worth remembering only once it actually compared something. */
    private fun saveRecent() {
        val cmp = _comparison.value
        val ev = evaluate(cmp)
        if (ev.readyCount < 2) return
        viewModelScope.launch {
            c.settings.updateUnitRecents { Recents.add(it, SavedComparison(System.currentTimeMillis(), Recents.summary(cmp, ev), cmp)) }
        }
    }

    /** Called when you leave the tab or the app stops: the moment a comparison is "done". */
    fun leave() = saveRecent()

    /** Start over, keeping the group, basis and home currency you were using. */
    fun clearAll() {
        saveRecent()
        val cmp = _comparison.value
        _comparison.value = Comparison.fresh(cmp.group, cmp.currency, us, cmp.basis)
        _focus.value = FocusTarget(0, Field.Price)
    }

    fun restore(saved: SavedComparison) {
        saveRecent()
        _comparison.value = saved.comparison
        _focus.value = FocusTarget(0, Field.Price)
    }

    fun clearRecents() {
        viewModelScope.launch { c.settings.updateUnitRecents { emptyList() } }
    }
}
