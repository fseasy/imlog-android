package top.fseasy.imlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import top.fseasy.imlog.navigation.RootAppScreen
import top.fseasy.imlog.ui.commonmain.ExternalFileOpener
import top.fseasy.imlog.ui.commonmain.LocalExternalFileOpener
import top.fseasy.imlog.ui.theme.ImlogTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  @Inject lateinit var externalFileOpener: ExternalFileOpener

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
        navigationBarStyle =
            SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
    )
    window.isNavigationBarContrastEnforced = false

    setContent {
      ImlogTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
          CompositionLocalProvider(LocalExternalFileOpener provides externalFileOpener) {
            RootAppScreen()
          }
        }
      }
    }
  }
}
