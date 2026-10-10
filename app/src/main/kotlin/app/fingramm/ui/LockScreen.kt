package app.fingramm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fingramm.R
import app.fingramm.security.AppLock
import app.fingramm.security.findFragmentActivity

/** Экран блокировки: подтверждение биометрией или PIN устройства. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_title)
    val subtitle = stringResource(R.string.lock_subtitle)
    val prompt = {
        context.findFragmentActivity()?.let { activity ->
            AppLock.authenticate(activity, title, subtitle, onSuccess = onUnlock, onFailure = {})
        }
    }
    LaunchedEffect(Unit) { prompt() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = { prompt() }) { Text(stringResource(R.string.lock_unlock)) }
    }
}
