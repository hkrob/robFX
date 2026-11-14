package app.robfx.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.robfx.Screen
import kotlin.coroutines.cancellation.CancellationException

/**
 * Top-level navigation: Markets is the root, the converter sits one level above it.
 * Back (button or swipe) unwinds one step at a time: converter -> markets, and edit mode -> normal.
 * Kept free of the ViewModel so the back behaviour can be tested on its own.
 *
 * The converter -> markets step is a predictive back: while the swipe is in flight the converter shrinks and
 * slides away over Markets, and only a completed gesture navigates. A cancelled one springs back.
 */
@Composable
fun AppRoot(
    screen: Screen,
    editing: Boolean,
    unitTabOpen: Boolean,
    onCloseUnitTab: () -> Unit,
    onBackToMarkets: () -> Unit,
    onStopEditing: () -> Unit,
    markets: @Composable () -> Unit,
    converter: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var fromRight by remember { mutableFloatStateOf(1f) }

    PredictiveBackHandler(enabled = screen == Screen.Converter) { events ->
        try {
            events.collect {
                progress = it.progress
                fromRight = if (it.swipeEdge == androidx.activity.BackEventCompat.EDGE_RIGHT) -1f else 1f
            }
            onBackToMarkets()
            progress = 0f
        } catch (e: CancellationException) {
            progress = 0f
            throw e
        }
    }
    BackHandler(enabled = screen == Screen.Home && editing, onBack = onStopEditing)
    // Unit price is a tab of the home screen: back goes to Markets before it would ever leave the app.
    BackHandler(enabled = screen == Screen.Home && unitTabOpen, onBack = onCloseUnitTab)

    val width = LocalConfiguration.current.screenWidthDp.dp
    if (screen == Screen.Converter && progress > 0f) {
        // Mid-gesture: Markets is revealed underneath the shrinking converter.
        Box(Modifier.fillMaxSize()) {
            markets()
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.1f * progress
                        scaleX = s
                        scaleY = s
                        translationX = fromRight * width.toPx() * 0.08f * progress
                        alpha = 1f - 0.25f * progress
                    }
                    .clip(RoundedCornerShape((28 * progress).dp)),
            ) { converter() }
        }
    } else {
        Crossfade(screen, label = "screen") { s ->
            when (s) {
                Screen.Home -> markets()
                Screen.Converter -> converter()
            }
        }
    }
}
