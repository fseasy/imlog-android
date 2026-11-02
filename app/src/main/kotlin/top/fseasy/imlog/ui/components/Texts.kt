package top.fseasy.imlog.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily


/**
 * A wrapper to force monospace text, which maybe useful for duration displaying
 */
@Composable
fun PlayerDurationText(text: String, style: TextStyle) =
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = style,
    )
