package com.ady.strokelearn_owui.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

private const val FEEDBACK_EMAIL = "stroke_learn.recede676@passmail.net"   // feedback/support email
private const val AUTHOR_NAME = "Ady + Claude Opus 5 (Anthropic)"                  // ← your name

/* ==================== settings ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    progress: ProgressStore,
    state: AppState,
    onBack: () -> Unit,
    onAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader("Audio")
            SwitchRow(
                title = "Play audio automatically",
                subtitle = "Plays the letter's sound when it appears, if a recording exists.",
                checked = settings.autoPlayAudio,
                onChange = { settings.autoPlayAudio = it }
            )

            SectionHeader("Practice")
            SwitchRow(
                title = "Show guide glyph",
                subtitle = "Faint character behind the drawing area. Turned off in tests.",
                checked = settings.showGuideGlyph,
                onChange = { settings.showGuideGlyph = it }
            )

            SectionHeader("Appearance")
            ThemePicker(settings)
            FontScaleSlider(settings)

            SectionHeader("Progress")
            ProgressBackup(progress)

            SectionHeader("Script data")
            ScriptExport(state)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAbout)
                    .padding(vertical = 14.dp)
            ) {
                Text("About StrokeLearn", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp)
            Text(
                subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemePicker(settings: AppSettings) {
    Column {
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.themeMode = mode }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.themeMode == mode,
                    onClick = { settings.themeMode = mode }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    fontSize = 17.sp
                )
            }
        }
    }
}

/* ==================== about ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("StrokeLearn", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Version $version", fontSize = 16.sp)
            Text("By $AUTHOR_NAME", fontSize = 16.sp)

            Spacer(Modifier.height(8.dp))
            Text(
                "Learn a writing system by drawing it, one stroke at a time.",
                fontSize = 16.sp
            )

            Spacer(Modifier.height(8.dp))
            Text("Credits", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Hindi pronunciation audio from the Hindi programme at the " +
                        "University of Texas at Austin, used under CC BY 4.0.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { context.startActivity(feedbackIntent(version)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Send feedback") }
        }
    }
}

/* ==================== feedback ==================== */

/** Opens the user's mail app with the version and device details prefilled. */
private fun feedbackIntent(version: String): Intent {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
    val body = """
        
        
        ---
        StrokeLearn $version
        Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Sent: $timestamp
    """.trimIndent()

    val mail = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$FEEDBACK_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "StrokeLearn feedback ($version)")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return Intent.createChooser(mail, "Send feedback")
}

private fun appVersion(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
@Composable
private fun FontScaleSlider(settings: AppSettings) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Text size", fontSize = 17.sp)
        Text(
            "Preview: the quick brown fox",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = settings.fontScale,
            onValueChange = { settings.fontScale = it },
            valueRange = FONT_SCALE_MIN..FONT_SCALE_MAX,
            steps = 12
        )
    }
}

/**
 * Backup and restore of learning progress. Private storage survives an app
 * update, but not an uninstall or a "clear data", so a backup is worth keeping.
 */
@Composable
private fun ProgressBackup(progress: ProgressStore) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(progress.exportToText().toByteArray())
            }
        }.fold(
            onSuccess = { "Progress saved." },
            onFailure = { e -> "Save failed: ${e.message}" }
        )
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read file")
        }.mapCatching { progress.importFromText(it).getOrThrow() }
            .fold(
                onSuccess = { count -> "Restored $count glyphs." },
                onFailure = { e -> "Restore failed: ${e.message}" }
            )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Your progress survives app updates, but not an uninstall. " +
                    "Back it up before reinstalling or switching device.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    saveFile.launch("strokelearn-progress-$stamp.json")
                },
                modifier = Modifier.weight(1f)
            ) { Text("Back up") }
            OutlinedButton(
                onClick = { openFile.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.weight(1f)
            ) { Text("Restore") }
        }
        status?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Exports a script's letter data, including the drawn strokes. Defaults to the
 * active script; the dropdown covers the rest once more than one is loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScriptExport(state: AppState) {
    val context = LocalContext.current
    var selectedId by remember(state.current?.id) { mutableStateOf(state.current?.id) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val selected = state.scripts.firstOrNull { it.id == selectedId }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val id = selectedId
        status = if (id == null) "No script selected."
        else runCatching {
            val json = state.exportScript(id) ?: error("Script not found")
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }.fold(
            onSuccess = { "Exported." },
            onFailure = { e -> "Export failed: ${e.message}" }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Saves a script's letters and stroke data as JSON, for backup or " +
                    "for sharing with another device.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected?.name ?: "No script",
                onValueChange = {},
                readOnly = true,
                label = { Text("Script") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.scripts.forEach { script ->
                    DropdownMenuItem(
                        text = { Text("${script.name}  (${script.allLetters.size})") },
                        onClick = { selectedId = script.id; expanded = false }
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { selected?.let { saveFile.launch("${it.id}.json") } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export script") }

        status?.let {
            Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}