package top.fseasy.imlog.features.appinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import top.fseasy.imlog.R
import top.fseasy.imlog.ui.components.HighlightConfig
import top.fseasy.imlog.ui.components.HighlightedText


@Composable
fun Splash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center
    ) {
        HighlightedText(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            highlight = HighlightConfig(),
            textAlign = TextAlign.Center,
        )
    }
}