package app.robfx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.HomeTab
import app.robfx.about.Release
import app.robfx.ui.theme.Pal

const val CONTACT_EMAIL = "robFX@robcloud.org"
const val SOURCE_URL = "https://github.com/hkrob/robFX"

data class AboutUi(val version: String, val releases: List<Release>)

interface AboutActions {
    fun selectTab(t: HomeTab)
    fun email()
    fun openSource()
    fun openSettings()
}

private val features = listOf(
    "Rates cross-checked across three independent sources, with a warning when they disagree",
    "Charts from one week to five years, and alerts when a rate reaches your target",
    "Unit price: find the better deal between sizes, shops and currencies",
    "Home-screen widgets you can set up one by one",
)

@Composable
fun AboutContent(ui: AboutUi, actions: AboutActions) {
    val pal = Pal.current
    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Outlined.Settings, "Settings", actions::openSettings, pal.keyText)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                item(key = "#tabs") { HomeTabs(HomeTab.About, actions::selectTab) }
                item(key = "#intro") {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text("robFX ${ui.version}", color = pal.subtext, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "A clean, fast currency converter that shows you how far to trust the number, and helps you spend it well.",
                            color = pal.text, fontSize = 19.sp, fontWeight = FontWeight.Light, lineHeight = 26.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        features.forEach { f ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text("•", color = pal.accent, fontSize = 15.sp, modifier = Modifier.width(18.dp))
                                Text(f, color = pal.subtext, fontSize = 15.sp, lineHeight = 21.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Rates are for information only, not for trading.", color = pal.subtext, fontSize = 13.sp)
                    }
                }

                item(key = "#contact") {
                    Header("Contact")
                    LinkRow(Icons.Outlined.MailOutline, CONTACT_EMAIL, "Questions, ideas and bug reports", "Email $CONTACT_EMAIL", actions::email)
                    LinkRow(Icons.AutoMirrored.Outlined.OpenInNew, "github.com/hkrob/robFX", "Source code and every release", "Open the robFX project page", actions::openSource)
                    Text(
                        "Reporting a problem? Settings → Diagnostics → Copy diagnostic report, and paste it into your email.",
                        color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }

                item(key = "#new") { Header("What's new") }
                if (ui.releases.isEmpty()) {
                    item(key = "#none") { Text("No release notes available.", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(ui.releases.take(RECENT_RELEASES), key = { it.version }) { r ->
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.version, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            if (r.version == ui.version) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "INSTALLED", color = pal.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(pal.accent.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        r.items.forEach { i ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Text("•", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.width(16.dp))
                                Text(i, color = pal.subtext, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

const val RECENT_RELEASES = 6

@Composable
private fun LinkRow(icon: ImageVector, title: String, sub: String, description: String, onClick: () -> Unit) {
    val pal = Pal.current
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = pal.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = pal.subtext, fontSize = 13.sp)
        }
    }
}
