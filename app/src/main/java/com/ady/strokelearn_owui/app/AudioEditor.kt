package com.ady.strokelearn_owui.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Record, import, play or remove the audio for one letter.
 * Used in the editor and in dataset mode, next to the drawing canvas.
 */
@Composable
fun LetterAudioSection(
    script: Script,
    letter: Letter,
    audio: AudioPlayer,
    capture: AudioCapture
) {
    val context = LocalContext.current
    // bumped after every change, to re-check what exists on disk
    var version by remember(letter.id) { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var status by remember(letter.id) { mutableStateOf<String?>(null) }

    val hasUser = remember(letter.id, version) { capture.hasUserAudio(script, letter) }
    val hasAny = remember(letter.id, version) { audio.hasAudio(script, letter) }

    val micGranted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    val askMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "Microphone ready — tap record." else "Microphone denied."
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = capture.import(script, letter, uri).fold(
            onSuccess = { version++; "Imported." },
            onFailure = { e -> "Import failed: ${e.message}" }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Audio", fontSize = 17.sp)
            if (hasAny) {
                IconButton(onClick = { audio.play(script, letter) }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Play")
                }
            }
            Spacer(Modifier.weight(1f))
            if (hasUser) {
                IconButton(onClick = {
                    capture.delete(script, letter); version++; status = "Recording removed."
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove recording")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (recording) {
                        val file = capture.stop()
                        recording = false
                        version++
                        status = if (file != null) "Recorded." else "Nothing was recorded."
                    } else if (!micGranted()) {
                        askMic.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        recording = capture.start(script, letter)
                        status = if (recording) "Recording…" else "Could not start recording."
                    }
                },
                colors = if (recording) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ) else ButtonDefaults.buttonColors(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (recording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (recording) "Stop" else "Record")
            }

            OutlinedButton(
                onClick = { pickFile.launch(arrayOf("audio/*")) },
                enabled = !recording,
                modifier = Modifier.weight(1f)
            ) { Text("Import file") }
        }

        status?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasUser) {
            Text(
                "Your recording is used instead of any bundled audio. " +
                        "It lives in app storage — back it up before reinstalling.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
