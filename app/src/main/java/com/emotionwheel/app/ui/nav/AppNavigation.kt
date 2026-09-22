package com.emotionwheel.app.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.emotionwheel.app.R
import com.emotionwheel.app.ui.entry.EntryScreen
import com.emotionwheel.app.ui.journal.JournalScreen
import com.emotionwheel.app.ui.settings.SettingsScreen
import com.emotionwheel.app.ui.wheel.WheelScreen

object Routes {
    const val WHEEL = "wheel"
    const val JOURNAL = "journal"
    const val SETTINGS = "settings"

    /** Both arguments are optional: new entry, edit, or new entry pre-filled from the wheel. */
    const val ENTRY = "entry?entryId={entryId}&emotionId={emotionId}"

    fun entry(entryId: String? = null, emotionId: String? = null): String =
        "entry?entryId=${entryId.orEmpty()}&emotionId=${emotionId.orEmpty()}"
}

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val Tabs = listOf(
    Tab(Routes.WHEEL, R.string.nav_wheel, Icons.Default.DonutLarge),
    Tab(Routes.JOURNAL, R.string.nav_journal, Icons.AutoMirrored.Filled.MenuBook),
)

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    Scaffold(
        // Each screen owns its window insets: the ones with a top bar through their
        // own Scaffold, the wheel through statusBarsPadding. Letting this outer
        // Scaffold add them too is what pushed every title down twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // The bar is for the two places the user lives in; forms and settings are
            // pushed on top of them and hide it.
            if (currentRoute in Tabs.map { it.route }) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WHEEL,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.WHEEL) {
                WheelScreen(
                    onLogEmotion = { emotion ->
                        navController.navigate(Routes.entry(emotionId = emotion.id))
                    },
                )
            }

            composable(Routes.JOURNAL) {
                JournalScreen(
                    onEditEntry = { id -> navController.navigate(Routes.entry(entryId = id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.ENTRY) { entry ->
                val entryId = entry.arguments?.getString("entryId")?.takeIf { it.isNotEmpty() }
                val emotionId = entry.arguments?.getString("emotionId")?.takeIf { it.isNotEmpty() }
                EntryScreen(
                    entryId = entryId,
                    initialEmotionId = emotionId,
                    onDone = {
                        navController.navigate(Routes.JOURNAL) {
                            popUpTo(Routes.WHEEL)
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
