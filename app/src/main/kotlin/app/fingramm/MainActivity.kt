package app.fingramm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import app.fingramm.ui.AppRoot
import app.fingramm.ui.AppViewModel
import app.fingramm.ui.theme.FingrammTheme
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity нужна для системного диалога биометрии (BiometricPrompt). */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FingrammTheme {
                Surface(Modifier.fillMaxSize()) { AppRoot(viewModel) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Поворот экрана — не уход в фон: блокировку включаем, только когда приложение действительно скрыто.
        if (!isChangingConfigurations) viewModel.onBackgrounded()
    }
}
