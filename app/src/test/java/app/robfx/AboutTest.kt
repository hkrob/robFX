package app.robfx

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import app.robfx.about.Changelog
import app.robfx.about.Release
import app.robfx.data.ThemeMode
import app.robfx.ui.AboutActions
import app.robfx.ui.AboutContent
import app.robfx.ui.AboutUi
import app.robfx.ui.CONTACT_EMAIL
import app.robfx.ui.theme.RobFxTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class ChangelogTest {
    // Unit tests run with the app module as the working directory.
    private val file = File("../changelog/CHANGELOG.md")
    private val version = Regex("versionName = \"(.*)\"").find(File("build.gradle.kts").readText())!!.groupValues[1]

    @Test fun parsesVersionsAndBulletsNewestFirst() {
        val r = Changelog.parse("# title\nintro text\n\n## 1.2.0\n- one\n- two that\n  wraps\n\n## v1.1.0\n* older\n\n## 1.0.0\n")
        assertEquals(listOf("1.2.0", "1.1.0"), r.map { it.version })
        assertEquals(listOf("one", "two that wraps"), r[0].items)
        assertEquals(listOf("older"), r[1].items)
    }

    @Test fun emptyOrJunkGivesNothingRatherThanCrashing() {
        assertEquals(emptyList<Release>(), Changelog.parse(""))
        assertEquals(emptyList<Release>(), Changelog.parse("just some words\n- a stray bullet"))
    }

    /** The guard: a version can't ship without saying what's new, because the release notes come from this file. */
    @Test fun theVersionBeingBuiltHasAnEntryAndItIsTheNewest() {
        val releases = Changelog.parse(file.readText())
        assertTrue("changelog/CHANGELOG.md has no '## $version' section", releases.any { it.version == version })
        assertEquals("the newest changelog entry should be the version being built", version, releases.first().version)
        assertTrue(releases.all { it.items.isNotEmpty() })
        assertEquals("duplicate versions in the changelog", releases.size, releases.map { it.version }.toSet().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class AboutScreenTest {
    @get:Rule val rule = createComposeRule()

    private class Recorder : AboutActions {
        val log = mutableListOf<String>()
        override fun selectTab(t: HomeTab) { log += "tab $t" }
        override fun email() { log += "email" }
        override fun openSource() { log += "source" }
        override fun openSettings() { log += "settings" }
    }

    private val releases = (9 downTo 1).map { Release("1.$it.0", listOf("Change A in 1.$it", "Change B in 1.$it")) }

    @Test fun showsDescriptionVersionContactAndRecentChanges() {
        val r = Recorder()
        rule.setContent { RobFxTheme(ThemeMode.Amoled, false) { AboutContent(AboutUi("1.9.0", releases), r) } }
        rule.onNodeWithText("robFX 1.9.0").assertExists()
        rule.onNodeWithText("currency converter", substring = true).assertExists()
        rule.onNodeWithText(CONTACT_EMAIL).performClick()
        rule.onNodeWithText("github.com/hkrob/robFX").performClick()
        rule.onNodeWithText("Markets").performClick()
        assertEquals(listOf("email", "source", "tab Markets"), r.log)
        assertEquals("robFX@robcloud.org", CONTACT_EMAIL)
        File("build/screens").mkdirs()
        rule.onRoot().captureRoboImage("build/screens/about.png")

        // The installed version is marked, and only the recent releases are listed (six of these nine).
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("INSTALLED"))
        rule.onRoot().captureRoboImage("build/screens/about_changes.png")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Change A in 1.4"))
        rule.onNodeWithText("Change A in 1.3").assertDoesNotExist()
    }

    @Test fun noReleaseNotesIsStatedPlainly() {
        rule.setContent { RobFxTheme(ThemeMode.Light, false) { AboutContent(AboutUi("1.0.0", emptyList()), Recorder()) } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No release notes available."))
    }
}
