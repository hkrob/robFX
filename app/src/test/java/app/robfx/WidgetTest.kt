package app.robfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import app.robfx.data.ThemeMode
import app.robfx.ui.theme.Pal
import app.robfx.ui.theme.RobFxTheme
import app.robfx.widget.ConvertContent
import app.robfx.widget.ConvertModel
import app.robfx.widget.ConvertRow
import app.robfx.widget.ListModel
import app.robfx.widget.ListRow
import app.robfx.widget.PairContent
import app.robfx.widget.PairModel
import app.robfx.widget.WatchlistContent
import app.robfx.widget.WidgetConfig
import app.robfx.widget.WidgetConfigScreen
import app.robfx.widget.WidgetTap
import app.robfx.widget.WidgetTheme
import app.robfx.widget.WidgetType
import app.robfx.widget.Widgets
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

private val series = listOf(7.81, 7.82, 7.815, 7.83, 7.84, 7.835, 7.845)
private val pairModel = PairModel("USD", "HKD", 1.0, 7.8449, 0.0045, series, 1_794_355_352_000L, false)
private val listModel = ListModel(
    listOf(ListRow("USD", "HKD", 7.8449, 0.0011), ListRow("AUD", "USD", 0.6781, -0.0124), ListRow("EUR", "GBP", 0.8349, 0.0017), ListRow("USD", "JPY", 159.779, null)),
    1_794_355_352_000L, false,
)
private val convertModel = ConvertModel("HKD", 100.0, listOf(ConvertRow("USD", 12.75), ConvertRow("AUD", 18.8), ConvertRow("CNY", 85.05)), 1_794_355_352_000L, true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetContentTest {
    private val open = actionStartActivity<MainActivity>()

    @Test fun singlePairShowsRateChangeAndPeriod() = runGlanceAppWidgetUnitTest {
        provideComposable { PairContent(WidgetConfig(), pairModel, DpSize(200.dp, 120.dp), 2f, open) }
        onNode(hasText("7.8449")).assertExists()
        onNode(hasText("+0.45%")).assertExists()
        onNode(hasText("week")).assertExists()
    }

    @Test fun singlePairHonoursAmountAndToggles() = runGlanceAppWidgetUnitTest {
        val cfg = WidgetConfig(amount = "100", showChange = false, showFlags = false, showUpdated = false)
        provideComposable { PairContent(cfg, pairModel.copy(amount = 100.0), DpSize(200.dp, 120.dp), 2f, open) }
        onNode(hasText("784.49")).assertExists()
        onNode(hasText("100 USD =")).assertExists()
        onNode(hasText("USD → HKD")).assertExists()
        onNode(hasText("+0.45%")).assertDoesNotExist()
    }

    @Test fun singlePairWithoutRatesShowsADashNotACrash() = runGlanceAppWidgetUnitTest {
        provideComposable { PairContent(WidgetConfig(), pairModel.copy(rate = null, change = null, series = null, updatedAt = null), DpSize(200.dp, 120.dp), 2f, open) }
        onNode(hasText("—")).assertExists()
    }

    @Test fun oneRowTallIsASingleLine() = runGlanceAppWidgetUnitTest {
        provideComposable { PairContent(WidgetConfig(), pairModel, DpSize(200.dp, 50.dp), 2f, open) }
        onNode(hasText("USD/HKD")).assertExists()
        onNode(hasText("7.8449")).assertExists()
    }

    @Test fun watchlistListsPairsAndChanges() = runGlanceAppWidgetUnitTest {
        provideComposable { WatchlistContent(WidgetConfig(WidgetType.Watchlist, showFlags = false), listModel, DpSize(260.dp, 190.dp), open) }
        onNode(hasText("USD/HKD")).assertExists()
        onNode(hasText("0.6781")).assertExists()
        onNode(hasText("−1.24%")).assertExists()
        onNode(hasText("Markets")).assertExists()
    }

    @Test fun emptyWatchlistExplainsItself() = runGlanceAppWidgetUnitTest {
        provideComposable { WatchlistContent(WidgetConfig(WidgetType.Watchlist), ListModel(emptyList(), null, false), DpSize(260.dp, 190.dp), open) }
        onNode(hasText("No pairs yet")).assertExists()
    }

    @Test fun convertShowsEachTargetAndFlagsOffline() = runGlanceAppWidgetUnitTest {
        provideComposable { ConvertContent(WidgetConfig(WidgetType.Convert, showFlags = false), convertModel, DpSize(200.dp, 150.dp), open) }
        onNode(hasText("100 HKD")).assertExists()
        onNode(hasText("12.75")).assertExists()
        onNode(hasText("85.05")).assertExists()
        onNode(hasText("Offline")).assertExists()
    }

    @Test fun shortListWidgetsShowOnlyTheRowsThatFit() = runGlanceAppWidgetUnitTest {
        provideComposable { WatchlistContent(WidgetConfig(WidgetType.Watchlist, showFlags = false), listModel, DpSize(260.dp, 110.dp), open) }
        onNode(hasText("USD/HKD")).assertExists()
        onNode(hasText("AUD/USD")).assertExists()
        onNode(hasText("USD/JPY")).assertDoesNotExist()
    }

    @Test fun rowFitMaths() {
        assertEquals(2, app.robfx.widget.rowsThatFit(110.dp, 50, 29, 9))
        assertEquals(1, app.robfx.widget.rowsThatFit(40.dp, 50, 29, 9))
        assertEquals(9, app.robfx.widget.rowsThatFit(900.dp, 50, 29, 9))
    }

    @Test fun providerClassesMapToTypes() {
        assertEquals(WidgetType.Watchlist, Widgets.typeOf("app.robfx.widget.WatchlistWidgetReceiver"))
        assertEquals(WidgetType.Convert, Widgets.typeOf("app.robfx.widget.ConvertWidgetReceiver"))
        assertEquals(WidgetType.Single, Widgets.typeOf("app.robfx.widget.RobFxWidgetReceiver"))
        assertEquals(WidgetType.Single, Widgets.typeOf(null))
    }
}

/** Renders widgets the way a launcher does: Glance -> RemoteViews -> real Views -> bitmap. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WidgetRenderTest {
    private class Fixed(val body: @Composable () -> Unit) : GlanceAppWidget() {
        override val sizeMode = SizeMode.Exact
        override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { body() }
    }

    private fun render(name: String, w: Int, h: Int, body: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val d = ctx.resources.displayMetrics.density
        val rv = runBlocking { Fixed(body).compose(ctx, size = DpSize(w.dp, h.dp)) }
        val host = FrameLayout(ctx)
        val view = rv.apply(ctx, host)
        host.addView(view)
        val wp = (w * d).toInt()
        val hp = (h * d).toInt()
        host.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, wp, hp)
        // List rows arrive through an adapter; let it deliver, then lay out again.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        host.measure(View.MeasureSpec.makeMeasureSpec(wp, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hp, View.MeasureSpec.EXACTLY))
        host.layout(0, 0, wp, hp)
        val bmp = Bitmap.createBitmap(wp, hp, Bitmap.Config.ARGB_8888)
        // A mid-grey "wallpaper" so transparency and rounded corners are visible.
        Canvas(bmp).apply { drawColor(0xFF7A8794.toInt()); host.draw(this) }
        File("build/screens").mkdirs()
        File("build/screens/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val open = actionStartActivity<MainActivity>()

    @Test fun renders() {
        render("widget_pair_black", 200, 120) { PairContent(WidgetConfig(), pairModel, DpSize(200.dp, 120.dp), 2.625f, open) }
        render("widget_pair_light_amount", 200, 120) {
            PairContent(WidgetConfig(theme = WidgetTheme.Light, amount = "100"), pairModel.copy(amount = 100.0, change = -0.012, series = series.reversed()), DpSize(200.dp, 120.dp), 2.625f, open)
        }
        render("widget_pair_row", 220, 52) { PairContent(WidgetConfig(theme = WidgetTheme.Dark, transparency = 30), pairModel, DpSize(220.dp, 52.dp), 2.625f, open) }
        render("widget_watchlist", 260, 190) { WatchlistContent(WidgetConfig(WidgetType.Watchlist, transparency = 30), listModel, DpSize(260.dp, 190.dp), open) }
        render("widget_watchlist_narrow", 180, 120) { WatchlistContent(WidgetConfig(WidgetType.Watchlist, theme = WidgetTheme.Light), listModel, DpSize(180.dp, 120.dp), open) }
        render("widget_convert_light", 200, 150) { ConvertContent(WidgetConfig(WidgetType.Convert, theme = WidgetTheme.Light), convertModel, DpSize(200.dp, 150.dp), open) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class WidgetConfigScreenTest {
    @get:Rule val rule = createComposeRule()

    private var cfg by mutableStateOf(WidgetConfig())
    private var saved = 0

    private fun show(initial: WidgetConfig, mode: ThemeMode = ThemeMode.Amoled) {
        cfg = initial
        rule.setContent {
            RobFxTheme(mode, false) {
                Box(Modifier.fillMaxSize().background(Pal.current.bg)) { WidgetConfigScreen(cfg, listOf("HKD", "USD"), { cfg = it }, {}, { saved++ }) }
            }
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun singlePairSettingsChangeTheConfig() {
        show(WidgetConfig(WidgetType.Single, "USD", "HKD"))
        rule.onNodeWithText("Single pair widget").assertExists()
        rule.onNodeWithContentDescription("Flip direction").performClick()
        assertEquals("HKD" to "USD", cfg.from to cfg.to)
        rule.onNodeWithText("Light").performClick()
        rule.onNodeWithText("30%").performClick()
        assertEquals(WidgetTheme.Light, cfg.theme)
        assertEquals(30, cfg.transparency)
        rule.onNodeWithText("Sparkline").performClick()
        assertEquals(false, cfg.showSpark)
        shot("widget_config_single")
        rule.onNodeWithText("Save widget").performClick()
        assertEquals(1, saved)
    }

    @Test fun watchlistCanFollowMarketsOrItsOwnPairs() {
        show(WidgetConfig(WidgetType.Watchlist, followMarkets = true, pairs = listOf("USD/HKD", "AUD/USD")))
        rule.onNodeWithText("USD/HKD").assertDoesNotExist()
        rule.onNodeWithText("Choose pairs").performClick()
        assertEquals(false, cfg.followMarkets)
        rule.onNodeWithContentDescription("Remove USD/HKD").performClick()
        assertEquals(listOf("AUD/USD"), cfg.pairs)
        rule.onNodeWithText("Add pair").assertExists()
        shot("widget_config_watchlist")
    }

    @Test fun convertHasTargetsAndNoChangeSettings() {
        show(WidgetConfig.default(WidgetType.Convert, "HKD", "AUD"), ThemeMode.Light)
        rule.onNodeWithText("Quick convert widget").assertExists()
        rule.onNodeWithText("Change period").assertDoesNotExist()
        rule.onNodeWithText("Add currency").assertExists()
        rule.onNodeWithText("Refresh rates").performScrollTo().performClick()
        assertEquals(WidgetTap.Refresh, cfg.tap)
        shot("widget_config_convert")
    }
}
