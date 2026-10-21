package com.ady.strokelearn_owui.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // these need a Context, so they are built here, not in the composable
        val repository = ScriptRepository(applicationContext)
        val settings = AppSettings(applicationContext)
        val audio = AudioPlayer(applicationContext)
        val progress = ProgressStore(applicationContext)
        val notes = NoteStore(applicationContext)

        setContent {
            StrokeLearnTheme(settings) {
                Surface {
                    StrokeLearnApp(
                        remember { AppState(repository) }, settings, audio, progress, notes
                    )
                }
            }
        }
    }
}

@Composable
private fun StrokeLearnApp(
    state: AppState,
    settings: AppSettings,
    audio: AudioPlayer,
    progress: ProgressStore,
    notes: NoteStore
) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // held here so setup, runner and summary share one session
    val session = remember { SessionHolder() }

    // The drawer's swipe gesture steals horizontal drags from the canvas,
    // so allow it only on the library screen.
    val backStackEntry by nav.currentBackStackEntryAsState()
    val onLibrary = backStackEntry?.destination?.route == "library"

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = onLibrary || drawer.isOpen,
        drawerContent = {
            ScriptDrawer(
                state = state,
                onSelect = { id ->
                    state.select(id)
                    scope.launch { drawer.close() }
                },
                onNotes = {
                    scope.launch { drawer.close() }
                    nav.navigate("notes")
                },
                onSettings = {
                    scope.launch { drawer.close() }
                    nav.navigate("settings")
                }
            )
        }
    ) {
        NavHost(nav, startDestination = "library") {

            composable("library") {
                LibraryScreen(
                    state = state,
                    progress = progress,
                    onOpenDrawer = { scope.launch { drawer.open() } },
                    onSessions = { nav.navigate("sessions") },
                    onAdd = { nav.navigate("edit") },
                    onEdit = { id -> nav.navigate("edit?id=$id") },
                    onPractice = { id -> nav.navigate("practice/$id") },
                    onTest = { id -> nav.navigate("test/$id") },
                    onDataset = { nav.navigate("dataset") }
                )
            }
            composable("sessions") {
                SessionChooserScreen(
                    onBack = { nav.popBackStack() },
                    onPick = { kind -> nav.navigate("sessions/setup/${kind.name}") }
                )
            }

            composable("sessions/setup/{kind}") { entry ->
                val kind = SessionKind.valueOf(
                    entry.arguments?.getString("kind") ?: SessionKind.TRAINING.name
                )
                SessionSetupScreen(
                    state = state,
                    progress = progress,
                    holder = session,
                    kind = kind,
                    onBack = { nav.popBackStack() },
                    onStart = { nav.navigate("sessions/run") }
                )
            }

            composable("sessions/run") {
                SessionRunnerScreen(
                    state = state,
                    progress = progress,
                    settings = settings,
                    audio = audio,
                    holder = session,
                    onBack = { nav.popBackStack("library", inclusive = false) },
                    onFinished = {
                        nav.navigate("sessions/summary") {
                            popUpTo("library")
                        }
                    }
                )
            }

            composable("sessions/summary") {
                SessionSummaryScreen(
                    state = state,
                    progress = progress,
                    holder = session,
                    onDone = { nav.popBackStack("library", inclusive = false) }
                )
            }


            composable("edit") {
                EditLetterScreen(state, null) { nav.popBackStack() }
            }

            composable(
                "edit?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })
            ) { entry ->
                EditLetterScreen(state, entry.arguments?.getString("id")) { nav.popBackStack() }
            }

            composable("practice/{id}") { entry ->
                PracticeScreen(
                    state = state,
                    letterId = entry.arguments?.getString("id"),
                    settings = settings,
                    audio = audio,
                    onBack = { nav.popBackStack() }
                )
            }

            composable("test/{id}") { entry ->
                TestScreen(
                    state = state,
                    letterId = entry.arguments?.getString("id"),
                    settings = settings,
                    audio = audio,
                    onBack = { nav.popBackStack() }
                )
            }

            composable("dataset") {
                DatasetStartScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onContinue = { nav.navigate("dataset/trace") }
                )
            }

            composable("dataset/trace") {
                DatasetScreen(
                    state = state,
                    onBack = { nav.popBackStack() },
                    onExport = { nav.navigate("export") }
                )
            }

            composable("export") {
                ExportScreen(state) { nav.popBackStack() }
            }

            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    progress = progress,
                    state = state,
                    onBack = { nav.popBackStack() },
                    onAbout = { nav.navigate("about") }
                )
            }

            composable("about") {
                AboutScreen { nav.popBackStack() }
            }

            composable("notes") {
                NotesScreen(
                    store = notes,
                    onBack = { nav.popBackStack() },
                    onEdit = { id ->
                        if (id == null) nav.navigate("notes/edit")
                        else nav.navigate("notes/edit?id=$id")
                    },
                    onView = { id -> nav.navigate("notes/view/$id") }
                )
            }

            composable("notes/view/{id}") { entry ->
                NoteViewScreen(
                    store = notes,
                    noteId = entry.arguments?.getString("id"),
                    onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate("notes/edit?id=$id") }
                )
            }

            composable("notes/edit") {
                NoteEditScreen(notes, null) { nav.popBackStack() }
            }

            composable(
                "notes/edit?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                })
            ) { entry ->
                NoteEditScreen(notes, entry.arguments?.getString("id")) { nav.popBackStack() }
            }
        }
    }
}