package app.robfx

import android.content.Context
import app.robfx.data.HistoryRepository
import app.robfx.data.RatesRepository
import app.robfx.data.SettingsStore
import app.robfx.data.source.ErApiSource
import app.robfx.data.source.FawazSource
import app.robfx.data.source.FrankfurterSource
import app.robfx.data.source.Http
import app.robfx.update.Updater
import kotlinx.coroutines.flow.first

/** Hand-rolled DI: three singletons don't justify a framework. */
class AppContainer(context: Context) {
    private val http = Http()
    private val frankfurter = FrankfurterSource(http)
    private val fawaz = FawazSource(http)
    private val erApi = ErApiSource(http)
    private val coinbase = app.robfx.data.source.CoinbaseSource(http)
    private val floatRates = app.robfx.data.source.FloatRatesSource(http)

    val settings = SettingsStore(context)
    val rates = RatesRepository(
        context, listOf(frankfurter, fawaz, erApi, coinbase, floatRates),
        wise = app.robfx.data.source.WiseQuotes(http),
        pairsInUse = {
            val s = settings.settings.first()
            listOf(s.from to s.to) + settings.watchlist.first().map { it.from to it.to } + settings.alerts.first().map { it.from to it.to }
        },
    )
    // Only Frankfurter and Currency-API can do history; the ECB goes first because it's one request instead of ~28.
    val history = HistoryRepository(listOf(frankfurter, fawaz))
    val updater = Updater(context.applicationContext, settings)
    val widgetSeries = app.robfx.widget.WidgetSeries(context.applicationContext, history)
}
