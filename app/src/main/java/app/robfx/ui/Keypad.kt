package app.robfx.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.robfx.ui.theme.Pal

private sealed interface Key {
    class Digit(val label: String, val send: String = label) : Key
    class Op(val glyph: String) : Key
    class Icon(val vector: ImageVector, val desc: String, val send: String, val long: String? = null) : Key
    data object Equals : Key
    data object Swap : Key
}

private val rows: List<List<Key>> = listOf(
    listOf(Key.Icon(Icons.Outlined.AutoFixNormal, "Clear", "C"), Key.Swap, Key.Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", "<", long = "C"), Key.Op("÷")),
    listOf(Key.Digit("7"), Key.Digit("8"), Key.Digit("9"), Key.Op("×")),
    listOf(Key.Digit("4"), Key.Digit("5"), Key.Digit("6"), Key.Op("−")),
    listOf(Key.Digit("1"), Key.Digit("2"), Key.Digit("3"), Key.Op("+")),
    listOf(Key.Digit("0"), Key.Digit("00"), Key.Digit("."), Key.Equals),
)

/** The screenshot's grid: flat tiles separated by one-pixel gaps of background, accent only on the operators. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(
    keyHeight: Dp,
    haptics: Boolean,
    onKey: (String) -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    swapIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.SwapVert,
    swapDesc: String = "Swap currencies",
) {
    val pal = Pal.current
    val view = LocalView.current
    val gap = 1.dp

    @Composable
    fun Cell(modifier: Modifier, fill: Color, desc: String, onClick: () -> Unit, onLong: (() -> Unit)? = null, content: @Composable () -> Unit) {
        Box(
            modifier
                .fillMaxSize()
                .background(fill)
                .semantics { contentDescription = desc }
                .combinedClickable(
                    onClick = {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = onLong?.let { l ->
                        {
                            if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            l()
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { content() }
    }

    Column(modifier.background(pal.bg), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(keyHeight), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    val m = Modifier.weight(1f)
                    when (key) {
                        is Key.Digit -> Cell(m, pal.key, key.label, { onKey(key.send) }) {
                            Text(key.label, color = pal.keyText, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        }
                        is Key.Op -> Cell(m, pal.key, key.glyph, { onKey(key.glyph) }) {
                            Text(key.glyph, color = pal.accent, fontSize = 32.sp, fontWeight = FontWeight.Light)
                        }
                        is Key.Icon -> Cell(m, pal.key, key.desc, { onKey(key.send) }, key.long?.let { l -> { onKey(l) } }) {
                            Icon(key.vector, contentDescription = null, tint = pal.accent, modifier = Modifier.padding(2.dp))
                        }
                        Key.Swap -> Cell(m, pal.key, swapDesc, onSwap) {
                            Icon(swapIcon, contentDescription = null, tint = pal.accent)
                        }
                        Key.Equals -> Cell(m, pal.accent, "Equals", { onKey("=") }) {
                            Text("=", color = pal.onAccent, fontSize = 34.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
            }
        }
    }
}
