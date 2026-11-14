package app.robfx.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Single-line text that never clips. [candidates] run from most to least precise: each is tried from [maxSize]
 * down to [preferredMin] (the least precise one goes all the way to [hardMin]), so digits are dropped before
 * the type gets small, and the type gets small before anything is cut off.
 */
@Composable
fun FitText(
    candidates: List<String>,
    color: Color,
    maxSize: Int,
    preferredMin: Int,
    hardMin: Int,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Light,
    textAlign: TextAlign = TextAlign.End,
    alignment: Alignment = Alignment.CenterEnd,
) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val maxPx = constraints.maxWidth
        val (text, size) = remember(candidates, maxPx, maxSize, preferredMin, hardMin, fontWeight, base) {
            fun width(t: String, s: Int) =
                measurer.measure(AnnotatedString(t), base.copy(fontSize = s.sp, fontWeight = fontWeight), maxLines = 1, softWrap = false).size.width

            var pick: Pair<String, Int>? = null
            search@ for ((i, t) in candidates.withIndex()) {
                val floor = if (i == candidates.lastIndex) hardMin else preferredMin
                var s = maxSize
                while (s >= floor) {
                    if (width(t, s) <= maxPx) { pick = t to s; break@search }
                    s -= 2
                }
            }
            pick ?: (candidates.last() to hardMin)
        }
        Text(text, color = color, fontSize = size.sp, fontWeight = fontWeight, maxLines = 1, softWrap = false, textAlign = textAlign)
    }
}
