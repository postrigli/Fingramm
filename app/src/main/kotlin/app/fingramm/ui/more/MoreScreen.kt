@file:OptIn(ExperimentalMaterial3Api::class)

package app.fingramm.ui.more

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.fingramm.R
import app.fingramm.ui.common.BackTopBar
import app.fingramm.ui.common.InfoCard
import app.fingramm.ui.common.ScreenColumn
import app.fingramm.ui.common.SectionTitle

@Composable
fun MoreScreen(
    onOpenSurvey: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val items = listOf(
        Triple(R.string.more_survey, R.string.more_survey_hint, onOpenSurvey),
        Triple(R.string.more_categories, R.string.more_categories_hint, onOpenCategories),
        Triple(R.string.more_recurring, R.string.more_recurring_hint, onOpenRecurring),
        Triple(R.string.more_settings, R.string.more_settings_hint, onOpenSettings),
        Triple(R.string.more_backup, R.string.more_backup_hint, onOpenBackup),
        Triple(R.string.more_about, R.string.more_about_hint, onOpenAbout),
    )
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            items.forEach { (title, hint, action) ->
                ListItem(
                    headlineContent = { Text(stringResource(title)) },
                    supportingContent = { Text(stringResource(hint)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = action),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.more_about), onBack) }) { padding ->
        ScreenColumn(Modifier.padding(padding)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)
            SectionTitle(stringResource(R.string.about_privacy_title))
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium)
            SectionTitle(stringResource(R.string.disclaimer_title))
            InfoCard { Text(stringResource(R.string.disclaimer_full), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
