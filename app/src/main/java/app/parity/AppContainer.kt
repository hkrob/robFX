package app.parity

import android.content.Context
import app.parity.data.HistoryRepository
import app.parity.data.RatesRepository
import app.parity.data.SettingsStore
import app.parity.data.source.ErApiSource
import app.parity.data.source.FawazSource
import app.parity.data.source.FrankfurterSource
import app.parity.data.source.Http

/** Hand-rolled DI: three singletons don't justify a framework. */
class AppContainer(context: Context) {
    private val http = Http()
    private val frankfurter = FrankfurterSource(http)
    private val fawaz = FawazSource(http)
    private val erApi = ErApiSource(http)

    val settings = SettingsStore(context)
    val rates = RatesRepository(context, listOf(frankfurter, fawaz, erApi))
    // Only Frankfurter and Currency-API can do history; the ECB goes first because it's one request instead of ~28.
    val history = HistoryRepository(listOf(frankfurter, fawaz))
}
