package com.ady.strokelearn_owui.app

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** How well the user knows a glyph. Set by them at the end of a test. */
enum class LearningStatus { NEW, LEARNING, LEARNED }

@Serializable
data class LetterProgress(
    val encounters: Int = 0,
    val status: LearningStatus = LearningStatus.NEW,
    val lastScore: Float? = null
)

/**
 * Per-letter progress, kept separately from the script JSON so that updating
 * the letter data never wipes the user's history, and exports stay clean.
 */
class ProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("strokelearn", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), LetterProgress.serializer())

    // keyed "<scriptId>/<letterId>"; a state map so tiles recompose on change
    private val entries = mutableStateMapOf<String, LetterProgress>()

    init {
        val stored = prefs.getString(KEY, null)
        if (stored != null) {
            runCatching { json.decodeFromString(serializer, stored) }
                .getOrNull()?.let { entries.putAll(it) }
        }
    }

    fun of(scriptId: String, letterId: String): LetterProgress =
        entries[key(scriptId, letterId)] ?: LetterProgress()

    /** Called once per glyph shown in a training session. */
    fun recordEncounter(scriptId: String, letterId: String) {
        val current = of(scriptId, letterId)
        val status =
            if (current.status == LearningStatus.NEW) LearningStatus.LEARNING
            else current.status
        put(scriptId, letterId, current.copy(encounters = current.encounters + 1, status = status))
    }

    fun recordScore(scriptId: String, letterId: String, score: Float) {
        put(scriptId, letterId, of(scriptId, letterId).copy(lastScore = score))
    }

    fun setStatus(scriptId: String, letterId: String, status: LearningStatus) {
        put(scriptId, letterId, of(scriptId, letterId).copy(status = status))
    }

    /** Letters the user has seen at least once — the pool a test can draw from. */
    fun encountered(script: Script): List<Letter> =
        script.allLetters.filter { of(script.id, it.id).encounters > 0 }

    /** Letters eligible for a new session: everything not yet marked learned. */
    fun notLearned(script: Script): List<Letter> =
        script.allLetters.filter { of(script.id, it.id).status != LearningStatus.LEARNED }

    /** Everything in the store, as pretty JSON, for backup before a reinstall. */
    fun exportToText(): String =
        Json { prettyPrint = true }.encodeToString(serializer, entries.toMap())

    /**
     * Merges a backup into the store. Existing entries are kept when they have
     * more encounters, so importing an older backup never loses newer progress.
     */
    fun importFromText(text: String): Result<Int> = runCatching {
        val incoming = json.decodeFromString(serializer, text)
        incoming.forEach { (key, imported) ->
            val current = entries[key]
            if (current == null || imported.encounters >= current.encounters) {
                entries[key] = imported
            }
        }
        save()
        incoming.size
    }
    /** Wipes a glyph's history, as if it had never been seen. */
    fun reset(scriptId: String, letterId: String) {
        entries[key(scriptId, letterId)] = LetterProgress()
        save()
    }
    private fun put(scriptId: String, letterId: String, value: LetterProgress) {
        entries[key(scriptId, letterId)] = value
        save()
    }

    private fun save() {
        prefs.edit()
            .putString(KEY, json.encodeToString(serializer, entries.toMap()))
            .apply()
    }

    private fun key(scriptId: String, letterId: String) = "$scriptId/$letterId"

    private companion object {
        const val KEY = "letter_progress"
    }
}