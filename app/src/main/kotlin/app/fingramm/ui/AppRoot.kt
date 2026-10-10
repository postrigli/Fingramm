package app.fingramm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fingramm.ui.common.LocalCurrency

/** Корень UI: пока читаются настройки — пустой фон, затем блокировка или навигация. */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val start by viewModel.startRoute.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val hasProfile by viewModel.hasProfile.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val route = start
    when {
        route == null || locked == null ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        locked == true -> LockScreen(onUnlock = viewModel::unlock)
        else -> CompositionLocalProvider(LocalCurrency provides currency) {
            AppNavigation(startRoute = route, hasProfile = hasProfile)
        }
    }
}
