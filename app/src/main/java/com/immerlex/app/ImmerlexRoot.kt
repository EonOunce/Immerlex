package com.immerlex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.immerlex.app.ui.ImmerlexTheme
import com.immerlex.feature.wordbank.WordBankRepository
import com.immerlex.feature.wordbank.WordBankScreen
import com.immerlex.feature.reader.ReaderScreen
import com.immerlex.feature.reader.ReaderViewModel
import com.immerlex.feature.settings.SettingsScreen
import com.immerlex.feature.settings.SettingsViewModel
import com.immerlex.feature.wordbank.WordBankViewModel

@Composable
fun ImmerlexRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as ImmerlexApplication).container
    val appViewModelFactory = viewModelFactory {
        initializer {
            WordBankViewModel(
                WordBankRepository(container.db, container.dictionary, container.importRepository),
            )
        }
        initializer {
            SettingsViewModel(
                prefs = container.prefs,
                keyStore = container.apiKeyStore,
                generator = container.chapterGenerator,
                wordDao = container.db.wordDao(),
            )
        }
        initializer {
            ReaderViewModel(
                chapterDao = container.db.chapterDao(),
                wordDao = container.db.wordDao(),
                schedule = container.scheduleService,
                prefs = container.prefs,
                generator = container.chapterGenerator,
                aiConfig = container::aiConfig,
            )
        }
    }

    val appearance by container.prefs.appearance.collectAsStateWithLifecycle()

    ImmerlexTheme(appearance) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        Scaffold(bottomBar = {
            NavigationBar {
                Destinations.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        }) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.BANK.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destinations.BANK.route) {
                    val vm: WordBankViewModel = viewModel(factory = appViewModelFactory)
                    WordBankScreen(vm)
                }
                composable(Destinations.READER.route) {
                    val vm: ReaderViewModel = viewModel(factory = appViewModelFactory)
                    ReaderScreen(vm, onOpenSettings = {
                        navController.navigate(Destinations.SETTINGS.route) { launchSingleTop = true }
                    })
                }
                composable(Destinations.SETTINGS.route) {
                    val vm: SettingsViewModel = viewModel(factory = appViewModelFactory)
                    SettingsScreen(vm)
                }
            }
        }
    }
}

private enum class Destinations(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    BANK("bank", R.string.tab_bank, Icons.AutoMirrored.Filled.List),
    READER("reader", R.string.tab_reader, Icons.Filled.Create),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}
