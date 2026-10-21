package com.ady.strokelearn_owui.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/* ==================== data model ==================== */

/** A point in normalised space: 0.0 is the left/top canvas edge, 1.0 the right/bottom. */
@Serializable
data class Point(val x: Float, val y: Float)

/** One continuous pen-down-to-pen-up movement. */
@Serializable
data class Stroke(val points: List<Point>)

/**
 * One character.
 * [order] drives position in the library grid; skipped numbers leave blank cells,
 * so gaps can be used to group letters visually.
 */
@Serializable
data class Letter(
    val id: String,
    val order: Int = 0,
    val name: String,
    val romanisation: String = "",
    val ipa: String = "",
    val pronunciationNote: String = "",
    val exampleWord: String = "",
    val exampleHinglish: String = "",
    val exampleMeaning: String = "",
    val audio: String? = null,
    val strokes: List<Stroke> = emptyList()
)

/** A named set of letters, e.g. vowels. */
@Serializable
data class LetterGroup(
    val id: String,
    val name: String,
    val letters: List<Letter> = emptyList()
)

/**
 * A whole writing system, as stored in one JSON file.
 * [audioDir] is a folder name under the app's audio directory; letter [Letter.audio]
 * values are filenames resolved against it.
 */
@Serializable
data class Script(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val script: String = "",
    val audioDir: String = "",
    val isDefault: Boolean = false,
    val groups: List<LetterGroup> = emptyList()
) {
    val allLetters: List<Letter> get() = groups.flatMap { it.letters }

    fun findLetter(letterId: String?): Letter? =
        allLetters.firstOrNull { it.id == letterId }
}

/* ==================== storage ==================== */

/**
 * Loads scripts from two places:
 *  - assets/scripts/      bundled with the app, read-only
 *  - filesDir/scripts/    written by the app, editable
 *
 * A user copy shadows a bundled one with the same id, so editing a bundled
 * script never destroys the original.
 */
class ScriptRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val userDir = File(context.filesDir, "scripts").apply { mkdirs() }

    fun loadAll(): List<Script> {
        val bundled = loadBundled()
        val user = loadUser()
        val userIds = user.map { it.id }.toSet()
        return (user + bundled.filterNot { it.id in userIds }).sortedBy { it.name }
    }

    /** Parses exported JSON and stores it as a user script. Returns the script id. */
    fun importFromText(text: String): Result<String> = runCatching {
        val script = json.decodeFromString(Script.serializer(), text)
        require(script.id.isNotBlank()) { "Script has no id" }
        save(script)
        script.id
    }
    private fun loadBundled(): List<Script> =
        runCatching {
            context.assets.list("scripts").orEmpty()
                .filter { it.endsWith(".json") }
                .mapNotNull { fileName ->
                    runCatching {
                        context.assets.open("scripts/$fileName")
                            .bufferedReader().use { it.readText() }
                    }.mapCatching { json.decodeFromString(Script.serializer(), it) }
                        .getOrNull()
                }
        }.getOrDefault(emptyList())

    private fun loadUser(): List<Script> =
        userDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { file ->
                runCatching { json.decodeFromString(Script.serializer(), file.readText()) }
                    .getOrNull()
            }

    fun save(script: Script) {
        File(userDir, "${script.id}.json")
            .writeText(json.encodeToString(Script.serializer(), script))
    }

    /** Pretty-printed JSON for sharing or copying out of the app. */
    fun exportToText(script: Script): String =
        json.encodeToString(Script.serializer(), script.smoothed())

    /** Absolute file for a letter's audio, or null if none is set. */
    fun audioFile(script: Script, letter: Letter): File? {
        val name = letter.audio ?: return null
        val dir = File(context.filesDir, "audio/${script.audioDir}")
        return File(dir, name).takeIf { it.exists() }
    }
}

/* ==================== smoothing ==================== */

/**
 * Chaikin corner-cutting: each segment is replaced by two points at 25% and 75%
 * along it, which rounds off the jitter of finger-drawn input. Endpoints are kept
 * so the stroke still starts and ends where it was drawn.
 */
fun Stroke.smooth(passes: Int = 2): Stroke {
    var points = this.points
    repeat(passes) {
        if (points.size < 3) return@repeat
        val cut = mutableListOf(points.first())
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            cut += Point(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f)
            cut += Point(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f)
        }
        cut += points.last()
        points = cut
    }
    return Stroke(points)
}

/** Smooths every stroke in the script — applied on export, not on save. */
fun Script.smoothed(): Script = copy(
    groups = groups.map { group ->
        group.copy(letters = group.letters.map { letter ->
            letter.copy(strokes = letter.strokes.map { it.smooth() })
        })
    }
)

/* ==================== shared state ==================== */

/** Holds the loaded scripts and which one is active. */
class AppState(private val repo: ScriptRepository) {

    val scripts = mutableStateListOf<Script>()
    private val currentId = mutableStateOf<String?>(null)

    val current: Script? get() = scripts.firstOrNull { it.id == currentId.value }

    init {
        reload()
    }

    fun reload() {
        scripts.clear()
        scripts.addAll(repo.loadAll())
        if (currentId.value == null || scripts.none { it.id == currentId.value }) {
            currentId.value = (scripts.firstOrNull { it.isDefault } ?: scripts.firstOrNull())?.id
        }
    }

    fun select(scriptId: String) {
        currentId.value = scriptId
    }

    /** Loads a script from exported JSON, then makes it the active one. */
    fun importScript(text: String): Result<String> =
        repo.importFromText(text).onSuccess { id ->
            reload()
            select(id)
        }

    /** Adds or replaces a letter in the given group, then writes to disk. */
    fun upsertLetter(groupId: String, letter: Letter) {
        val script = current ?: return
        val updated = script.copy(
            groups = script.groups.map { group ->
                if (group.id != groupId) group
                else {
                    val rest = group.letters.filterNot { it.id == letter.id }
                    group.copy(letters = (rest + letter).sortedBy { it.order })
                }
            }
        )
        replaceCurrent(updated)
    }

    fun deleteLetter(letterId: String) {
        val script = current ?: return
        replaceCurrent(
            script.copy(
                groups = script.groups.map { group ->
                    group.copy(letters = group.letters.filterNot { it.id == letterId })
                }
            )
        )
    }

    fun exportCurrent(): String? = current?.let { repo.exportToText(it) }

    /** Exports any loaded script, not just the active one. */
    fun exportScript(scriptId: String): String? =
        scripts.firstOrNull { it.id == scriptId }?.let { repo.exportToText(it) }

    private fun replaceCurrent(updated: Script) {
        val index = scripts.indexOfFirst { it.id == updated.id }
        if (index >= 0) scripts[index] = updated else scripts.add(updated)
        repo.save(updated)
    }
}