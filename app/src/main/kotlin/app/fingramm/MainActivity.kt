package app.fingramm

import android.content.Intent
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
import app.fingramm.widget.QuickAction
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity нужна для системного диалога биометрии (BiometricPrompt). */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Повторно (после поворота или восстановления процесса) действие виджета не выполняем: оно уже отработало.
        if (savedInstanceState == null) QuickAction.fromIntent(intent)?.let(viewModel::offerAction)
        setContent {
            FingrammTheme {
                Surface(Modifier.fillMaxSize()) { AppRoot(viewModel) }
            }
        }
    }

    /** Приложение уже открыто, а пользователь нажал кнопку виджета (`launchMode="singleTop"`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        QuickAction.fromIntent(intent)?.let(viewModel::offerAction)
    }

    override fun onStop() {
        super.onStop()
        // Поворот экрана — не уход в фон: блокировку включаем, только когда приложение действительно скрыто.
        if (!isChangingConfigurations) viewModel.onBackgrounded()
    }
}
