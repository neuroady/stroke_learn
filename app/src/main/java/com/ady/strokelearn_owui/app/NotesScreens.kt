package com.ady.strokelearn_owui.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch

/* ==================== list ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    store: NoteStore,
    state: AppState,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onView: (String) -> Unit
) {
    val context = LocalContext.current
    val script = state.current
    val visible = store.forScript(script?.id)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(script?.let { "Notes · ${it.name}" } ?: "Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        if (visible.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("No notes yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visible.size) { index ->
                val note = visible[index]
                NoteRow(
                    note = note,
                    onOpen = {
                        if (note.isBookmark) openLink(context, note.url!!) else onView(note.id)
                    },
                    onEdit = { onEdit(note.id) },
                    onDelete = { store.delete(note.id) }
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (note.isBookmark) Icons.Default.Link else Icons.Default.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(note.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (note.body.isNotBlank()) {
                    Text(
                        note.body,
                        fontSize = 14.sp,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

/** Opens a URL in whatever browser the user has. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/* ==================== edit ==================== */

/** One editor for both kinds: fill in the URL and it becomes a bookmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    store: NoteStore,
    state: AppState,
    noteId: String?,
    onDone: () -> Unit
) {
    val existing = store.notes.firstOrNull { it.id == noteId }
    val script = state.current

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    // a new note belongs to the script you are in; existing notes keep their scope
    var global by remember { mutableStateOf(existing?.scriptId == null && existing != null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New note" else "Edit note") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = {
                            store.upsert(
                                Note(
                                    id = existing?.id ?: NoteStore.newId(),
                                    title = title.trim(),
                                    body = body.trim(),
                                    url = url.trim().ifBlank { null },
                                    scriptId = if (global) null else script?.id
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link (optional)") },
                placeholder = { Text("https://…") },
                singleLine = true,
                supportingText = { Text("With a link, tapping the note opens the browser.") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().clickable { global = !global },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show in every script", fontSize = 16.sp)
                    Text(
                        if (global) "Visible everywhere."
                        else "Only in ${script?.name ?: "this script"}.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = global, onCheckedChange = { global = it })
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Note") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
/* ==================== view ==================== */

/** Read-only view of a text note. Bookmarks open in the browser instead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteViewScreen(
    store: NoteStore,
    noteId: String?,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val note = store.notes.firstOrNull { it.id == noteId } ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(note.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(note.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(note.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (note.body.isBlank()) {
                Text(
                    "This note is empty.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SelectionContainer {
                    Text(note.body, fontSize = 17.sp, lineHeight = 26.sp)
                }
            }
        }
    }
}