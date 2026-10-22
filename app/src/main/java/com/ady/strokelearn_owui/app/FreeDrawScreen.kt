package com.ady.strokelearn_owui.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Practice without stored strokes: the rendered font is shown clearly and the
 * user traces over it freehand. Nothing is scored, since there is nothing to
 * score against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeDrawScreen(
    state: AppState,
    audio: AudioPlayer,
    letterId: String?,
    onBack: () -> Unit,
    onCreate: (String) -> Unit
) {
    val script = state.current ?: return
    val letter = script.findLetter(letterId) ?: return

    val drawn = remember(letter.id) { mutableStateListOf<Stroke>() }
    val typeface = rememberScriptTypeface(script.font)
    val hasAudio = remember(letter.id) { audio.hasAudio(script, letter) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Free draw · ${letter.name}") },
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
                "Trace over the shape. There are no stored strokes for this " +
                        "letter, so nothing is scored — this is freehand practice.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DrawingCanvas(
                strokes = drawn,
                onStrokeFinished = { drawn.add(it) },
                guideGlyph = letter.name,
                guideTypeface = typeface,
                strongGuide = true
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { drawn.removeAt(drawn.lastIndex) },
                    enabled = drawn.isNotEmpty()
                ) { Text("Undo") }
                OutlinedButton(
                    onClick = { drawn.clear() },
                    enabled = drawn.isNotEmpty()
                ) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Text("${drawn.size} strokes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (letter.romanisation.isNotBlank() || letter.pronunciationNote.isNotBlank()) {
                HorizontalDivider()
                Text("${letter.name}  ${letter.romanisation}", fontSize = 19.sp)
                if (letter.ipa.isNotBlank()) {
                    Text(
                        letter.ipa,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (letter.pronunciationNote.isNotBlank()) {
                    Text(letter.pronunciationNote, fontSize = 16.sp)
                }
            }

            HorizontalDivider()
            Text(
                "Happy with how you write this letter? Record the strokes once " +
                        "and the app can guide and score you on it from then on.",
                fontSize = 15.sp
            )
            Button(
                onClick = { onCreate(letter.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add strokes in creator mode") }
        }
    }
}