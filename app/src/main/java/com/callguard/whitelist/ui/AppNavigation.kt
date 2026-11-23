package com.callguard.whitelist.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.callguard.whitelist.MainActivity
import com.callguard.whitelist.ui.screens.CategoriesScreen
import com.callguard.whitelist.ui.screens.ContactImportScreen
import com.callguard.whitelist.ui.screens.HistoryScreen
import com.callguard.whitelist.ui.screens.HomeScreen
import com.callguard.whitelist.ui.screens.SettingsScreen
import com.callguard.whitelist.ui.screens.WhitelistScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Início", Icons.Filled.Shield),
    WHITELIST("whitelist", "Whitelist", Icons.Filled.VerifiedUser),
    HISTORY("history", "Histórico", Icons.Filled.History),
    CATEGORIES("categories", "Categorias", Icons.Filled.Category),
    SETTINGS("settings", "Ajustes", Icons.Filled.Settings),
}

private const val ROUTE_IMPORT = "import_contacts"

private fun NavController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(openRequest: String?, onOpenHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(openRequest) {
        if (openRequest == MainActivity.OPEN_HISTORY) {
            nav.openTab(Tab.HISTORY.route)
            onOpenHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (currentRoute != ROUTE_IMPORT) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    onOpenHistory = { nav.openTab(Tab.HISTORY.route) },
                    onOpenWhitelist = { nav.openTab(Tab.WHITELIST.route) },
                    showMessage = showMessage,
                )
            }
            composable(Tab.WHITELIST.route) { WhitelistScreen(showMessage) }
            composable(Tab.HISTORY.route) { HistoryScreen(showMessage) }
            composable(Tab.CATEGORIES.route) { CategoriesScreen(showMessage) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(onImportContacts = { nav.navigate(ROUTE_IMPORT) }, showMessage = showMessage)
            }
            composable(ROUTE_IMPORT) {
                ContactImportScreen(onBack = { nav.popBackStack() }, showMessage = showMessage)
            }
        }
    }
}
