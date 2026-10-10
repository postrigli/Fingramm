package app.fingramm.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Запрос на показ уведомления: результат — выдано ли разрешение. */
typealias NotificationPermissionRequest = (onResult: (Boolean) -> Unit) -> Unit

/**
 * Запрашивает POST_NOTIFICATIONS (Android 13+) в момент выбора режима напоминаний, а не при первом запуске.
 * На старых версиях Android разрешение не требуется. Отказ не мешает работе приложения.
 */
@Composable
fun rememberNotificationPermissionRequest(): NotificationPermissionRequest {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }
    return remember(context, launcher) {
        { onResult ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                onResult(true)
            } else {
                pending = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/** Разрешены ли уведомления сейчас (для подсказки в настройках). */
fun notificationsAllowed(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
