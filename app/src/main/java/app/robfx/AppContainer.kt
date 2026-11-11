package app.robfx

import android.content.Context
import app.robfx.data.HistoryRepository
import app.robfx.data.RatesRepository
import app.robfx.data.SettingsStore
import app.robfx.data.source.ErApiSource
import app.robfx.data.source.FawazSource
import app.robfx.data.source.FrankfurterSource
import app.robfx.data.source.Http

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
