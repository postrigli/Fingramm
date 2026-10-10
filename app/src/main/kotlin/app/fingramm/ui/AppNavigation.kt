package app.fingramm.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fingramm.R
import app.fingramm.domain.model.TransactionType
import app.fingramm.domain.survey.SurveyStep
import app.fingramm.ui.dashboard.DashboardScreen
import app.fingramm.ui.goals.GoalEditScreen
import app.fingramm.ui.goals.GoalsScreen
import app.fingramm.ui.more.AboutScreen
import app.fingramm.ui.more.BackupScreen
import app.fingramm.ui.more.CategoriesScreen
import app.fingramm.ui.more.CategoryEditScreen
import app.fingramm.ui.more.MoreScreen
import app.fingramm.ui.more.RecurringEditScreen
import app.fingramm.ui.more.RecurringListScreen
import app.fingramm.ui.more.SettingsScreen
import app.fingramm.ui.navigation.Routes
import app.fingramm.ui.onboarding.OnboardingScreen
import app.fingramm.ui.survey.SurveyEditScreen
import app.fingramm.ui.survey.SurveySummaryScreen
import app.fingramm.ui.transactions.TransactionEditScreen
import app.fingramm.ui.transactions.TransactionsScreen
import app.fingramm.widget.QuickAction

private data class TopLevelItem(val route: String, val label: Int, val icon: ImageVector)

private val TopLevelItems = listOf(
    TopLevelItem(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Home),
    TopLevelItem(Routes.TRANSACTIONS, R.string.nav_transactions, Icons.AutoMirrored.Filled.List),
    TopLevelItem(Routes.GOALS, R.string.nav_goals, Icons.Filled.Flag),
    TopLevelItem(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz),
)

private fun idArgument() = navArgument("id") {
    type = NavType.LongType
    defaultValue = Routes.NEW
}

/** Граф навигации и нижняя панель (Сводка · Операции · Цели · Ещё). */
@Composable
fun AppNavigation(
    startRoute: String,
    hasProfile: Boolean?,
    pendingAction: QuickAction? = null,
    onActionHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Профиль появился (импорт при первом запуске) или исчез (удаление данных) — перенаправляем.
    var previous by remember { mutableStateOf(hasProfile) }
    LaunchedEffect(hasProfile) {
        val before = previous
        previous = hasProfile
        if (before == true && hasProfile == false) navController.clearAndGo(Routes.ONBOARDING)
        if (before == false && hasProfile == true) navController.clearAndGo(Routes.DASHBOARD)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (currentRoute in Routes.topLevel) {
                NavigationBar {
                    TopLevelItems.forEach { item ->
                        NavigationBarItem(
                            selected = backStack?.destination?.route == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = { navController.clearAndGo(Routes.DASHBOARD) },
                    onRestore = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(onOpenGoals = { navController.navigateTopLevel(Routes.GOALS) })
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(
                    onAdd = { navController.navigate(Routes.transactionEdit()) },
                    onEdit = { navController.navigate(Routes.transactionEdit(it)) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                )
            }
            composable(Routes.GOALS) {
                GoalsScreen(
                    onAdd = { navController.navigate(Routes.goalEdit()) },
                    onEdit = { navController.navigate(Routes.goalEdit(it)) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenSurvey = { navController.navigate(Routes.SURVEY) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(
                Routes.TRANSACTION_EDIT,
                arguments = listOf(
                    idArgument(),
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { TransactionEditScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.GOAL_EDIT, arguments = listOf(idArgument())) {
                GoalEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { type: TransactionType -> navController.navigate(Routes.categoryEdit(type = type.name)) },
                    onEdit = { navController.navigate(Routes.categoryEdit(it)) },
                )
            }
            composable(
                Routes.CATEGORY_EDIT,
                arguments = listOf(
                    idArgument(),
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = TransactionType.EXPENSE.name
                    },
                ),
            ) { CategoryEditScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.RECURRING) {
                RecurringListScreen(
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(Routes.recurringEdit()) },
                    onEdit = { navController.navigate(Routes.recurringEdit(it)) },
                )
            }
            composable(Routes.RECURRING_EDIT, arguments = listOf(idArgument())) {
                RecurringEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SURVEY) {
                SurveySummaryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenStep = { step: SurveyStep -> navController.navigate(Routes.surveyStep(step.name)) },
                )
            }
            composable(
                Routes.SURVEY_STEP,
                arguments = listOf(navArgument("step") { type = NavType.StringType }),
            ) { SurveyEditScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) { BackupScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }

    // Действие из виджета выполняем, когда граф навигации уже готов (backStack != null). Это место после Scaffold
    // намеренно: эффект стартует позже NavHost. Пока приложение заблокировано, AppNavigation не показывается,
    // и действие ждёт разблокировки. Без профиля (онбординг) добавлять нечего — действие сбрасывается.
    val graphReady = backStack != null
    LaunchedEffect(pendingAction, hasProfile, graphReady) {
        if (pendingAction != null && hasProfile != null && graphReady) {
            if (hasProfile) navController.perform(pendingAction)
            onActionHandled()
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.clearAndGo(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Открывает экран, заказанный виджетом. Сначала возвращаемся на сводку (без сохранения состояния, чтобы
 * брошенная форма не «воскресла» позже), затем — нужный экран: «Назад» из формы ведёт на сводку, а не из приложения.
 */
private fun NavHostController.perform(action: QuickAction) {
    when (action) {
        is QuickAction.AddTransaction -> {
            goFresh(Routes.DASHBOARD)
            navigate(Routes.transactionEdit(type = action.type))
        }
        QuickAction.OpenTransactions -> goFresh(Routes.TRANSACTIONS)
        QuickAction.OpenDashboard -> goFresh(Routes.DASHBOARD)
    }
}

private fun NavHostController.goFresh(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
