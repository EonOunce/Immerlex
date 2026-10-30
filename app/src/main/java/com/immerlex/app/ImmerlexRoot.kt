package com.immerlex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.immerlex.core.quiz.QuizMode
import com.immerlex.feature.home.HomeScreen
import com.immerlex.feature.home.HomeViewModel
import com.immerlex.feature.quiz.QuizScreen
import com.immerlex.feature.quiz.QuizViewModel
import com.immerlex.feature.roommate.RoommateScreen
import com.immerlex.feature.roommate.RoommateViewModel
import com.immerlex.feature.wordbank.WordBankRepository
import com.immerlex.feature.wordbank.WordBankScreen
import com.immerlex.feature.reader.ReaderScreen
import com.immerlex.feature.reader.ReaderViewModel
import com.immerlex.feature.settings.AboutScreen
import com.immerlex.feature.settings.BibleScreen
import com.immerlex.feature.settings.BibleViewModel
import com.immerlex.feature.settings.SettingsScreen
import com.immerlex.feature.settings.SettingsViewModel
import com.immerlex.feature.wordbank.WordBankViewModel

private const val ROUTE_ABOUT = "about"
private const val ROUTE_BIBLE = "bible"
private const val ROUTE_ROOM = "room"
private const val ROUTE_QUIZ = "quiz/{mode}"

private fun quizRoute(mode: QuizMode): String = "quiz/${mode.name}"

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
                notifier = container.generationNotifier,
                reminder = container.reminder,
                backup = container.backupService,
            )
        }
        initializer {
            BibleViewModel(
                bible = container.storyBible,
                expander = container.bibleExpander,
                prefs = container.prefs,
                aiConfig = container::aiConfig,
            )
        }
        initializer {
            ReaderViewModel(
                chapterDao = container.db.chapterDao(),
                wordDao = container.db.wordDao(),
                positionDao = container.db.readingPositionDao(),
                schedule = container.scheduleService,
                trickyRepository = container.trickyRepository,
                prefs = container.prefs,
                generator = container.chapterGenerator,
                aiConfig = container::aiConfig,
                notifier = container.generationNotifier,
            )
        }
        initializer {
            HomeViewModel(
                dailyLoop = container.dailyLoopService,
                schedule = container.scheduleService,
                quiz = container.quizService,
                chapterDao = container.db.chapterDao(),
                prefs = container.prefs,
                roommate = container.roommateService,
            )
        }
        initializer {
            QuizViewModel(
                quiz = container.quizService,
                prefs = container.prefs,
            )
        }
        initializer {
            RoommateViewModel(container.roommateService)
        }
    }

    val appearance by container.prefs.appearance.collectAsStateWithLifecycle()

    ImmerlexTheme(appearance) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        // 今日页的"开始今日阅读"和底栏走的是同一套换栏逻辑，否则两处会一个存状态一个不存。
        val goToTab: (Destinations) -> Unit = { dest ->
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(bottomBar = {
            NavigationBar {
                Destinations.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { goToTab(dest) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        }) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destinations.HOME.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destinations.HOME.route) {
                    val vm: HomeViewModel = viewModel(factory = appViewModelFactory)
                    HomeScreen(
                        vm,
                        onStartReading = { goToTab(Destinations.READER) },
                        onStartQuiz = {
                            navController.navigate(quizRoute(QuizMode.DUE)) { launchSingleTop = true }
                        },
                        onStartEarly = {
                            navController.navigate(quizRoute(QuizMode.EARLY)) { launchSingleTop = true }
                        },
                        onOpenRoom = {
                            navController.navigate(ROUTE_ROOM) { launchSingleTop = true }
                        },
                    )
                }
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
                    SettingsScreen(
                        vm,
                        onOpenBible = { navController.navigate(ROUTE_BIBLE) { launchSingleTop = true } },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) { launchSingleTop = true } },
                    )
                }
                composable(ROUTE_QUIZ) { entry ->
                    val mode = QuizMode.entries.firstOrNull {
                        it.name == entry.arguments?.getString("mode")
                    } ?: QuizMode.DUE
                    val vm: QuizViewModel = viewModel(factory = appViewModelFactory)
                    LaunchedEffect(mode) { vm.start(mode) }
                    QuizScreen(vm, onExit = { navController.popBackStack() })
                }
                composable(ROUTE_BIBLE) {
                    val vm: BibleViewModel = viewModel(factory = appViewModelFactory)
                    BibleScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_ROOM) {
                    val vm: RoommateViewModel = viewModel(factory = appViewModelFactory)
                    RoommateScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
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
    HOME("home", R.string.tab_home, Icons.Filled.Home),
    BANK("bank", R.string.tab_bank, Icons.AutoMirrored.Filled.List),
    READER("reader", R.string.tab_reader, Icons.Filled.Create),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}
