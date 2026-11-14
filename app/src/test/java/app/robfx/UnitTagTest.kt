package app.robfx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.robfx.data.PairRate
import app.robfx.data.Quality
import app.robfx.data.ThemeMode
import app.robfx.ui.RecentsList
import app.robfx.ui.TagEditor
import app.robfx.ui.UnitPriceActions
import app.robfx.ui.UnitPriceContent
import app.robfx.ui.UnitPriceUi
import app.robfx.ui.theme.RobFxTheme
import app.robfx.unitprice.Comparison
import app.robfx.unitprice.Recents
import app.robfx.unitprice.SavedComparison
import app.robfx.unitprice.UnitGroup
import app.robfx.unitprice.UnitItem
import app.robfx.unitprice.UnitPrice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h892dp-port-xxhdpi")
class UnitTagTest {
    @get:Rule val rule = createComposeRule()

    private class Recorder : UnitPriceActions {
        val log = mutableListOf<String>()
        override fun focusOn(item: Int, field: Field) {}
        override fun key(k: String) {}
        override fun next() {}
        override fun setGroup(g: UnitGroup) {}
        override fun setBasis(id: String) {}
        override fun addItem() {}
        override fun removeItem(i: Int) {}
        override fun clearAll() {}
        override fun toggleKeypad() {}
        override fun pickUnit(item: Int) {}
        override fun pickCurrency(item: Int) {}
        override fun pickHomeCurrency() {}
        override fun openRecents() {}
        override fun pickTag() { log += "pickTag" }
        override fun openSettings() {}
        override fun selectTab(t: HomeTab) {}
    }

    private val base = Comparison(
        group = UnitGroup.Weight, basis = "100g", currency = "HKD",
        items = listOf(UnitItem("5", "100", "", "g", "HKD"), UnitItem("7", "150", "", "g", "HKD")),
    )

    private val now = System.currentTimeMillis()

    /** Sheets sit on the theme's background in the app; give the bare composable the same one. */
    @androidx.compose.runtime.Composable
    private fun OnSheet(content: @androidx.compose.runtime.Composable () -> Unit) =
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(app.robfx.ui.theme.Pal.current.bg)) { content() }

    private fun saved(minutesAgo: Long, tag: String, price: String) =
        SavedComparison(now - minutesAgo * 60_000, "Weight · 2 items · best Item B HK$$price / 100 g", base.copy(items = base.items.map { it.copy(price = price) }).withTag(tag))

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun tappingTheTagChipOpensTheEditorAndShowsTheTag() {
        val r = Recorder()
        val c = base.withTag("Pasta night")
        val ev = UnitPrice.evaluate(c) { _, _ -> PairRate(1.0, emptyMap(), 0.0, Quality.Consensus, 0) }
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { UnitPriceContent(UnitPriceUi(c, ev, null, true, true, null), r) } }
        rule.onNodeWithText("Pasta night").performClick()
        assertEquals(listOf("pickTag"), r.log)
        shot("unit_tagged")
    }

    @Test fun untaggedShowsAQuietPromptNotAnEmptyLabel() {
        val ev = UnitPrice.evaluate(base) { _, _ -> null }
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { UnitPriceContent(UnitPriceUi(base, ev, null, true, false, null), Recorder()) } }
        rule.onNodeWithText("Tag").assertExists()
    }

    @Test fun editorTakesASuggestionAndSavesIt() {
        var out: String? = null
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { OnSheet { TagEditor("", listOf("Milk", "Pasta night")) { out = it } } } }
        rule.onNodeWithText("Milk").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals("Milk", out)
        shot("unit_tag_editor")
    }

    @Test fun editorCanRemoveAnExistingTag() {
        var out: String? = "unset"
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { TagEditor("Milk", emptyList()) { out = it } } }
        rule.onNodeWithText("Remove tag").performClick()
        assertEquals("", out)
    }

    @Test fun historyShowsTagsAndFiltersByThem() {
        val list = listOf(saved(4, "Pasta", "6"), saved(95, "", "8"), saved(1500, "Milk", "9"), saved(4000, "Pasta", "7"))
        assertEquals(listOf("Pasta", "Milk"), Recents.tags(list))
        var picked: SavedComparison? = null
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { OnSheet { RecentsList(list, { picked = it }, {}) } } }
        rule.onNodeWithText("best Item B HK$9 / 100 g", substring = true).assertExists()
        shot("unit_recents_tagged")
        // Choosing the "Milk" chip leaves only the Milk comparison.
        rule.onAllNodesWithText("Milk")[0].performClick()
        rule.onNodeWithText("best Item B HK$6 / 100 g", substring = true).assertDoesNotExist()
        rule.onNodeWithText("best Item B HK$9 / 100 g", substring = true).performClick()
        assertEquals("9", picked?.comparison?.items?.first()?.price)
    }
}
