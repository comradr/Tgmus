package app.tgmus

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tgmus.core.telegram.AuthState
import app.tgmus.feature.auth.AuthScreen
import app.tgmus.feature.home.HomeScreen
import app.tgmus.ui.theme.TgmusTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Приложение всегда тёмное, поэтому значки системных панелей всегда светлые.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val telegram = (application as TgmusApp).telegram
        setContent {
            TgmusTheme {
                val authState by telegram.authState.collectAsStateWithLifecycle()
                if (authState == AuthState.Ready) {
                    HomeScreen()
                } else {
                    AuthScreen()
                }
            }
        }
    }
}
