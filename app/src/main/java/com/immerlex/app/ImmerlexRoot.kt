package com.immerlex.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Scaffold
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
import com.immerlex.app.ui.AppBottomBar
import com.immerlex.app.ui.ImmerlexTheme
import com.immerlex.app.ui.NavSlot
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
import com.immerlex.feature.settings.KeyboardScreen
import com.immerlex.feature.settings.KeyboardViewModel
import com.immerlex.feature.settings.MaterialViewModel
import com.immerlex.feature.settings.SettingsScreen
import com.immerlex.feature.settings.SettingsViewModel
import com.immerlex.feature.stats.StatsScreen
import com.immerlex.feature.stats.StatsViewModel
import com.immerlex.feature.tricky.TrickyScreen
import com.immerlex.feature.tricky.TrickyViewModel
import com.immerlex.feature.wordbank.WordBankViewModel

private const val ROUTE_ABOUT = "about"
private const val ROUTE_BIBLE = "bible"
private const val ROUTE_ROOM = "room"
private const val ROUTE_TRICKY = "tricky"
private const val ROUTE_KEYBOARD = "keyboard"
private const val ROUTE_QUIZ = "quiz/{mode}"

private fun quizRoute(mode: QuizMode): String = "quiz/${mode.name}"

@Composable
fun ImmerlexRoot() {
    val context = LocalContext.current
    val container = (context.applicationContext as ImmerlexApplication).container
    val appViewModelFactory = viewModelFactory {
        initializer {
            WordBankViewModel(
                WordBankRepository(
                    container.db,
                    container.dictionary,
                    container.importRepository,
                    container.materialRepository,
                ),
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
            MaterialViewModel(
                material = container.materialRepository,
                sync = container.materialSync,
                prefs = container.prefs,
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
                logDao = container.db.reviewLogDao(),
                positionDao = container.db.readingPositionDao(),
                schedule = container.scheduleService,
                trickyRepository = container.trickyRepository,
                prefs = container.prefs,
                generator = container.chapterGenerator,
                aiConfig = container::aiConfig,
                notifier = container.generationNotifier,
                boundary = container.dayBoundary,
            )
        }
        initializer {
            HomeViewModel(
                dailyLoop = container.dailyLoopService,
                schedule = container.scheduleService,
                quiz = container.quizService,
                resume = container.quizResumeStore,
                chapterDao = container.db.chapterDao(),
                prefs = container.prefs,
                roommate = container.roommateService,
            )
        }
        initializer {
            QuizViewModel(
                quiz = container.quizService,
                prefs = container.prefs,
                resume = container.quizResumeStore,
                tricky = container.trickyRepository,
                runs = container.quizRunStore,
            )
        }
        initializer {
            RoommateViewModel(container.roommateService)
        }
        initializer {
            TrickyViewModel(container.trickyRepository)
        }
        initializer {
            KeyboardViewModel(container.prefs)
        }
        initializer {
            StatsViewModel(
                stats = container.statsService,
                runs = container.quizRunStore,
            )
        }
    }

    val appearance by container.prefs.appearance.collectAsStateWithLifecycle()
    val keyMap by container.prefs.keyBindings.collectAsStateWithLifecycle()

    ImmerlexTheme(appearance) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        // 今日页的"开始今日阅读"和底栏走的是同一套换栏逻辑，否则两处会一个存状态一个不存。
        // 不存也不还原状态：一还原就会把舍友页、易混词页这类挂在某栏上面的子页一起带回来，
        // 点主页却回到子页。各页的数据本来就从库里现读，换栏时没什么可丢的。
        val goToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }

        Scaffold(bottomBar = {
            AppBottomBar(
                slots = Destinations.entries.map { NavSlot(it.route, it.labelRes, it.icon) },
                currentRoute = currentRoute,
                onSelect = { slot -> goToTab(slot.route) },
            )
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
                        onStartReading = { goToTab(Destinations.READER.route) },
                        onStartQuiz = {
                            navController.navigate(quizRoute(QuizMode.DUE)) { launchSingleTop = true }
                        },
                        onStartEarly = {
                            navController.navigate(quizRoute(QuizMode.EARLY)) { launchSingleTop = true }
                        },
                        onStartTricky = {
                            navController.navigate(quizRoute(QuizMode.TRICKY)) { launchSingleTop = true }
                        },
                        onOpenRoom = {
                            navController.navigate(ROUTE_ROOM) { launchSingleTop = true }
                        },
                    )
                }
                composable(Destinations.BANK.route) {
                    val vm: WordBankViewModel = viewModel(factory = appViewModelFactory)
                    WordBankScreen(vm, onOpenTricky = {
                        navController.navigate(ROUTE_TRICKY) { launchSingleTop = true }
                    })
                }
                composable(Destinations.READER.route) {
                    val vm: ReaderViewModel = viewModel(factory = appViewModelFactory)
                    ReaderScreen(vm, keyMap, onOpenSettings = {
                        navController.navigate(Destinations.SETTINGS.route) { launchSingleTop = true }
                    })
                }
                composable(Destinations.STATS.route) {
                    val vm: StatsViewModel = viewModel(factory = appViewModelFactory)
                    StatsScreen(
                        vm,
                        onTakeRevenge = {
                            navController.navigate(quizRoute(QuizMode.NEMESIS)) { launchSingleTop = true }
                        },
                    )
                }
                composable(Destinations.SETTINGS.route) {
                    val vm: SettingsViewModel = viewModel(factory = appViewModelFactory)
                    val material: MaterialViewModel = viewModel(factory = appViewModelFactory)
                    SettingsScreen(
                        vm,
                        material,
                        onOpenBible = { navController.navigate(ROUTE_BIBLE) { launchSingleTop = true } },
                        onOpenKeyboard = { navController.navigate(ROUTE_KEYBOARD) { launchSingleTop = true } },
                        onOpenAbout = { navController.navigate(ROUTE_ABOUT) { launchSingleTop = true } },
                    )
                }
                composable(ROUTE_QUIZ) { entry ->
                    val mode = QuizMode.entries.firstOrNull {
                        it.name == entry.arguments?.getString("mode")
                    } ?: QuizMode.DUE
                    val vm: QuizViewModel = viewModel(factory = appViewModelFactory)
                    LaunchedEffect(mode) { vm.start(mode) }
                    QuizScreen(vm, keyMap, onExit = { navController.popBackStack() })
                }
                composable(ROUTE_BIBLE) {
                    val vm: BibleViewModel = viewModel(factory = appViewModelFactory)
                    BibleScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_ROOM) {
                    val vm: RoommateViewModel = viewModel(factory = appViewModelFactory)
                    RoommateScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_TRICKY) {
                    val vm: TrickyViewModel = viewModel(factory = appViewModelFactory)
                    TrickyScreen(vm, onBack = { navController.popBackStack() })
                }
                composable(ROUTE_KEYBOARD) {
                    val vm: KeyboardViewModel = viewModel(factory = appViewModelFactory)
                    KeyboardScreen(vm, onBack = { navController.popBackStack() })
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
    // 底栏按这个顺序摆：中间一格（HOME）画成抬起的主色圆，左右各两格。
    BANK("bank", R.string.tab_bank, Icons.AutoMirrored.Filled.List),
    READER("reader", R.string.tab_reader, Icons.Filled.Create),
    HOME("home", R.string.tab_home, Icons.Filled.Home),
    STATS("stats", R.string.tab_stats, Icons.Filled.Star),
    SETTINGS("settings", R.string.tab_settings, Icons.Filled.Settings),
}
