package com.ady.strokelearn_owui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Good = Color(0xFF2E7D32)
private val Okay = Color(0xFFE65100)
private val Bad = Color(0xFFB00020)
private val Learning = Color(0xFFFBC02D)

private fun scoreColor(percent: Int) = when {
    percent >= 75 -> Good
    percent >= 50 -> Okay
    else -> Bad
}

/* ==================== session model ==================== */

enum class SessionKind { TRAINING, TEST, EXAM }

/** What the runner needs to know, independent of which kind started it. */
data class SessionConfig(
    val kind: SessionKind,
    val letters: List<Letter>,
    val repsPerGlyph: Int,
    val scoringEnabled: Boolean = true
) {
    val showGuideGlyph get() = kind == SessionKind.TRAINING
    val traceStrokes get() = kind == SessionKind.TRAINING
}

/** Session state lives here so it survives navigation between setup and runner. */
class SessionHolder {
    var config by mutableStateOf<SessionConfig?>(null)
    var results = mutableStateListOf<SessionResult>()

    fun start(config: SessionConfig) {
        this.config = config
        results.clear()
    }
}

data class SessionResult(
    val letter: Letter,
    val score: Float,
    val drawn: List<Stroke> = emptyList()
)

/** Builds the shuffled queue: each glyph appears up to [repsPerGlyph] times. */
private fun buildQueue(config: SessionConfig): List<Letter> =
    config.letters
        .flatMap { letter -> List(config.repsPerGlyph) { letter } }
        .shuffled()

/* ==================== chooser ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionChooserScreen(
    onBack: () -> Unit,
    onPick: (SessionKind) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start a session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SessionOption(
                title = "Training session",
                body = "Trace each glyph with the guide showing. Glyphs you haven't " +
                        "learned yet, repeated as often as you choose.",
                onClick = { onPick(SessionKind.TRAINING) }
            )
            SessionOption(
                title = "Test session",
                body = "Write from memory, no guide. Only glyphs you have already " +
                        "trained on.",
                onClick = { onPick(SessionKind.TEST) }
            )
            SessionOption(
                title = "Final exam",
                body = "Every glyph in the script, no guides at all.",
                onClick = { onPick(SessionKind.EXAM) }
            )
        }
    }
}

@Composable
private fun SessionOption(title: String, body: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ==================== setup ==================== */

/**
 * Picks how many glyphs and which ones. The pool depends on the session kind:
 * training excludes learned glyphs, test uses only encountered ones, the exam
 * uses everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    state: AppState,
    progress: ProgressStore,
    holder: SessionHolder,
    kind: SessionKind,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    val script = state.current ?: return

    val pool = remember(script.id, kind) {
        when (kind) {
            SessionKind.TRAINING -> progress.notLearned(script)
            SessionKind.TEST -> progress.encountered(script)
                .filter { progress.of(script.id, it.id).status != LearningStatus.LEARNED }
            SessionKind.EXAM -> script.allLetters
        }.filter { it.strokes.isNotEmpty() }
    }

    var count by remember { mutableIntStateOf(minOf(5, pool.size)) }
    var selected by remember { mutableStateOf(pool.shuffled().take(count).map { it.id }.toSet()) }
    var reps by remember { mutableIntStateOf(3) }
    var picking by remember { mutableStateOf(false) }
    var scoring by remember { mutableStateOf(false) }

    // reshuffle when the count changes, unless the user is hand-picking
    fun reshuffle(newCount: Int) {
        count = newCount
        selected = pool.shuffled().take(newCount).map { it.id }.toSet()
    }

    if (pool.isEmpty()) {
        EmptyPool(kind, onBack)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(kind.title()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Button(
                    onClick = {
                        val letters = pool.filter { it.id in selected }
                        holder.start(
                            SessionConfig(
                                kind = kind,
                                letters = letters,
                                repsPerGlyph = if (kind == SessionKind.TRAINING) reps else 1,
                                scoringEnabled = kind == SessionKind.TRAINING || scoring
                            )
                        )
                        onStart()
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Start — ${selected.size} glyphs") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (kind == SessionKind.EXAM) {
                ExamWarning()
                Spacer(Modifier.height(8.dp))
            }

            Text("How many glyphs?", fontSize = 17.sp)
            Text(
                "$count of ${pool.size} available",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = count.toFloat(),
                onValueChange = { reshuffle(it.roundToInt().coerceAtLeast(1)) },
                valueRange = 1f..pool.size.toFloat(),
                steps = (pool.size - 2).coerceAtLeast(0)
            )

            if (kind == SessionKind.TRAINING) {
                Spacer(Modifier.height(8.dp))
                Text("Repetitions per glyph: $reps", fontSize = 17.sp)
                Slider(
                    value = reps.toFloat(),
                    onValueChange = { reps = it.roundToInt().coerceAtLeast(1) },
                    valueRange = 1f..10f,
                    steps = 8
                )
                Text(
                    "${selected.size * reps} prompts in total",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (kind != SessionKind.TRAINING) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { scoring = !scoring },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Score each answer", fontSize = 17.sp)
                        Text(
                            "Compares your drawing to the stored strokes. " +
                                    "Off by default — the comparison is rough.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = scoring, onCheckedChange = { scoring = it })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selected glyphs", fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { reshuffle(count) }) { Text("Shuffle") }
                TextButton(onClick = { picking = true }) { Text("Choose") }
            }

            FlowGlyphs(pool.filter { it.id in selected })
        }
    }

    if (picking) {
        GlyphPickerDialog(
            pool = pool,
            selected = selected,
            onDismiss = { picking = false },
            onConfirm = {
                selected = it
                count = it.size
                picking = false
            }
        )
    }
}

@Composable
private fun ExamWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Exam conditions", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "Every glyph can appear, whether or not you've trained on it. " +
                        "No guide glyph and no stroke hints — only the box showing where " +
                        "the character sits.",
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun EmptyPool(kind: SessionKind, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (kind) {
                    SessionKind.TEST ->
                        "No glyphs to test yet.\nRun a training session first."
                    SessionKind.TRAINING ->
                        "Nothing left to train — every glyph is marked learned."
                    SessionKind.EXAM ->
                        "This script has no glyphs with strokes yet."
                },
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

/** Compact read-only display of the chosen glyphs. */
@Composable
private fun FlowGlyphs(letters: List<Letter>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        letters.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { letter ->
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(letter.name, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Grid of the whole pool, tap to toggle. */
@Composable
private fun GlyphPickerDialog(
    pool: List<Letter>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var working by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose glyphs (${working.size})") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(pool.size) { index ->
                    val letter = pool[index]
                    val isOn = letter.id in working
                    Surface(
                        tonalElevation = if (isOn) 6.dp else 1.dp,
                        shape = MaterialTheme.shapes.small,
                        color = if (isOn) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                working = if (isOn) working - letter.id else working + letter.id
                            }
                    ) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(letter.name, fontSize = 22.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(working) },
                enabled = working.isNotEmpty()
            ) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun SessionKind.title() = when (this) {
    SessionKind.TRAINING -> "Training session"
    SessionKind.TEST -> "Test session"
    SessionKind.EXAM -> "Final exam"
}

/* ==================== runner ==================== */

/**
 * Works through the shuffled queue. Training traces stroke by stroke with the
 * guide showing; test and exam ask for the whole glyph at once, with only the
 * bounding box as a placement hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionRunnerScreen(
    state: AppState,
    progress: ProgressStore,
    settings: AppSettings,
    audio: AudioPlayer,
    holder: SessionHolder,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val script = state.current
    val config = holder.config

    // the session can be lost if the process is restarted — leave rather than
    // render an empty screen with no way out
    if (script == null || config == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val queue = remember(config) { buildQueue(config) }

    var index by remember { mutableIntStateOf(0) }
    val drawn = remember { mutableStateListOf<Stroke>() }
    var step by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var comparisons by remember { mutableStateOf<List<StrokeComparison>>(emptyList()) }

    if (index >= queue.size) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    val letter = queue[index]
    val training = config.kind == SessionKind.TRAINING
    val hasAudio = remember(letter.id) { audio.hasAudio(script, letter) }

    val inkColor = MaterialTheme.colorScheme.onSurface
    val ghostColor = inkColor.copy(alpha = 0.28f)
    val boxColor = inkColor.copy(alpha = 0.22f)

    // the sound is part of the prompt, so it plays when the glyph appears and
    // again once the drawing is done
    LaunchedEffect(index) {
        if (settings.autoPlayAudio && hasAudio) audio.play(script, letter)
    }

    val glyphDone = checked || (training && step >= letter.strokes.size)
    LaunchedEffect(index, glyphDone) {
        if (glyphDone && settings.autoPlayAudio && hasAudio) audio.play(script, letter)
    }

    fun commitAndAdvance() {
        val score = compareLetters(drawn, letter.strokes)
        holder.results.add(SessionResult(letter, score, drawn.toList()))
        progress.recordEncounter(script.id, letter.id)
        if (config.scoringEnabled) progress.recordScore(script.id, letter.id, score)

        drawn.clear()
        step = 0
        checked = false
        comparisons = emptyList()
        index++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${config.kind.title()} · ${index + 1}/${queue.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Quit session")
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
            LinearProgressIndicator(
                progress = { index.toFloat() / queue.size },
                modifier = Modifier.fillMaxWidth()
            )

            val trainingDone = training && step >= letter.strokes.size

            Text(
                when {
                    training && trainingDone -> "All ${letter.strokes.size} strokes done."
                    training -> "Trace stroke ${step + 1} of ${letter.strokes.size}."
                    letter.romanisation.isNotBlank() -> "Write: ${letter.romanisation}"
                    else -> "Write the glyph."
                },
                fontSize = 17.sp
            )

            // the prompt details, shown from the start — the glyph itself is
            // still hidden, which is what the test is actually about
            if (!training) {
                if (letter.ipa.isNotBlank()) {
                    Text(
                        letter.ipa,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (letter.pronunciationNote.isNotBlank()) {
                    Text("Sounds like: ${letter.pronunciationNote}", fontSize = 16.sp)
                }
//                if (letter.exampleWord.isNotBlank()) {
//                    Text(
//                        buildString {
//                            append(letter.exampleWord)
//                            if (letter.exampleHinglish.isNotBlank())
//                                append(" (${letter.exampleHinglish})")
//                            if (letter.exampleMeaning.isNotBlank())
//                                append(" — ${letter.exampleMeaning}")
//                        },
//                        fontSize = 16.sp,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
                }
//            }

            DrawingCanvas(
                // keep the raw strokes on screen when there is no coloured version
                strokes = if ((checked || trainingDone) && config.scoringEnabled)
                    emptyList() else drawn,
                enabled = !checked && !trainingDone,
                guideGlyph = if (config.showGuideGlyph && settings.showGuideGlyph)
                    letter.name else "",
                onStrokeFinished = { stroke ->
                    drawn.add(stroke)
                    if (training) {
                        step++
                        if (step >= letter.strokes.size) {
                            comparisons = compareLettersDetailed(drawn, letter.strokes)
                        }
                    }
                },
                overlay = {
                    drawBoundingBox(letter.strokes, boxColor)

                    when {
                        checked -> {
                            letter.strokes.forEach { drawStroke(it, Color(0x5500AA00), 20f) }
                            comparisons.forEach { drawComparison(it, inkColor) }
                        }
                        trainingDone -> comparisons.forEach { drawComparison(it, inkColor) }
                        config.traceStrokes && step < letter.strokes.size ->
                            drawStroke(letter.strokes[step], ghostColor, 18f, showStart = true)
                    }
                }
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { drawn.removeAt(drawn.lastIndex) },
                    enabled = drawn.isNotEmpty() && !checked && !trainingDone
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = { drawn.clear(); step = 0; comparisons = emptyList() },
                    enabled = drawn.isNotEmpty() && !checked
                ) { Text("Clear") }
                Spacer(Modifier.weight(1f))

                if (training) {
                    Button(
                        onClick = { commitAndAdvance() },
                        enabled = trainingDone
                    ) { Text("Next") }
                } else if (!checked) {
                    Button(
                        onClick = {
                            if (config.scoringEnabled) {
                                comparisons = compareLettersDetailed(drawn, letter.strokes)
                            }
                            checked = true
                        },
                        enabled = drawn.isNotEmpty()
                    ) { Text(if (config.scoringEnabled) "Check" else "Reveal") }
                } else {
                    Button(onClick = { commitAndAdvance() }) { Text("Next") }
                }
            }

            if (checked || trainingDone) {
                if (config.scoringEnabled) {
                    val percent = (compareLetters(drawn, letter.strokes) * 100).toInt()
                    Text(
                        "$percent%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(percent)
                    )
                }
                Text("${letter.name}  ${letter.romanisation}", fontSize = 19.sp)
                // ipa and the hint are already on screen for tests; training
                // hasn't shown them yet
                if (training && letter.ipa.isNotBlank()) {
                    Text(
                        letter.ipa,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (training && letter.pronunciationNote.isNotBlank()) {
                    Text(letter.pronunciationNote, fontSize = 16.sp)
                }
            }
        }
    }
}

/* ==================== summary ==================== */

/** Per-glyph scores, with the learning/learned switch for each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSummaryScreen(
    state: AppState,
    progress: ProgressStore,
    holder: SessionHolder,
    onDone: () -> Unit
) {
    val script = state.current
    if (script == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // several attempts at one glyph collapse to its best score
    // several attempts at one glyph collapse to the best-scoring one, keeping
    // the drawing that produced it
    val byLetter = remember(holder.results.size) {
        holder.results
            .groupBy { it.letter.id }
            .map { (_, results) -> results.maxBy { it.score } }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Session complete") }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Done") }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (byLetter.isEmpty()) {
                Text("No glyphs were completed.", fontSize = 16.sp)
                return@Column
            }

            val scored = holder.config?.scoringEnabled ?: true
            if (scored) {
                val average = byLetter.map { it.score }.average()
                Text(
                    "Average ${(average * 100).toInt()}%",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    "${byLetter.size} glyphs",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Mark each glyph. Learned ones are left out of future sessions.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            byLetter.forEach { result ->
                SummaryRow(script.id, result, scored, progress)
            }
        }
    }
}

@Composable
private fun SummaryRow(
    scriptId: String,
    result: SessionResult,
    showScore: Boolean,
    progress: ProgressStore
) {
    val letter = result.letter
    val status = progress.of(scriptId, letter.id).status
    val percent = (result.score * 100).toInt()

    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StrokeThumbnail(
                target = letter.strokes,
                drawn = result.drawn,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(letter.name, fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append(letter.romanisation)
                            if (letter.ipa.isNotBlank()) append("  ${letter.ipa}")
                        },
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showScore) {
                    Text(
                        "$percent%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor(percent)
                    )
                }
                StatusToggle(status) { progress.setStatus(scriptId, letter.id, it) }
            }
        }
    }
}
@Composable
private fun StatusToggle(status: LearningStatus, onChange: (LearningStatus) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusChip("Learning", Learning, status == LearningStatus.LEARNING) {
            onChange(LearningStatus.LEARNING)
        }
        StatusChip("Learned", Good, status == LearningStatus.LEARNED) {
            onChange(LearningStatus.LEARNED)
        }
    }
}

@Composable
private fun StatusChip(label: String, tint: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) tint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tint else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}