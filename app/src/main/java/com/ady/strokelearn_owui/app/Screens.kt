package com.ady.strokelearn_owui.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow

private val Good = Color(0xFF2E7D32)
private val Okay = Color(0xFFE65100)
private val Bad = Color(0xFFB00020)

private fun scoreColor(percent: Int) = when {
    percent >= 75 -> Good
    percent >= 50 -> Okay
    else -> Bad
}

/* ==================== drawer ==================== */

/** The script list shown in the navigation drawer. */
@Composable
fun ScriptDrawer(
    state: AppState,
    onSelect: (String) -> Unit,
    onNotes: () -> Unit,
    onSettings: () -> Unit
) {
    ModalDrawerSheet {
        Text(
            "Scripts",
            Modifier.padding(16.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider()
        state.scripts.forEach { script ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(script.name)
                        Text(
                            "${script.allLetters.size} letters",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                selected = script.id == state.current?.id,
                onClick = { onSelect(script.id) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Notes") },
            icon = { Icon(Icons.Default.Notes, contentDescription = null) },
            selected = false,
            onClick = onNotes,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/* ==================== library ==================== */
/**
 * One scrollable grid of all letters, with a header per group. Skipped
 * [Letter.order] numbers leave blank cells, so gaps group letters visually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: AppState,
    progress: ProgressStore,
    onOpenDrawer: () -> Unit,
    onSessions: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onPractice: (String) -> Unit,
    onTest: (String) -> Unit,
    onDataset: () -> Unit
) {
    val script = state.current
    // hoisted so the scroll position survives navigating away and back
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(script?.name ?: "StrokeLearn") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Scripts")
                    }
                },
                actions = {
                    IconButton(onClick = onDataset) {
                        Icon(Icons.Default.Brush, contentDescription = "Dataset mode")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add letter")
                }
                ExtendedFloatingActionButton(
                    onClick = onSessions,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Session") }
                )
            }
        }
    ) { padding ->
        if (script == null || script.groups.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text(
                    "No script loaded.\nPut a .json file in assets/scripts/",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            script.groups.forEach { group ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        group.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                val cells = gridCells(group.letters)
                items(cells.size) { index ->
                    val letter = cells[index]
                    if (letter == null) Spacer(Modifier.aspectRatio(1f))
                    else LetterTile(
                        letter = letter,
                        progress = progress.of(script.id, letter.id),
                        onClick = { onPractice(letter.id) },
                        onTest = { onTest(letter.id) },
                        onEdit = { onEdit(letter.id) },
                        onDelete = { state.deleteLetter(letter.id) }
                    )
                }
            }
        }
    }
}

/** Expands a letter list into grid cells, with nulls where order numbers are skipped. */
private fun gridCells(letters: List<Letter>): List<Letter?> {
    if (letters.isEmpty()) return emptyList()
    val sorted = letters.sortedBy { it.order }
    val byOrder = sorted.associateBy { it.order }
    val first = sorted.first().order
    val last = sorted.last().order
    // a nonsense range (unset orders, or a huge gap) falls back to a plain list
    return if (last - first in 0..200) (first..last).map { byOrder[it] } else sorted
}

/** New is hollow, learning amber, learned green. */
@Composable
private fun StatusDot(status: LearningStatus) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = when (status) {
        LearningStatus.NEW -> Color.Transparent
        LearningStatus.LEARNING -> Color(0xFFFBC02D)
        LearningStatus.LEARNED -> Good
    }
    Box(
        Modifier
            .size(10.dp)
            .border(1.dp, outline, CircleShape)
            .background(fill, CircleShape)
    )
}
@Composable
private fun LetterTile(
    letter: Letter,
    progress: LetterProgress,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(letter.name, fontSize = 34.sp)
                if (letter.romanisation.isNotBlank()) {
                    Text(
                        letter.romanisation,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (letter.ipa.isNotBlank()) {
                    Text(
                        letter.ipa,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (letter.strokes.isEmpty()) {
                    Text("no strokes", fontSize = 11.sp, color = Bad)
                }
            }

            // order id, for tracking while authoring
            Text(
                "#${letter.order}",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 5.dp, top = 3.dp)
            )

            // status dot on the left edge, clear of the IPA text below
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp)
                    .wrapContentSize(Alignment.CenterStart),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                StatusDot(progress.status)
                if (progress.encounters > 0) {
                    Text(
                        "${progress.encounters}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.align(Alignment.TopEnd).size(44.dp)
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = Color.Gray,
                modifier = Modifier.size(26.dp)
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Test", fontSize = 16.sp) },
                onClick = { showMenu = false; onTest() }
            )
            DropdownMenuItem(
                text = { Text("Edit", fontSize = 16.sp) },
                onClick = { showMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Bad, fontSize = 16.sp) },
                onClick = { showMenu = false; confirmDelete = true }
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${letter.name}?") },
            text = { Text("The letter and its strokes will be removed from this script.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = Bad)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/* ==================== edit ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLetterScreen(state: AppState, letterId: String?, onDone: () -> Unit) {
    val script = state.current ?: return
    val existing = script.findLetter(letterId)
    val existingGroupId = script.groups
        .firstOrNull { g -> g.letters.any { it.id == letterId } }?.id

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var romanisation by remember { mutableStateOf(existing?.romanisation ?: "") }
    var ipa by remember { mutableStateOf(existing?.ipa ?: "") }
    var pronunciationNote by remember { mutableStateOf(existing?.pronunciationNote ?: "") }

    // order numbers already in use, excluding this letter's own
    val takenOrders = remember(script, letterId) {
        script.allLetters.filter { it.id != letterId }.map { it.order }.toSet()
    }
    // a new letter gets the next free number
    var order by remember {
        mutableStateOf(
            existing?.order?.toString()
                ?: ((script.allLetters.maxOfOrNull { it.order } ?: 0) + 1).toString()
        )
    }
    val orderValue = order.toIntOrNull()
    val orderClash = orderValue != null && orderValue in takenOrders

    // id is order + romanisation; order is unique, so the id is too
    val generatedId = if (orderValue == null) "" else buildString {
        append("%02d".format(orderValue))
        append("_")
        append(romanisation.trim().ifBlank { "x" }.replace(" ", "_"))
    }

    var exampleWord by remember { mutableStateOf(existing?.exampleWord ?: "") }
    var exampleHinglish by remember { mutableStateOf(existing?.exampleHinglish ?: "") }
    var exampleMeaning by remember { mutableStateOf(existing?.exampleMeaning ?: "") }
    var groupId by remember {
        mutableStateOf(existingGroupId ?: script.groups.firstOrNull()?.id ?: "")
    }
    val strokes = remember {
        mutableStateListOf<Stroke>().apply { existing?.let { addAll(it.strokes) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New letter" else "Edit letter") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank() && strokes.isNotEmpty() && !orderClash
                                && orderValue != null,
                        onClick = {
                            state.upsertLetter(
                                groupId,
                                Letter(
                                    id = existing?.id ?: generatedId,
                                    order = orderValue ?: 0,
                                    name = name.trim(),
                                    romanisation = romanisation.trim(),
                                    ipa = ipa.trim(),
                                    pronunciationNote = pronunciationNote.trim(),
                                    exampleWord = exampleWord.trim(),
                                    exampleHinglish = exampleHinglish.trim(),
                                    exampleMeaning = exampleMeaning.trim(),
                                    audio = existing?.audio,
                                    strokes = strokes.toList()
                                )
                            )
                            onDone()
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GroupPicker(script.groups, groupId) { groupId = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Letter") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = order,
                    onValueChange = { order = it.filter(Char::isDigit) },
                    label = { Text("Order") },
                    singleLine = true,
                    isError = orderClash || orderValue == null,
                    supportingText = {
                        if (orderClash) Text("In use", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.width(120.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = romanisation, onValueChange = { romanisation = it },
                    label = { Text("Romanisation") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ipa, onValueChange = { ipa = it },
                    label = { Text("IPA") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (existing == null && generatedId.isNotBlank()) {
                Text(
                    "ID: $generatedId",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = pronunciationNote, onValueChange = { pronunciationNote = it },
                label = { Text("Pronunciation hint") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = exampleWord, onValueChange = { exampleWord = it },
                label = { Text("Example word") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = exampleHinglish, onValueChange = { exampleHinglish = it },
                    label = { Text("Transliteration") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = exampleMeaning, onValueChange = { exampleMeaning = it },
                    label = { Text("Meaning") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "Draw each stroke in writing order. Lift your finger between strokes.",
                fontSize = 13.sp, color = Color.Gray
            )
            DrawingCanvas(
                strokes = strokes,
                onStrokeFinished = { strokes.add(it) },
                guideGlyph = name
            )
            StrokeButtons(strokes)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(groups: List<LetterGroup>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = groups.firstOrNull { it.id == selected }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = { onSelect(group.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StrokeButtons(strokes: MutableList<Stroke>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { strokes.removeAt(strokes.lastIndex) },
            enabled = strokes.isNotEmpty()
        ) { Text("Undo") }
        OutlinedButton(
            onClick = { strokes.clear() },
            enabled = strokes.isNotEmpty()
        ) { Text("Clear") }
        Spacer(Modifier.weight(1f))
        Text("${strokes.size} strokes", color = Color.Gray)
    }
}



/* ==================== dataset mode ==================== */

/**
 * Entry point for dataset mode: either carry on with the loaded script,
 * or import a previously exported JSON to resume work on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetStartScreen(
    state: AppState,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val script = state.current
    var status by remember { mutableStateOf<String?>(null) }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read file")
        }.mapCatching { text ->
            state.importScript(text).getOrThrow()
        }.fold(
            onSuccess = { "Loaded." },
            onFailure = { e -> "Could not load: ${e.message}" }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dataset mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val traced = script?.allLetters?.count { it.strokes.isNotEmpty() } ?: 0
            val total = script?.allLetters?.size ?: 0

            Text(
                script?.name ?: "No script loaded",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text("$traced of $total letters have strokes.", color = Color.Gray)

            Button(
                onClick = onContinue,
                enabled = total > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue tracing") }

            OutlinedButton(
                onClick = { openFile.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Load JSON…") }

            status?.let { Text(it, color = Color.Gray, fontSize = 13.sp) }

            Text(
                "Loading a JSON replaces the stored copy of that script. " +
                        "Export before you reinstall — private storage is wiped on uninstall.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

/**
 * Walks through every letter in the script that has no strokes yet, showing the
 * real glyph behind the canvas to trace. Aimed at authoring a shareable JSON.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatasetScreen(state: AppState, onBack: () -> Unit, onExport: () -> Unit) {
    val script = state.current ?: return
    val queue = remember(script) {
        script.groups.flatMap { group -> group.letters.map { group.id to it } }
            .sortedBy { it.second.order }
    }
    var index by remember { mutableIntStateOf(0) }
    val strokes = remember { mutableStateListOf<Stroke>() }

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No letters defined in this script.", color = Color.Gray)
        }
        return
    }

    val (groupId, letter) = queue[index.coerceIn(queue.indices)]

    // reload the stored strokes whenever we move to another letter
    LaunchedEffect(letter.id) {
        strokes.clear()
        strokes.addAll(script.findLetter(letter.id)?.strokes.orEmpty())
    }

    fun persist() {
        state.upsertLetter(groupId, letter.copy(strokes = strokes.toList()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dataset · ${index + 1}/${queue.size}") },
                navigationIcon = {
                    IconButton(onClick = { persist(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { persist(); onExport() }) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "${letter.name}   ${letter.romanisation}",
                fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Trace the grey glyph, one stroke at a time, in writing order.",
                fontSize = 15.sp, color = Color.DarkGray
            )
            if (letter.pronunciationNote.isNotBlank()) {
                Text(letter.pronunciationNote, fontSize = 15.sp, color = Color.Gray)
            }
            DrawingCanvas(
                strokes = strokes,
                onStrokeFinished = { strokes.add(it) },
                guideGlyph = letter.name
            )
            StrokeButtons(strokes)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = index > 0,
                    onClick = { persist(); index-- }
                ) { Text("Previous") }

                val isLast = index == queue.size - 1
                Button(
                    onClick = {
                        persist()
                        if (isLast) onExport() else index++
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (isLast) "Save & finish" else "Save & next") }
            }
        }
    }
}

/* ==================== export ==================== */
/**
 * Writes the current script to a file the user picks, via the system file picker.
 *
 * The JSON is passed as a stream rather than in an Intent extra: intent extras go
 * through Binder IPC, which has a hard size limit that stroke data exceeds easily.
 */
@Composable
fun ExportScreen(state: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val script = state.current
    var status by remember { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            onBack()
            return@rememberLauncherForActivityResult
        }
        val json = state.exportCurrent()
        status = if (json == null) "Nothing to export."
        else runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
        }.fold(
            onSuccess = { "Saved." },
            onFailure = { e -> "Failed: ${e.message}" }
        )
    }

    // open the picker as soon as the screen appears
    LaunchedEffect(script?.id) {
        if (script != null) saveFile.launch("${script.id}.json")
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status ?: "Choose where to save…", color = Color.Gray)
            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}


@Composable
private fun SelectionContainerText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(text, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

/* ==================== practice ==================== */
/* ==================== practice ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    state: AppState,
    letterId: String?,
    settings: AppSettings,
    audio: AudioPlayer,
    onBack: () -> Unit
) {
    val script = state.current ?: return
    val letter = script.findLetter(letterId) ?: return

    var step by remember { mutableIntStateOf(0) }
    val drawn = remember { mutableStateListOf<Stroke>() }
    var comparisons by remember { mutableStateOf<List<StrokeComparison>>(emptyList()) }

    val finished = step >= letter.strokes.size
    val hasAudio = remember(letter.id) { audio.hasAudio(script, letter) }

    // read here, since the overlay lambda is a DrawScope and not composable
    val inkColor = MaterialTheme.colorScheme.onSurface
    val ghostColor = inkColor.copy(alpha = 0.28f)

    // play the letter once when the screen opens
    LaunchedEffect(letter.id) {
        if (settings.autoPlayAudio && hasAudio) audio.play(script, letter)
    }

    // colour the whole character once the last stroke lands
    LaunchedEffect(finished) {
        if (finished && drawn.size == letter.strokes.size) {
            comparisons = compareLettersDetailed(drawn, letter.strokes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice · ${letter.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasAudio) {
                        IconButton(onClick = { audio.play(script, letter) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Play sound")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (finished) "All ${letter.strokes.size} strokes done."
                else "Trace stroke ${step + 1} of ${letter.strokes.size}. " +
                        "The dot marks where to start.",
                fontSize = 15.sp
            )
            LinearProgressIndicator(
                progress = { step.toFloat() / letter.strokes.size },
                modifier = Modifier.fillMaxWidth()
            )

            DrawingCanvas(
                // once finished we draw the coloured version in the overlay instead
                strokes = if (finished) emptyList() else drawn,
                enabled = !finished,
                guideGlyph = if (settings.showGuideGlyph) letter.name else "",
                onStrokeFinished = { stroke ->
                    drawn.add(stroke)
                    step++
                },
                overlay = {
                    if (finished) {
                        comparisons.forEach { drawComparison(it, inkColor) }
                    } else {
                        drawStroke(letter.strokes[step], ghostColor, 18f, showStart = true)
                    }
                }
            )

            if (finished && comparisons.isNotEmpty()) {
                StrokeScoreList(comparisons)
            }

            if (letter.ipa.isNotBlank()) {
                Text("IPA: ${letter.ipa}", fontSize = 16.sp)
            }
            if (letter.pronunciationNote.isNotBlank()) {
                Text("Sounds like: ${letter.pronunciationNote}", fontSize = 16.sp)
            }
            if (letter.exampleWord.isNotBlank()) {
                Text(
                    buildString {
                        append("Example: ${letter.exampleWord}")
                        if (letter.exampleHinglish.isNotBlank()) append(" (${letter.exampleHinglish})")
                        if (letter.exampleMeaning.isNotBlank()) append(" — ${letter.exampleMeaning}")
                    },
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    step = 0; drawn.clear(); comparisons = emptyList()
                }) { Text("Again") }
                if (finished) Button(onClick = onBack) { Text("Finish") }
            }
        }
    }
}

/** Per-stroke percentages, so a bad score points at the stroke that caused it. */
@Composable
private fun StrokeScoreList(comparisons: List<StrokeComparison>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        comparisons.forEachIndexed { index, comparison ->
            val percent = (comparison.score * 100).toInt()
            Row {
                Text("Stroke ${index + 1}", Modifier.width(100.dp), fontSize = 15.sp)
                Text(
                    "$percent%",
                    color = scoreColor(percent),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

/* ==================== test ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    state: AppState,
    letterId: String?,
    settings: AppSettings,
    audio: AudioPlayer,
    onBack: () -> Unit
) {
    val script = state.current ?: return
    val letter = script.findLetter(letterId) ?: return

    val drawn = remember { mutableStateListOf<Stroke>() }
    var score by remember { mutableStateOf<Float?>(null) }
    var comparisons by remember { mutableStateOf<List<StrokeComparison>>(emptyList()) }

    val checked = score != null
    val hasAudio = remember(letter.id) { audio.hasAudio(script, letter) }

    val inkColor = MaterialTheme.colorScheme.onSurface

    // only after checking — hearing the letter first would give the answer away
    LaunchedEffect(checked) {
        if (checked && settings.autoPlayAudio && hasAudio) audio.play(script, letter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasAudio && checked) {
                        IconButton(onClick = { audio.play(script, letter) }) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Play sound")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                when {
                    letter.romanisation.isNotBlank() -> "Write: ${letter.romanisation}"
                    letter.exampleWord.isNotBlank() -> "Write the letter in ${letter.exampleWord}"
                    else -> "Write the letter from memory."
                },
                fontSize = 18.sp
            )

            DrawingCanvas(
                strokes = if (checked) emptyList() else drawn,
                enabled = !checked,
                guideGlyph = "",   // never show the answer in a test
                onStrokeFinished = { drawn.add(it) },
                overlay = {
                    if (checked) {
                        // the correct shape, faint green under the user's attempt
                        letter.strokes.forEach { drawStroke(it, Color(0x5500AA00), 20f) }
                        comparisons.forEach { drawComparison(it, inkColor) }
                    }
                }
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { drawn.removeAt(drawn.lastIndex) },
                    enabled = drawn.isNotEmpty() && !checked
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = {
                        drawn.clear(); score = null; comparisons = emptyList()
                    },
                    enabled = drawn.isNotEmpty()
                ) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = drawn.isNotEmpty() && !checked,
                    onClick = {
                        score = compareLetters(drawn, letter.strokes)
                        comparisons = compareLettersDetailed(drawn, letter.strokes)
                    }
                ) { Text("Check") }
            }

            score?.let { value ->
                val percent = (value * 100).toInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$percent%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(percent)
                    )
                    Text("${letter.name}  ${letter.romanisation}", fontSize = 20.sp)
                    if (drawn.size != letter.strokes.size) {
                        Text(
                            "You used ${drawn.size} strokes; the letter has ${letter.strokes.size}.",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (letter.pronunciationNote.isNotBlank()) {
                        Text(letter.pronunciationNote, fontSize = 16.sp)
                    }
                    if (comparisons.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        StrokeScoreList(comparisons)
                    }
                }
            }
        }
    }
}