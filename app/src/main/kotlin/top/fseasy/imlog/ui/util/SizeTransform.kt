package top.fseasy.imlog.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** dp to Px */
@Composable
fun Dp.rememberPx(): Int {
  val density = LocalDensity.current
  return remember(density, this) {
    with(density) { this@rememberPx.roundToPx() }
  }
}
