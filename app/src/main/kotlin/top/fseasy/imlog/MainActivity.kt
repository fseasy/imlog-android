package top.fseasy.imlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import top.fseasy.imlog.navigation.RootAppScreen
import top.fseasy.imlog.ui.theme.ImlogTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      ImlogTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
          RootAppScreen()
        }
      }
    }
  }
}
